const SPREADSHEET_ID = "1qh4aP9Hot-tAO5i7fEfzNTqQ2YK71xFkwgrm7uuDIcw";
const ZONA_HORARIA = "America/Argentina/Mendoza";

function doGet(e) {
  try {
    var datos = obtenerDatos_(e);
    if (!datos.accion && !datos.id && !datos.movimiento) {
      return responder_({ ok: true, mensaje: "API Registro Guardia activa" });
    }
    return procesarRegistro_(datos);
  } catch (error) {
    return responder_({ ok: false, mensaje: error.message });
  }
}

function doPost(e) {
  try {
    return procesarRegistro_(obtenerDatos_(e));
  } catch (error) {
    return responder_({
      ok: false,
      mensaje: error.message
    });
  }
}

function procesarRegistro_(datos) {
  var libro = SpreadsheetApp.openById(SPREADSHEET_ID);

  if (String(datos.accion || "") === "llave_movimiento") {
    return registrarMovimientoLlave_(libro, datos);
  }

  return responder_({
    ok: true,
    mensaje: "Movimiento de personal ignorado en Sheets"
  });
}

function registrarMovimientoPersonal_(libro, datos) {
  var hoja = obtenerHoja_(libro, "Registros", [
    "Fecha",
    "Hora",
    "ID",
    "Nombre",
    "Movimiento"
  ]);

  var id = String(datos.id || "").trim();
  var nombre = String(datos.nombre || "").trim();
  var movimiento = String(datos.movimiento || "").toUpperCase();
  var fecha = String(datos.fecha || "").trim();
  var hora = String(datos.hora || "").trim();

  var ahora = new Date();
  if (!fecha) fecha = Utilities.formatDate(ahora, ZONA_HORARIA, "dd/MM/yyyy");
  if (!hora) hora = Utilities.formatDate(ahora, ZONA_HORARIA, "HH:mm:ss");

  hoja.appendRow([
    fecha,
    hora,
    id,
    nombre,
    movimiento
  ]);

  return responder_({
    ok: true,
    mensaje: "Registro guardado",
    fecha: fecha,
    hora: hora
  });
}

function registrarMovimientoLlave_(libro, datos) {
  var hoja = obtenerHoja_(libro, "RegistrosLlaves", [
    "Fecha",
    "Hora",
    "Llave ID",
    "Llave",
    "Movimiento",
    "Persona",
    "Usuario"
  ]);

  var fecha = String(datos.fecha || "").trim();
  var hora = String(datos.hora || "").trim();
  var ahora = new Date();
  if (!fecha) fecha = Utilities.formatDate(ahora, ZONA_HORARIA, "dd/MM/yyyy");
  if (!hora) hora = Utilities.formatDate(ahora, ZONA_HORARIA, "HH:mm:ss");

  hoja.appendRow([
    fecha,
    hora,
    String(datos.llaveId || "").trim(),
    String(datos.llave || "").trim(),
    String(datos.movimiento || "").trim(),
    String(datos.persona || "").trim(),
    String(datos.usuario || "").trim()
  ]);

  return responder_({
    ok: true,
    mensaje: "Registro de llave guardado",
    fecha: fecha,
    hora: hora
  });
}

function obtenerDatos_(e) {
  if (!e) return {};

  var parametros = e.parameter || {};
  if (Object.keys(parametros).length > 0) return parametros;

  var contenido = e.postData && e.postData.contents ? e.postData.contents : "";
  if (!contenido) return {};

  try {
    return JSON.parse(contenido);
  } catch (error) {
    return contenido.split("&").reduce(function(resultado, parte) {
      var piezas = parte.split("=");
      var clave = decodeURIComponent((piezas[0] || "").replace(/\+/g, " "));
      var valor = decodeURIComponent((piezas.slice(1).join("=") || "").replace(/\+/g, " "));
      if (clave) resultado[clave] = valor;
      return resultado;
    }, {});
  }
}

function obtenerHoja_(libro, nombre, encabezados) {
  var hoja = libro.getSheetByName(nombre);
  if (!hoja) hoja = libro.insertSheet(nombre);

  if (hoja.getLastRow() === 0) {
    hoja.appendRow(encabezados);
  }

  return hoja;
}

function responder_(datos) {
  return ContentService
    .createTextOutput(JSON.stringify(datos))
    .setMimeType(ContentService.MimeType.JSON);
}

function probarAPI() {
  var url = "https://script.google.com/macros/s/AKfycbyerApHKgpzWBr0fqZBXX0k_N6aoAyvHUsVatbtQU-H699ET8p2mckCrxlqUgxUW7If/exec";

  var datos = {
    id: "001",
    nombre: "PRUEBA",
    movimiento: "ENTRADA"
  };

  var opciones = {
    method: "post",
    contentType: "application/json",
    payload: JSON.stringify(datos),
    muteHttpExceptions: true
  };

  var respuesta = UrlFetchApp.fetch(url, opciones);
  Logger.log(respuesta.getContentText());
}

function probarRegistroLlave() {
  var url = "https://script.google.com/macros/s/AKfycbyerApHKgpzWBr0fqZBXX0k_N6aoAyvHUsVatbtQU-H699ET8p2mckCrxlqUgxUW7If/exec";

  var datos = {
    accion: "llave_movimiento",
    llaveId: "K0001",
    llave: "Llave prueba",
    movimiento: "Retiro",
    persona: "OPERARIO PRUEBA",
    fecha: Utilities.formatDate(new Date(), ZONA_HORARIA, "dd/MM/yyyy"),
    hora: Utilities.formatDate(new Date(), ZONA_HORARIA, "HH:mm:ss"),
    usuario: "GUARDIA PRUEBA"
  };

  var opciones = {
    method: "post",
    contentType: "application/json",
    payload: JSON.stringify(datos),
    muteHttpExceptions: true
  };

  var respuesta = UrlFetchApp.fetch(url, opciones);
  Logger.log(respuesta.getContentText());
}
