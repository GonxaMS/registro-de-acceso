const fs = require("fs");
const path = require("path");
const vm = require("vm");

const root = path.resolve(__dirname, "..");
const scriptPath = path.join(root, "apps-script", "Codigo.gs.js");
const source = fs.readFileSync(scriptPath, "utf8");
const context = {};
vm.createContext(context);
vm.runInContext(source, context, {filename: scriptPath});

function assertEqual(actual, expected, label) {
  if (actual !== expected) {
    throw new Error(`${label}: se esperaba ${expected}, se obtuvo ${actual}`);
  }
}

assertEqual(context.nombreHojaRegistros("01/01/2026"), "Registro Personal 2026-1", "Registro enero");
assertEqual(context.nombreHojaRegistros("25/08/2026"), "Registro Personal 2026-8", "Registro agosto");
assertEqual(context.nombreHojaRegistros("01/09/2026"), "Registro Personal 2026-9", "Registro septiembre");
assertEqual(context.nombreHojaRegistros("31/12/2026"), "Registro Personal 2026-12", "Registro diciembre");
assertEqual(context.nombreHojaRegistros("2027-02-01"), "Registro Personal 2027-2", "Registro ISO otro año");
assertEqual(context.nombreHojaLlaves("01/01/2026"), "Registro Llaves 2026-1", "Llaves enero");
assertEqual(context.nombreHojaLlaves("25/08/2026"), "Registro Llaves 2026-8", "Llaves agosto");
assertEqual(context.nombreHojaLlaves("2026-09-01"), "Registro Llaves 2026-9", "Llaves septiembre ISO");
assertEqual(context.nombreHojaLlaves("31/12/2026"), "Registro Llaves 2026-12", "Llaves diciembre");
assertEqual(context.claveMesDocumento({fecha: "26/08/2026"}), "2026-08", "Filtro mensual Firebase");
assertEqual(context.claveMesDocumento({fecha: "2026-01-09"}), "2026-01", "Filtro mensual ISO");
assertEqual(context.claveMesDocumento({fecha: "fecha inválida"}), "", "Fecha Firebase inválida");
assertEqual(context.claveMesDocumento(null), "", "Documento Firebase ausente");
assertEqual(context.compararFechas("22/08/2026", "24/08/2026") < 0, true,
  "Una corrección atrasada debe insertarse antes de una fecha posterior");
assertEqual(context.compararFechas("26/08/2026", "25/08/2026") > 0, true,
  "Una fecha nueva debe mantenerse después de la anterior");

let invalidDateRejected = false;
try {
  context.nombreHojaRegistros("31/02/2026");
} catch (_) {
  invalidDateRejected = true;
}
if (!invalidDateRejected) throw new Error("Una fecha inexistente debe ser rechazada");

for (const invalidDate of ["00/01/2026", "01/13/2026", "29/02/2025", "2026-04-31", "1-9-2026", ""]) {
  let rejected = false;
  try {
    context.nombreHojaLlaves(invalidDate);
  } catch (_) {
    rejected = true;
  }
  if (!rejected) throw new Error(`La fecha inválida ${JSON.stringify(invalidDate)} debe ser rechazada`);
}

assertEqual(context.nombreHojaRegistros("29/02/2024"), "Registro Personal 2024-2",
  "Un día bisiesto real debe aceptarse");
assertEqual(context.compararFechas("2026-09-01", "01/09/2026"), 0,
  "Los formatos ISO y argentino deben representar el mismo día");

const forbiddenPatterns = [
  /const\s+HOJA_REGISTROS\s*=\s*["']Registros["']/,
  /const\s+HOJA_LLAVES\s*=\s*["']Registros Llaves["']/,
  /function\s+doGet\s*\(/,
  /function\s+doPost\s*\(/,
  /function\s+migrarMesTemporal\s*\(/,
  /function\s+estadoMigracionTemporal\s*\(/,
  /function\s+registrarMovimientoPersonal\s*\(/,
  /function\s+registrarMovimientoLlave\s*\(/,
];
for (const pattern of forbiddenPatterns) {
  if (pattern.test(source)) throw new Error(`Volvió un destino fijo prohibido: ${pattern}`);
}
if (!source.includes("obtenerRegistros(config, fecha)")) {
  throw new Error("Los movimientos de operarios deben seleccionar la pestaña usando su fecha");
}
if (!source.includes("obtenerRegistrosLlaves(config, fecha)")) {
  throw new Error("Los movimientos de llaves deben seleccionar la pestaña usando su fecha");
}
if (!source.includes("function sincronizarDesdeFirebase()")) {
  throw new Error("Apps Script debe consultar Firebase como fuente de movimientos");
}
if (!source.includes("function reconstruirPlanillasDesdeFirebase(fechaMes)")) {
  throw new Error("Apps Script debe permitir reconstruir las vistas mensuales desde Firebase");
}
if (!source.includes("leerColeccionCompletaFirestore(firebase, token, \"movimientos\")")
    || !source.includes("leerColeccionCompletaFirestore(firebase, token, \"movimientosLlaves\")")) {
  throw new Error("La reconstrucción debe leer Personal y Llaves directamente desde Firebase");
}
if (!source.includes(".everyMinutes(1)")) {
  throw new Error("La sincronización de Firebase debe ejecutarse cada minuto");
}
if (!source.includes("erroresSincronizacion")) {
  throw new Error("Los fallos de sincronización deben quedar registrados");
}
if (!source.includes("insertColumnsBefore(columna, 2)")) {
  throw new Error("Personal debe insertar fechas atrasadas en orden cronológico");
}
if (!source.includes("insertColumnsBefore(columna, 4)")) {
  throw new Error("Llaves debe insertar fechas atrasadas en orden cronológico");
}

console.log("Contrato mensual de Google Sheets: OK");
