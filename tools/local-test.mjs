import fs from "node:fs";
import { promises as fsPromises } from "node:fs";
import net from "node:net";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawn, spawnSync } from "node:child_process";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const DEFAULT_PROJECT_ID = "registro-guardias-408cb";
const DEFAULT_HOST = "127.0.0.1";
const DEFAULT_FIRESTORE_PORT = 8080;
const RUNTIME_DIRECTORY = path.join(PROJECT_ROOT, "build", "local-test");
const FIREBASE_PID_FILE = path.join(RUNTIME_DIRECTORY, "firebase.pid");
const APPLICATION_ID = "com.ejemplo.registroguardias";

function usage() {
  console.log(`Uso:
  node tools/local-test.mjs start [--reset-app] [--keep-data]
  node tools/local-test.mjs seed [--keep-data]
  node tools/local-test.mjs stop [--stop-android-emulator]

Opciones:
  --reset-app              Limpia los datos locales de la aplicación Android.
  --keep-data              Conserva los datos actuales del emulador de Firestore.
  --stop-android-emulator  Detiene también el emulador Android.
  --project <id>           Project ID local de Firebase.
  --host <host>            Host del emulador de Firestore.
  --port <puerto>          Puerto del emulador de Firestore.
  --admin-uid <uid>        UID local al que se asigna el rol Administrativo.
  --help                   Muestra esta ayuda.`);
}

function parseArguments(argv) {
  const command = argv[0] && !argv[0].startsWith("-") ? argv.shift() : "help";
  const options = {
    projectId: DEFAULT_PROJECT_ID,
    host: DEFAULT_HOST,
    port: DEFAULT_FIRESTORE_PORT,
    adminUid: "",
    keepData: false,
    resetApp: false,
    stopAndroidEmulator: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else if (argument === "--reset-app") {
      options.resetApp = true;
    } else if (argument === "--keep-data") {
      options.keepData = true;
    } else if (argument === "--stop-android-emulator") {
      options.stopAndroidEmulator = true;
    } else if (["--project", "--host", "--port", "--admin-uid"].includes(argument)) {
      const value = argv[++index];
      if (!value || value.startsWith("-")) {
        throw new Error(`Falta el valor de ${argument}`);
      }
      if (argument === "--project") options.projectId = value;
      if (argument === "--host") options.host = value;
      if (argument === "--port") options.port = Number(value);
      if (argument === "--admin-uid") options.adminUid = value;
    } else {
      throw new Error(`Opción desconocida: ${argument}`);
    }
  }

  if (!Number.isInteger(options.port) || options.port < 1 || options.port > 65535) {
    throw new Error("El puerto debe ser un número entero entre 1 y 65535");
  }
  return {command, options};
}

function delay(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

function run(command, args, {cwd = PROJECT_ROOT, stdio = "pipe"} = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      stdio,
      windowsHide: true,
      shell: process.platform === "win32" && /\.(?:bat|cmd)$/i.test(command),
    });
    let stdout = "";
    let stderr = "";
    if (child.stdout) child.stdout.on("data", chunk => { stdout += chunk; });
    if (child.stderr) child.stderr.on("data", chunk => { stderr += chunk; });
    child.once("error", reject);
    child.once("close", (code, signal) => {
      const result = {code, signal, stdout: stdout.trim(), stderr: stderr.trim()};
      if (code === 0) resolve(result);
      else {
        const detail = [stderr.trim(), stdout.trim()].filter(Boolean).join("\n");
        reject(new Error(`${command} terminó con código ${code ?? "desconocido"}${
          signal ? ` (${signal})` : ""}${detail ? `\n${detail}` : ""}`));
      }
    });
  });
}

async function runAllowFailure(command, args, options = {}) {
  try {
    return await run(command, args, options);
  } catch (error) {
    return {code: error.code ?? 1, error};
  }
}

function readSdkPath() {
  const propertiesPath = path.join(PROJECT_ROOT, "local.properties");
  if (!fs.existsSync(propertiesPath)) {
    throw new Error("Falta local.properties. Copia local.properties.example y configura sdk.dir.");
  }
  const source = fs.readFileSync(propertiesPath, "utf8");
  const line = source.split(/\r?\n/).find(value => /^\s*sdk\.dir\s*=/.test(value));
  if (!line) throw new Error("Falta sdk.dir en local.properties");
  const raw = line.substring(line.indexOf("=") + 1).trim();
  return raw.replace(/\\:/g, ":").replace(/\\\\/g, "\\");
}

function androidTools() {
  const sdkPath = readSdkPath();
  const suffix = process.platform === "win32" ? ".exe" : "";
  return {
    adb: path.join(sdkPath, "platform-tools", `adb${suffix}`),
    emulator: path.join(sdkPath, "emulator", `emulator${suffix}`),
  };
}

