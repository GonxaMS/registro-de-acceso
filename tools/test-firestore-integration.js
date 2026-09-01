const fs = require("fs");
const net = require("net");
const path = require("path");
const {
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  collection,
  doc,
  getDoc,
  getDocs,
  runTransaction,
  serverTimestamp,
  setDoc,
} = require("firebase/firestore");

const projectId = "registro-guardias-test";
const rules = fs.readFileSync(path.resolve(__dirname, "..", "firestore.rules"), "utf8");
const DATE = "01/09/2026";

let environment;
let networkEnvironment;
let networkProxy;
let passed = 0;
let failed = 0;

async function test(name, action) {
  try {
    await action();
    passed += 1;
    console.log(`OK  ${name}`);
  } catch (error) {
    failed += 1;
    console.error(`FALLO  ${name}`);
    console.error(error && error.stack ? error.stack : error);
  }
}

function assertEqual(actual, expected, label) {
  if (actual !== expected) {
    throw new Error(`${label}: se esperaba ${expected}, se obtuvo ${actual}`);
  }
}

function assert(condition, label) {
  if (!condition) throw new Error(label);
}

function person(name = "ANA PEREZ") {
  return {
    nombre: name,
    estado: "Fuera",
    ultimoMovimiento: "",
    fecha: "",
    hora: "",
    actualizado: new Date(),
    activo: true,
    retirado: false,
  };
}

function key(name) {
  return {
    nombre: name,
    estado: "Disponible",
    quienTiene: "",
    quienTieneId: "",
    fechaRetiro: "",
    horaRetiro: "",
    ultimoMovimiento: "",
    ultimoMovimientoId: "",
    ultimaFecha: "",
    ultimaHora: "",
    actualizado: new Date(),
    activo: true,
  };
}

async function seed() {
  await environment.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await Promise.all([
      setDoc(doc(db, "dispositivos", "guardia-a"), {
        nombre: "GUARDIA A", estado: "Normal", actualizado: new Date(),
      }),
      setDoc(doc(db, "dispositivos", "guardia-b"), {
        nombre: "GUARDIA B", estado: "Normal", actualizado: new Date(),
      }),
      setDoc(doc(db, "personal", "P0001"), person("ANA PEREZ")),
      setDoc(doc(db, "personal", "P0002"), person("JUAN LOPEZ")),
      setDoc(doc(db, "personal", "P0003"), person("MARIA GOMEZ")),
      setDoc(doc(db, "personal", "P0004"), person("PEDRO DIAZ")),
      setDoc(doc(db, "personal", "P0005"), person("SOFIA RUIZ")),
      setDoc(doc(db, "llaves", "K0001"), key("PORTON")),
      setDoc(doc(db, "llaves", "K0002"), key("DEPOSITO")),
      setDoc(doc(db, "meta", "config"), {
        siguienteId: 3,
        siguienteMovimiento: 1,
        siguienteLlave: 3,
        siguienteMovimientoLlave: 1,
      }),
    ]);
  });
}

function dbAs(uid) {
  return environment.authenticatedContext(uid).firestore();
}

async function recordPersonMovement(db, personId, type, time, user, options = {}) {
  const personRef = doc(db, "personal", personId);
  const metaRef = doc(db, "meta", "config");
  return runTransaction(db, async transaction => {
    const current = await transaction.get(personRef);
    const config = await transaction.get(metaRef);
    const data = current.data();
    const entry = type === "Ingreso";
    if (entry && data.estado === "Dentro") throw new Error(`${data.nombre} ya está dentro`);
    if (!entry && data.estado !== "Dentro") throw new Error(`Primero debe ingresar ${data.nombre}`);
    if (entry && data.fecha === DATE && data.ultimoMovimiento === "Salida") {
      throw new Error(`${data.nombre} ya completó el día`);
    }
    if (options.beforeCommit) await options.beforeCommit();

    const next = config.data().siguienteMovimiento;
    const movementId = `M${String(next).padStart(6, "0")}`;
    transaction.update(personRef, {
      estado: entry ? "Dentro" : "Fuera",
      ultimoMovimiento: type,
      fecha: DATE,
      hora: time,
      actualizado: serverTimestamp(),
    });
    transaction.set(doc(db, "movimientos", movementId), {
      movimientoId: movementId,
      personalId: personId,
      nombre: data.nombre,
      movimiento: type,
      fecha: DATE,
      hora: time,
      creado: serverTimestamp(),
      usuario: user,
    });
    transaction.set(metaRef, {siguienteMovimiento: next + 1}, {merge: true});
    return movementId;
  });
}

