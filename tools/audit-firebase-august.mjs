import fs from "node:fs/promises";

const source = JSON.parse(await fs.readFile("outputs/firebase-2026-08.json", "utf8"));
const errors = [];
const warnings = [];

const byId = rows => new Map(rows.map(row => [row.id, row]));
const peopleById = byId(source.personal);
const keysById = byId(source.llaves);
const movementsById = byId(source.movimientos);

const orderKey = row => `${row.creado || row._createTime || ""}|${row.id}`;
const orderedPersonal = [...source.movimientos].sort((a, b) => orderKey(a).localeCompare(orderKey(b)));
const orderedKeys = [...source.movimientosLlaves].sort((a, b) => orderKey(a).localeCompare(orderKey(b)));

function issue(bucket, code, row, detail) {
  bucket.push({ code, id: row.id, detail });
}

for (const row of orderedPersonal) {
  if (!/^M\d{6}$/.test(row.id) || row.movimientoId !== row.id) issue(errors, "ID_PERSONAL_INVALIDO", row, row.movimientoId || "");
  if (!peopleById.has(row.personalId)) issue(errors, "PERSONAL_INEXISTENTE", row, row.personalId || "");
  if (!/^\d{2}:\d{2}$/.test(row.hora || "")) issue(errors, "HORA_INVALIDA", row, row.hora || "");
  if (row.reemplazaA && !movementsById.has(row.reemplazaA)) issue(errors, "CORRECCION_SIN_ORIGINAL", row, row.reemplazaA);
  if (row.anulaA && !movementsById.has(row.anulaA)) issue(errors, "ANULACION_SIN_ORIGINAL", row, row.anulaA);
}

for (const row of orderedKeys) {
  if (!/^L\d{6}$/.test(row.id) || row.movimientoId !== row.id) issue(errors, "ID_LLAVE_INVALIDO", row, row.movimientoId || "");
  if (!keysById.has(row.llaveId)) issue(errors, "LLAVE_INEXISTENTE", row, row.llaveId || "");
  if (row.personaId && !peopleById.has(row.personaId)) issue(errors, "PERSONAL_LLAVE_INEXISTENTE", row, row.personaId);
  if (!/^\d{2}:\d{2}$/.test(row.hora || "")) issue(errors, "HORA_LLAVE_INVALIDA", row, row.hora || "");
}

const personalCells = new Map();
for (const row of orderedPersonal) {
  const type = row.movimiento;
  const baseType = type === "AnulacionIngreso" ? "Ingreso" : type === "AnulacionSalida" ? "Salida" : type;
  const key = `${row.personalId}|${row.fecha}|${baseType}`;
  if (type.startsWith("Anulacion")) {
    personalCells.delete(key);
    continue;
  }
  const previous = personalCells.get(key);
  if (previous && !row.esCorreccion) issue(warnings, "MOVIMIENTO_REPETIDO_REEMPLAZADO", row, `Reemplaza ${previous.id} en ${row.fecha}`);
  personalCells.set(key, row);
}

const personalByDay = new Map();
for (const row of personalCells.values()) {
  const key = `${row.personalId}|${row.fecha}`;
  const day = personalByDay.get(key) || {};
  day[row.movimiento] = row;
  personalByDay.set(key, day);
}
for (const [key, day] of personalByDay) {
  if (day.Salida && !day.Ingreso) warnings.push({ code: "SALIDA_SIN_INGRESO", id: day.Salida.id, detail: key });
  if (day.Ingreso && day.Salida && day.Salida.hora < day.Ingreso.hora) warnings.push({ code: "SALIDA_ANTES_DEL_INGRESO", id: day.Salida.id, detail: key });
}

const keyState = new Map();
for (const row of orderedKeys) {
  const state = keyState.get(row.llaveId) || "Disponible";
  if (row.movimiento === "Retiro" && state === "Prestada") issue(warnings, "RETIRO_SIN_DEVOLUCION_PREVIA", row, row.llaveId);
  if (row.movimiento === "Devolucion" && state === "Disponible") issue(warnings, "DEVOLUCION_SIN_RETIRO_PREVIO_EN_AGOSTO", row, row.llaveId);
  keyState.set(row.llaveId, row.movimiento === "Retiro" ? "Prestada" : "Disponible");
}

const effectivePersonal = [...personalCells.values()].sort((a, b) =>
  `${a.fecha.split("/").reverse().join("-")}|${a.nombre}|${a.movimiento}`.localeCompare(`${b.fecha.split("/").reverse().join("-")}|${b.nombre}|${b.movimiento}`)
);

const audit = {
  sourceTotals: source.totals,
  effectivePersonalCount: effectivePersonal.length,
  keyMovementCount: orderedKeys.length,
  errors,
  warnings,
  effectivePersonal,
  keyMovements: orderedKeys,
};
await fs.writeFile("outputs/audit-2026-08.json", JSON.stringify(audit, null, 2));
console.log(JSON.stringify({
  sourceTotals: source.totals,
  effectivePersonalCount: effectivePersonal.length,
  errors: errors.length,
  warnings: warnings.length,
  errorCodes: Object.fromEntries(Object.entries(Object.groupBy(errors, row => row.code)).map(([key, value]) => [key, value.length])),
  warningCodes: Object.fromEntries(Object.entries(Object.groupBy(warnings, row => row.code)).map(([key, value]) => [key, value.length])),
}));
