const fs = require("fs");
const path = require("path");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  writeBatch,
} = require("firebase/firestore");

const projectId = "registro-guardias-test";
const rules = fs.readFileSync(path.resolve(__dirname, "..", "firestore.rules"), "utf8");

const USERS = {
  admin: "admin-test",
  normal: "normal-test",
  blocked: "blocked-test",
  unknown: "unknown-test",
  service: "service-test",
};

let environment;
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
    console.error(error && error.message ? error.message : error);
  }
}

function dbAs(uid) {
  return environment.authenticatedContext(uid).firestore();
}

function person(overrides = {}) {
  return {
    nombre: "ANA PEREZ",
    estado: "Fuera",
    ultimoMovimiento: "",
    fecha: "",
    hora: "",
    actualizado: new Date("2026-09-01T12:00:00Z"),
    activo: true,
    retirado: false,
    ...overrides,
  };
}

function movement(id, overrides = {}) {
  return {
    movimientoId: id,
    personalId: "P0001",
    nombre: "ANA PEREZ",
    movimiento: "Ingreso",
    fecha: "01/09/2026",
    hora: "09:00",
    creado: new Date("2026-09-01T12:00:00Z"),
    usuario: "GUARDIA TEST",
    ...overrides,
  };
}

function key(overrides = {}) {
  return {
    nombre: "PORTON",
    estado: "Disponible",
    quienTiene: "",
    quienTieneId: "",
    fechaRetiro: "",
    horaRetiro: "",
    ultimoMovimiento: "",
    ultimoMovimientoId: "",
    ultimaFecha: "",
    ultimaHora: "",
    actualizado: new Date("2026-09-01T12:00:00Z"),
    activo: true,
    ...overrides,
  };
}

async function seed() {
  await environment.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await Promise.all([
      setDoc(doc(db, "administradores", USERS.admin), {
        activo: true, nombre: "ADMIN TEST", actualizado: new Date(),
      }),
      setDoc(doc(db, "dispositivos", USERS.normal), {
        nombre: "NORMAL TEST", estado: "Normal", actualizado: new Date(),
      }),
      setDoc(doc(db, "dispositivos", USERS.blocked), {
        nombre: "BLOQUEADO TEST", estado: "Bloqueado", actualizado: new Date(),
      }),
      setDoc(doc(db, "servicios", USERS.service), {activo: true}),
      setDoc(doc(db, "personal", "P0001"), person()),
      setDoc(doc(db, "llaves", "K0001"), key()),
      setDoc(doc(db, "meta", "config"), {
        siguienteId: 2,
        siguienteMovimiento: 1,
        siguienteLlave: 2,
        siguienteMovimientoLlave: 1,
      }),
    ]);
  });
}