function firebaseCliPath() {
  const executable = path.join(
    PROJECT_ROOT, "node_modules", "firebase-tools", "lib", "bin", "firebase.js");
  if (!fs.existsSync(executable)) {
    throw new Error("Falta firebase-tools. Ejecuta npm install antes de iniciar el entorno local.");
  }
  return executable;
}

async function waitFor(description, check, timeoutMilliseconds = 120000, intervalMilliseconds = 2000) {
  const deadline = Date.now() + timeoutMilliseconds;
  let lastError = null;
  while (Date.now() < deadline) {
    try {
      if (await check()) return;
    } catch (error) {
      lastError = error;
    }
    await delay(intervalMilliseconds);
  }
  throw new Error(`Tiempo agotado esperando ${description}${lastError ? `: ${lastError.message}` : ""}`);
}

async function waitForPort(host, port) {
  await waitFor(`el puerto ${host}:${port}`, () => new Promise(resolve => {
    const socket = net.createConnection({host, port});
    const finish = available => {
      socket.destroy();
      resolve(available);
    };
    socket.once("connect", () => finish(true));
    socket.once("error", () => finish(false));
  }));
}

function stringField(value) { return {stringValue: value}; }
function boolField(value) { return {booleanValue: value}; }
function intField(value) { return {integerValue: String(value)}; }
function timestampField(value) { return {timestampValue: value}; }

function encodeDocumentPath(documentPath) {
  return documentPath.split("/").map(encodeURIComponent).join("/");
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Firebase respondió ${response.status}: ${body}`);
  }
  return response;
}

async function seedLocalFirebase(options) {
  const baseUrl = `http://${options.host}:${options.port}/v1/projects/${options.projectId}`
    + "/databases/(default)/documents";
  const headers = {Authorization: "Bearer owner"};
  if (!options.keepData) {
    const resetUrl = `http://${options.host}:${options.port}/emulator/v1/projects/`
      + `${options.projectId}/databases/(default)/documents`;
    await requestJson(resetUrl, {method: "DELETE", headers});
  }

  const timestamp = new Date().toISOString();
  const putDocument = async (documentPath, fields) => {
    const url = `${baseUrl}/${encodeDocumentPath(documentPath)}`;
    await requestJson(url, {
      method: "PATCH",
      headers: {...headers, "Content-Type": "application/json"},
      body: JSON.stringify({fields}),
    });
  };

  await putDocument("meta/config", {
    siguienteId: intField(4),
    siguienteMovimiento: intField(1),
    siguienteLlave: intField(3),
    siguienteMovimientoLlave: intField(1),
    solicitudRehacerPlanillas: intField(0),
  });

  if (options.adminUid) {
    await putDocument(`administradores/${options.adminUid}`, {
      activo: boolField(true),
      nombre: stringField("Administrador local"),
    });
  }

  const people = [
    ["P0001", "ACOSTA MARTINA"],
    ["P0002", "BENITEZ LAUTARO"],
    ["P0003", "FERNANDEZ AGUSTINA"],
  ];
  for (const [id, name] of people) {
    await putDocument(`personal/${id}`, {
      nombre: stringField(name),
      estado: stringField("Fuera"),
      ultimoMovimiento: stringField(""),
      fecha: stringField(""),
      hora: stringField(""),
      activo: boolField(true),
      retirado: boolField(false),
      actualizado: timestampField(timestamp),
    });
  }

  const keys = [
    ["K0001", "PORTON PRINCIPAL"],
    ["K0002", "DEPOSITO"],
  ];
  for (const [id, name] of keys) {
    await putDocument(`llaves/${id}`, {
      nombre: stringField(name),
      estado: stringField("Disponible"),
      quienTiene: stringField(""),
      quienTieneId: stringField(""),
      fechaRetiro: stringField(""),
      horaRetiro: stringField(""),
      ultimoMovimiento: stringField(""),
      ultimoMovimientoId: stringField(""),
      ultimaFecha: stringField(""),
      ultimaHora: stringField(""),
      activo: boolField(true),
      actualizado: timestampField(timestamp),
    });
  }

  console.log("Firebase local cargado: 3 operarios y 2 llaves.");
  if (options.adminUid) console.log(`Administrador local habilitado: ${options.adminUid}`);
}

function processIsRunning(processId) {
  if (!processId || !Number.isInteger(processId)) return false;
  if (process.platform === "win32") {
    const result = spawnSync("tasklist.exe", ["/FI", `PID eq ${processId}`, "/NH"], {
      encoding: "utf8",
      windowsHide: true,
    });
    return result.status === 0 && result.stdout.includes(String(processId));
  }
  try {
    process.kill(processId, 0);
    return true;
  } catch (_) {
    return false;
  }
}