async function recordKeyMovement(db, keyId, type, personId, personName, time, user) {
  const keyRef = doc(db, "llaves", keyId);
  const metaRef = doc(db, "meta", "config");
  return runTransaction(db, async transaction => {
    const current = await transaction.get(keyRef);
    const config = await transaction.get(metaRef);
    const data = current.data();
    const take = type === "Retiro";
    if (take && data.estado === "Prestada") throw new Error(`La llave ya está prestada a ${data.quienTiene}`);
    if (!take && data.estado !== "Prestada") throw new Error("La llave ya está disponible");

    const next = config.data().siguienteMovimientoLlave;
    const movementId = `L${String(next).padStart(6, "0")}`;
    transaction.update(keyRef, {
      estado: take ? "Prestada" : "Disponible",
      quienTiene: take ? personName : "",
      quienTieneId: take ? personId : "",
      fechaRetiro: take ? DATE : "",
      horaRetiro: take ? time : "",
      ultimoMovimiento: type,
      ultimoMovimientoId: movementId,
      ultimaFecha: DATE,
      ultimaHora: time,
      actualizado: serverTimestamp(),
    });
    const movement = {
      movimientoId: movementId,
      llaveId: keyId,
      llaveNombre: data.nombre,
      movimiento: type,
      personaId: personId,
      persona: personName,
      fecha: DATE,
      hora: time,
      usuario: user,
      creado: serverTimestamp(),
    };
    if (take) {
      movement.quienRetiraId = personId;
      movement.quienRetira = personName;
    } else {
      movement.quienDevuelveId = personId;
      movement.quienDevuelve = personName;
    }
    transaction.set(doc(db, "movimientosLlaves", movementId), movement);
    transaction.set(metaRef, {siguienteMovimientoLlave: next + 1}, {merge: true});
    return movementId;
  });
}

async function countDocuments(db, collectionName) {
  return (await getDocs(collection(db, collectionName))).size;
}

async function expectOneSuccess(promises, label) {
  const results = await Promise.allSettled(promises);
  const succeeded = results.filter(result => result.status === "fulfilled");
  const rejected = results.filter(result => result.status === "rejected");
  assertEqual(succeeded.length, 1, `${label}: operaciones aceptadas`);
  assertEqual(rejected.length, 1, `${label}: operaciones rechazadas`);
  return succeeded[0].value;
}

function delay(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

async function createNetworkProxy(targetPort) {
  let online = true;
  let latencyMs = 0;
  const connections = new Set();
  const server = net.createServer(client => {
    if (!online) {
      client.destroy();
      return;
    }
    const upstream = net.connect(targetPort, "127.0.0.1");
    const pair = {client, upstream};
    connections.add(pair);
    const closePair = () => {
      connections.delete(pair);
      client.destroy();
      upstream.destroy();
    };
    client.on("error", closePair);
    upstream.on("error", closePair);
    client.on("close", () => connections.delete(pair));
    upstream.on("close", () => connections.delete(pair));
    const forward = (source, destination) => {
      source.on("data", data => {
        if (!online) return;
        if (latencyMs > 0) setTimeout(() => {
          if (online && !destination.destroyed) destination.write(data);
        }, latencyMs);
        else destination.write(data);
      });
    };
    forward(client, upstream);
    forward(upstream, client);
  });
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(8181, "127.0.0.1", resolve);
  });
  return {
    setOnline(value) {
      online = value;
      if (!online) {
        for (const pair of Array.from(connections)) {
          pair.client.destroy();
          pair.upstream.destroy();
        }
        connections.clear();
      }
    },
    setLatency(milliseconds) {
      latencyMs = Math.max(0, milliseconds);
    },
    close() {
      for (const pair of Array.from(connections)) {
        pair.client.destroy();
        pair.upstream.destroy();
      }
      return new Promise(resolve => server.close(resolve));
    },
  };
}

function networkDbAs(uid) {
  return networkEnvironment.authenticatedContext(uid).firestore();
}