async function run() {
  environment = await initializeTestEnvironment({
    projectId,
    firestore: {rules},
  });
  await environment.clearFirestore();
  await seed();

  await test("un dispositivo normal puede leer personal", async () => {
    await assertSucceeds(getDocs(collection(dbAs(USERS.normal), "personal")));
  });
  await test("un administrador puede leer personal", async () => {
    await assertSucceeds(getDocs(collection(dbAs(USERS.admin), "personal")));
  });
  await test("un dispositivo bloqueado no puede leer personal", async () => {
    await assertFails(getDocs(collection(dbAs(USERS.blocked), "personal")));
  });
  await test("un dispositivo desconocido no puede leer personal", async () => {
    await assertFails(getDocs(collection(dbAs(USERS.unknown), "personal")));
  });
  await test("el servicio puede leer datos para copiar a Sheets", async () => {
    await assertSucceeds(getDocs(collection(dbAs(USERS.service), "personal")));
  });
  await test("un dispositivo normal puede crear personal válido", async () => {
    await assertSucceeds(setDoc(doc(dbAs(USERS.normal), "personal", "P0002"), person({nombre: "JUAN LOPEZ"})));
  });
  await test("se rechaza un identificador de personal inválido", async () => {
    await assertFails(setDoc(doc(dbAs(USERS.normal), "personal", "2"), person()));
  });
  await test("se rechaza crear personal inicialmente Dentro", async () => {
    await assertFails(setDoc(doc(dbAs(USERS.normal), "personal", "P0003"), person({estado: "Dentro"})));
  });
  await test("un dispositivo bloqueado no puede crear personal", async () => {
    await assertFails(setDoc(doc(dbAs(USERS.blocked), "personal", "P0003"), person()));
  });
  await test("un dispositivo normal puede crear un ingreso válido", async () => {
    await assertSucceeds(setDoc(doc(dbAs(USERS.normal), "movimientos", "M000001"), movement("M000001")));
  });
  await test("un movimiento no puede modificarse", async () => {
    await assertFails(updateDoc(doc(dbAs(USERS.normal), "movimientos", "M000001"), {hora: "09:05"}));
  });
  await test("un movimiento no puede eliminarse", async () => {
    await assertFails(deleteDoc(doc(dbAs(USERS.admin), "movimientos", "M000001")));
  });
  await test("el ID guardado debe coincidir con el documento", async () => {
    await assertFails(setDoc(doc(dbAs(USERS.normal), "movimientos", "M000002"), movement("M999999")));
  });
  await test("se rechaza un tipo de movimiento desconocido", async () => {
    await assertFails(setDoc(doc(dbAs(USERS.normal), "movimientos", "M000002"), movement("M000002", {movimiento: "Pausa"})));
  });
  await test("se rechaza un usuario operativo vacío", async () => {
    await assertFails(setDoc(doc(dbAs(USERS.normal), "movimientos", "M000002"), movement("M000002", {usuario: ""})));
  });
  await test("un dispositivo normal puede crear una llave válida", async () => {
    await assertSucceeds(setDoc(doc(dbAs(USERS.normal), "llaves", "K0002"), key({nombre: "DEPOSITO"})));
  });
  await test("se rechaza crear una llave inicialmente prestada", async () => {
    await assertFails(setDoc(doc(dbAs(USERS.normal), "llaves", "K0003"), key({estado: "Prestada", quienTiene: "ANA PEREZ"})));
  });
  await test("una llave prestada no puede ocultarse", async () => {
    await environment.withSecurityRulesDisabled(async context => {
      await setDoc(doc(context.firestore(), "llaves", "K0004"), key({
        estado: "Prestada", quienTiene: "ANA PEREZ", quienTieneId: "P0001",
        fechaRetiro: "01/09/2026", horaRetiro: "09:00",
      }));
    });
    await assertFails(updateDoc(doc(dbAs(USERS.normal), "llaves", "K0004"), {activo: false}));
  });
  await test("una llave disponible sí puede ocultarse", async () => {
    await assertSucceeds(updateDoc(doc(dbAs(USERS.normal), "llaves", "K0001"), {activo: false}));
  });
  await test("un préstamo válido actualiza llave y movimiento de forma atómica", async () => {
    const db = dbAs(USERS.normal);
    const batch = writeBatch(db);
    batch.set(doc(db, "movimientosLlaves", "L000001"), {
      movimientoId: "L000001",
      llaveId: "K0002",
      llaveNombre: "DEPOSITO",
      movimiento: "Retiro",
      personaId: "P0001",
      persona: "ANA PEREZ",
      quienRetiraId: "P0001",
      quienRetira: "ANA PEREZ",
      fecha: "01/09/2026",
      hora: "09:30",
      usuario: "GUARDIA TEST",
      creado: serverTimestamp(),
    });
    batch.update(doc(db, "llaves", "K0002"), {
      estado: "Prestada",
      quienTiene: "ANA PEREZ",
      quienTieneId: "P0001",
      fechaRetiro: "01/09/2026",
      horaRetiro: "09:30",
      ultimoMovimiento: "Retiro",
      ultimoMovimientoId: "L000001",
      ultimaFecha: "01/09/2026",
      ultimaHora: "09:30",
      actualizado: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
  });
  await test("no se puede prestar una llave sin crear su movimiento", async () => {
    await assertFails(updateDoc(doc(dbAs(USERS.normal), "llaves", "K0001"), {
      estado: "Prestada",
      quienTiene: "ANA PEREZ",
      quienTieneId: "P0001",
      fechaRetiro: "01/09/2026",
      horaRetiro: "09:30",
      ultimoMovimiento: "Retiro",
      ultimoMovimientoId: "L999999",
      ultimaFecha: "01/09/2026",
      ultimaHora: "09:30",
      actualizado: serverTimestamp(),
    }));
  });
  await test("un usuario no puede cambiar su propio permiso", async () => {
    await assertFails(updateDoc(doc(dbAs(USERS.normal), "dispositivos", USERS.normal), {
      estado: "Bloqueado", actualizado: serverTimestamp(),
    }));
  });
  await test("un administrador puede bloquear otro dispositivo", async () => {
    await assertSucceeds(updateDoc(doc(dbAs(USERS.admin), "dispositivos", USERS.normal), {
      estado: "Bloqueado", actualizado: serverTimestamp(),
    }));
  });
  await test("un administrador solo puede registrar su propio dispositivo como Bloqueado", async () => {
    await assertSucceeds(setDoc(doc(dbAs(USERS.admin), "dispositivos", USERS.admin), {
      nombre: "ADMIN TEST", estado: "Bloqueado", actualizado: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(dbAs(USERS.admin), "dispositivos", USERS.admin), {
      estado: "Normal", actualizado: serverTimestamp(),
    }));
  });
  await test("un dispositivo nuevo solo puede registrarse Bloqueado", async () => {
    await assertSucceeds(setDoc(doc(dbAs("nuevo-test"), "dispositivos", "nuevo-test"), {
      nombre: "NUEVO TEST", estado: "Bloqueado", actualizado: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(dbAs("otro-test"), "dispositivos", "otro-test"), {
      nombre: "OTRO TEST", estado: "Normal", actualizado: serverTimestamp(),
    }));
  });
  await test("solo administradores consultan errores de la app", async () => {
    await assertSucceeds(getDocs(collection(dbAs(USERS.admin), "erroresApp")));
    await assertFails(getDocs(collection(dbAs(USERS.normal), "erroresApp")));
  });
  await test("los contadores no son visibles para bloqueados", async () => {
    await assertSucceeds(getDoc(doc(dbAs(USERS.admin), "meta", "config")));
    await assertFails(getDoc(doc(dbAs(USERS.blocked), "meta", "config")));
  });
  await test("un administrador puede eliminar otro dispositivo y su permiso", async () => {
    await assertSucceeds(setDoc(doc(dbAs(USERS.admin), "administradores", USERS.normal), {
      activo: false, nombre: "GUARDIA NORMAL", actualizado: serverTimestamp(),
    }));
    await assertSucceeds(deleteDoc(doc(dbAs(USERS.admin), "administradores", USERS.normal)));
    await assertSucceeds(deleteDoc(doc(dbAs(USERS.admin), "dispositivos", USERS.normal)));
  });
  await test("un administrador no puede eliminar su propio acceso", async () => {
    await assertFails(deleteDoc(doc(dbAs(USERS.admin), "administradores", USERS.admin)));
    await assertFails(deleteDoc(doc(dbAs(USERS.admin), "dispositivos", USERS.admin)));
  });

  console.log(`\nResultado: ${passed} aprobadas, ${failed} fallidas.`);
  if (failed > 0) process.exitCode = 1;
}

run()
  .catch(error => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (environment) await environment.cleanup();
  });