function killProcessTree(processId) {
  if (process.platform === "win32") {
    spawnSync("taskkill.exe", ["/PID", String(processId), "/T", "/F"], {
      stdio: "ignore",
      windowsHide: true,
    });
    return;
  }
  try {
    process.kill(-processId, "SIGTERM");
  } catch (_) {
    try { process.kill(processId, "SIGTERM"); } catch (_) { /* ya se detuvo */ }
  }
}

async function startFirebaseEmulators(options) {
  await fsPromises.mkdir(RUNTIME_DIRECTORY, {recursive: true});
  let processId = null;
  if (fs.existsSync(FIREBASE_PID_FILE)) {
    const saved = Number(fs.readFileSync(FIREBASE_PID_FILE, "utf8").trim());
    if (processIsRunning(saved)) processId = saved;
  }

  if (!processId) {
    const stdoutPath = path.join(RUNTIME_DIRECTORY, "firebase.stdout.log");
    const stderrPath = path.join(RUNTIME_DIRECTORY, "firebase.stderr.log");
    const stdout = fs.openSync(stdoutPath, "a");
    const stderr = fs.openSync(stderrPath, "a");
    const child = spawn(process.execPath, [
      firebaseCliPath(),
      "emulators:start",
      "--only", "auth,firestore",
      "--project", options.projectId,
    ], {
      cwd: PROJECT_ROOT,
      detached: true,
      windowsHide: true,
      stdio: ["ignore", stdout, stderr],
    });
    child.unref();
    fs.closeSync(stdout);
    fs.closeSync(stderr);
    processId = child.pid;
    fs.writeFileSync(FIREBASE_PID_FILE, `${processId}\n`, "utf8");
  }

  await waitForPort(options.host, options.port);
}

async function startLocalTest(options) {
  const {adb, emulator} = androidTools();
  if (!fs.existsSync(adb)) throw new Error(`No se encontró adb: ${adb}`);
  if (!fs.existsSync(emulator)) throw new Error(`No se encontró el emulador Android: ${emulator}`);

  const devices = await run(adb, ["devices"]);
  if (!/^emulator-\d+\s+device$/m.test(devices.stdout)) {
    const child = spawn(emulator, [
      "-avd", "RegistroAcceso_API35",
      "-no-snapshot", "-no-boot-anim", "-no-audio", "-gpu", "swiftshader_indirect",
    ], {detached: true, windowsHide: true, stdio: "ignore"});
    child.unref();
    await waitFor("el dispositivo Android", async () => {
      const result = await run(adb, ["devices"]);
      return /^emulator-\d+\s+device$/m.test(result.stdout);
    });
  }

  await run(adb, ["wait-for-device"], {stdio: "inherit"});
  await waitFor("el arranque completo de Android", async () => {
    const result = await run(adb, ["shell", "getprop", "sys.boot_completed"]);
    return result.stdout.trim() === "1";
  });

  await startFirebaseEmulators(options);
  await seedLocalFirebase(options);

  const gradle = process.platform === "win32"
    ? path.join(PROJECT_ROOT, "gradlew.bat") : path.join(PROJECT_ROOT, "gradlew");
  await run(gradle, ["--no-daemon", "-PfirebaseEmulator=true", "assembleDebug"], {
    stdio: "inherit",
  });
  const apk = path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk");
  await run(adb, ["install", "-r", apk], {stdio: "inherit"});
  if (options.resetApp) await run(adb, ["shell", "pm", "clear", APPLICATION_ID], {stdio: "inherit"});
  await run(adb, ["shell", "monkey", "-p", APPLICATION_ID,
    "-c", "android.intent.category.LAUNCHER", "1"], {stdio: "inherit"});
  console.log("Entorno listo. Firebase UI: http://127.0.0.1:4000");
}

async function stopLocalTest(options) {
  if (fs.existsSync(FIREBASE_PID_FILE)) {
    const processId = Number(fs.readFileSync(FIREBASE_PID_FILE, "utf8").trim());
    if (processIsRunning(processId)) killProcessTree(processId);
    await fsPromises.rm(FIREBASE_PID_FILE, {force: true});
  }

  if (options.stopAndroidEmulator) {
    const {adb} = androidTools();
    if (fs.existsSync(adb)) await runAllowFailure(adb, ["emu", "kill"], {stdio: "inherit"});
  }
  console.log("Entorno local detenido.");
}

async function main() {
  const {command, options} = parseArguments(process.argv.slice(2));
  if (options.help || command === "help") {
    usage();
    return;
  }
  if (command === "start") return startLocalTest(options);
  if (command === "seed") return seedLocalFirebase(options);
  if (command === "stop") return stopLocalTest(options);
  throw new Error(`Comando desconocido: ${command}`);
}

main().catch(error => {
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});