async function run() {
  environment = await initializeTestEnvironment({projectId, firestore: {rules}});
  await environment.clearFirestore();
  await seed();
  networkProxy = await createNetworkProxy(8080);
  networkEnvironment = await initializeTestEnvironment({
    projectId,
    firestore: {host: "127.0.0.1", port: 8181},
  });

  await test("ingreso completo actualiza estado, historial y contador", async () => {
    const db = dbAs("guardia-a");
    const id = await recordPersonMovement(db, "P0001", "Ingreso", "08:00", "GUARDIA A");
    assertEqual(id, "M000001", "ID del ingreso");
    const state = (await getDoc(doc(db, "personal", "P0001"))).data();
    assertEqual(state.estado, "Dentro", "Estado después del ingreso");
    assertEqual(state.ultimoMovimiento, "Ingreso", "Último movimiento");
    assert((await getDoc(doc(db, "movimientos", id))).exists(), "El movimiento debe existir");
    assertEqual((await getDoc(doc(db, "meta", "config"))).data().siguienteMovimiento, 2, "Contador siguiente");
  });

  await test("salida completa deja a la persona Fuera", async () => {
    const db = dbAs("guardia-b");
    const id = await recordPersonMovement(db, "P0001", "Salida", "17:00", "GUARDIA B");
    assertEqual(id, "M000002", "ID de la salida");
    const state = (await getDoc(doc(db, "personal", "P0001"))).data();
    assertEqual(state.estado, "Fuera", "Estado después de la salida");
    assertEqual(state.ultimoMovimiento, "Salida", "Último movimiento");
  });

  await test("no permite un segundo ingreso después de completar el día", async () => {
    let rejected = false;
    try {
      await recordPersonMovement(dbAs("guardia-a"), "P0001", "Ingreso", "18:00", "GUARDIA A");
    } catch (error) {
      rejected = /completó el día/.test(error.message);
    }
    assert(rejected, "El nuevo ingreso debía rechazarse por la lógica operativa");
    assertEqual(await countDocuments(dbAs("guardia-a"), "movimientos"), 2, "No debe aparecer un movimiento adicional");
  });

  await test("dos ingresos simultáneos aceptan solamente uno", async () => {
    const winner = await expectOneSuccess([
      recordPersonMovement(dbAs("guardia-a"), "P0002", "Ingreso", "08:01", "GUARDIA A"),
      recordPersonMovement(dbAs("guardia-b"), "P0002", "Ingreso", "08:01", "GUARDIA B"),
    ], "Ingreso simultáneo");
    assert(/^M00000[34]$/.test(winner), "El ganador debe recibir un ID correlativo");
    const state = (await getDoc(doc(dbAs("guardia-a"), "personal", "P0002"))).data();
    assertEqual(state.estado, "Dentro", "Estado final concurrente");
    assertEqual(await countDocuments(dbAs("guardia-a"), "movimientos"), 3, "Debe existir un solo ingreso concurrente");
  });

  await test("retiro y devolución completos conservan el historial", async () => {
    const takeId = await recordKeyMovement(dbAs("guardia-a"), "K0001", "Retiro", "P0001", "ANA PEREZ", "09:00", "GUARDIA A");
    assertEqual(takeId, "L000001", "ID del retiro");
    let state = (await getDoc(doc(dbAs("guardia-a"), "llaves", "K0001"))).data();
    assertEqual(state.estado, "Prestada", "Estado tras retiro");
    assertEqual(state.quienTiene, "ANA PEREZ", "Responsable del retiro");

    const returnId = await recordKeyMovement(dbAs("guardia-b"), "K0001", "Devolucion", "P0002", "JUAN LOPEZ", "16:00", "GUARDIA B");
    assertEqual(returnId, "L000002", "ID de la devolución");
    state = (await getDoc(doc(dbAs("guardia-a"), "llaves", "K0001"))).data();
    assertEqual(state.estado, "Disponible", "Estado tras devolución");
    assertEqual(state.quienTiene, "", "La llave ya no debe tener responsable");
    assertEqual(await countDocuments(dbAs("guardia-a"), "movimientosLlaves"), 2, "Historial de la llave");
  });

  await test("dos retiros simultáneos de la misma llave aceptan solamente uno", async () => {
    await expectOneSuccess([
      recordKeyMovement(dbAs("guardia-a"), "K0002", "Retiro", "P0001", "ANA PEREZ", "10:00", "GUARDIA A"),
      recordKeyMovement(dbAs("guardia-b"), "K0002", "Retiro", "P0002", "JUAN LOPEZ", "10:00", "GUARDIA B"),
    ], "Retiro simultáneo");
    const state = (await getDoc(doc(dbAs("guardia-a"), "llaves", "K0002"))).data();
    assertEqual(state.estado, "Prestada", "Estado final de la llave");
    assert(["ANA PEREZ", "JUAN LOPEZ"].includes(state.quienTiene), "Debe conservar al ganador real");
    assertEqual(await countDocuments(dbAs("guardia-a"), "movimientosLlaves"), 3, "Debe existir un solo retiro concurrente");
  });

  await test("sin Internet espera la confirmación y completa una sola vez al reconectar", async () => {
    const offlineDb = networkDbAs("guardia-a");
    const observerDb = dbAs("guardia-b");
    const before = await countDocuments(observerDb, "movimientos");
    networkProxy.setOnline(false);

    let settled = false;
    const operation = recordPersonMovement(
      offlineDb, "P0003", "Ingreso", "08:10", "GUARDIA A"
    ).finally(() => { settled = true; });

    await delay(400);
    assertEqual(settled, false, "La operación no debe informar éxito mientras no tiene conexión");
    assertEqual(await countDocuments(observerDb, "movimientos"), before,
      "No debe existir un movimiento antes de la confirmación");

    networkProxy.setOnline(true);
    const movementId = await operation;
    assert(/^M[0-9]{6}$/.test(movementId), "La operación recuperada debe recibir un ID válido");
    assertEqual(await countDocuments(observerDb, "movimientos"), before + 1,
      "La recuperación debe crear exactamente un movimiento");
    assertEqual((await getDoc(doc(observerDb, "personal", "P0003"))).data().estado, "Dentro",
      "La persona debe quedar Dentro después de recuperar conexión");
  });

  await test("una caída durante la confirmación no deja escrituras parciales ni duplicados", async () => {
    const unstableDb = networkDbAs("guardia-a");
    const observerDb = dbAs("guardia-b");
    const before = await countDocuments(observerDb, "movimientos");
    let networkInterrupted = false;

    let settled = false;
    const operation = recordPersonMovement(
      unstableDb,
      "P0004",
      "Ingreso",
      "08:20",
      "GUARDIA A",
      {
        beforeCommit: async () => {
          if (networkInterrupted) return;
          networkInterrupted = true;
          networkProxy.setOnline(false);
        },
      }
    ).finally(() => { settled = true; });

    await delay(400);
    assert(networkInterrupted, "La interrupción debía ocurrir después de leer los datos");
    assertEqual(settled, false, "El corte no debe producir un éxito anticipado");
    assertEqual(await countDocuments(observerDb, "movimientos"), before,
      "No debe quedar una escritura parcial durante el corte");

    networkProxy.setOnline(true);
    await operation;
    assertEqual(await countDocuments(observerDb, "movimientos"), before + 1,
      "El reintento debe crear un único movimiento");
    const movements = await getDocs(collection(observerDb, "movimientos"));
    const matches = movements.docs.filter(item => item.data().personalId === "P0004");
    assertEqual(matches.length, 1, "No debe duplicarse el ingreso después del reintento");
  });

  await test("una conexión lenta demora la confirmación sin duplicar el movimiento", async () => {
    const slowDb = networkDbAs("guardia-b");
    const observerDb = dbAs("guardia-a");
    const before = await countDocuments(observerDb, "movimientos");
    networkProxy.setLatency(150);
    const started = Date.now();
    await recordPersonMovement(slowDb, "P0005", "Ingreso", "08:30", "GUARDIA B");
    const elapsed = Date.now() - started;
    networkProxy.setLatency(0);
    assert(elapsed >= 250, `La confirmación lenta terminó demasiado rápido (${elapsed} ms)`);
    assertEqual(await countDocuments(observerDb, "movimientos"), before + 1,
      "La latencia debe producir exactamente un movimiento");
    const movements = await getDocs(collection(observerDb, "movimientos"));
    const matches = movements.docs.filter(item => item.data().personalId === "P0005");
    assertEqual(matches.length, 1, "La conexión lenta no debe duplicar el ingreso");
    console.log(`    Latencia simulada: confirmación en ${elapsed} ms`);
  });

  console.log(`\nIntegración: ${passed} aprobadas, ${failed} fallidas.`);
  if (failed > 0) process.exitCode = 1;
}

run()
  .catch(error => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (networkEnvironment) await networkEnvironment.cleanup();
    if (networkProxy) await networkProxy.close();
    if (environment) await environment.cleanup();
  });
