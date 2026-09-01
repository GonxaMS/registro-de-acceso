import fs from "node:fs/promises";
import path from "node:path";

const projectRoot = process.cwd();
const googleConfig = JSON.parse(await fs.readFile(path.join(projectRoot, "app", "google-services.json"), "utf8"));
const projectId = googleConfig.project_info.project_id;
const apiKey = googleConfig.client[0].api_key[0].current_key;

const authResponse = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${encodeURIComponent(apiKey)}`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ returnSecureToken: true }),
});
if (!authResponse.ok) throw new Error(`Firebase Auth ${authResponse.status}: ${await authResponse.text()}`);
const { idToken } = await authResponse.json();

function decodeValue(value) {
  if (!value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("timestampValue" in value) return value.timestampValue;
  if ("nullValue" in value) return null;
  if ("arrayValue" in value) return (value.arrayValue.values || []).map(decodeValue);
  if ("mapValue" in value) return decodeFields(value.mapValue.fields || {});
  return null;
}

function decodeFields(fields) {
  return Object.fromEntries(Object.entries(fields || {}).map(([key, value]) => [key, decodeValue(value)]));
}

async function readCollection(collection) {
  const rows = [];
  let pageToken = "";
  do {
    const endpoint = new URL(`https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collection}`);
    endpoint.searchParams.set("pageSize", "1000");
    if (pageToken) endpoint.searchParams.set("pageToken", pageToken);
    const response = await fetch(endpoint, { headers: { authorization: `Bearer ${idToken}` } });
    if (!response.ok) throw new Error(`Firestore ${collection} ${response.status}: ${await response.text()}`);
    const payload = await response.json();
    for (const document of payload.documents || []) {
      rows.push({
        id: document.name.split("/").pop(),
        ...decodeFields(document.fields),
        _createTime: document.createTime,
        _updateTime: document.updateTime,
      });
    }
    pageToken = payload.nextPageToken || "";
  } while (pageToken);
  return rows;
}

const [personal, movimientos, llaves, movimientosLlaves] = await Promise.all([
  readCollection("personal"),
  readCollection("movimientos"),
  readCollection("llaves"),
  readCollection("movimientosLlaves"),
]);

function isAugust2026(value) {
  const date = String(value || "").trim();
  return date.startsWith("2026-08-") || /^\d{2}\/08\/2026$/.test(date);
}
const payload = {
  projectId,
  exportedAt: new Date().toISOString(),
  personal,
  llaves,
  movimientos: movimientos.filter(row => isAugust2026(row.fecha)),
  movimientosLlaves: movimientosLlaves.filter(row => isAugust2026(row.fecha)),
  totals: {
    personal: personal.length,
    llaves: llaves.length,
    movimientosAgosto: movimientos.filter(row => isAugust2026(row.fecha)).length,
    movimientosLlavesAgosto: movimientosLlaves.filter(row => isAugust2026(row.fecha)).length,
  },
};

const outputPath = path.join(projectRoot, "outputs", "firebase-2026-08.json");
await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.writeFile(outputPath, JSON.stringify(payload, null, 2));
console.log(JSON.stringify(payload.totals));
