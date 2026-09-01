const PREFIJO_REGISTROS = "Registro Personal";
const HOJA_REGISTROS_DATOS = "Registros - Datos";
const PREFIJO_LLAVES = "Registro Llaves";
const HOJA_LLAVES_DATOS = "Registros Llaves - Datos";
const FUNCION_SINCRONIZACION_FIREBASE = "sincronizarDesdeFirebase";
const COLECCION_ESTADO_SINCRONIZACION = "sincronizacion";
const COLECCION_ERRORES_SINCRONIZACION = "erroresSincronizacion";
const COLECCION_COMANDOS_ADMIN = "comandosAdmin";
const DOCUMENTO_REHACER_PLANILLAS = "rehacerPlanillas";
const ULTIMO_PERSONAL_FIREBASE = "FIREBASE_ULTIMO_PERSONAL";
const ULTIMO_LLAVES_FIREBASE = "FIREBASE_ULTIMO_LLAVES";
const TAMANO_LOTE_FIREBASE = 500;

function configurarSincronizacionFirebase(projectId, apiKey) {
  projectId = texto(projectId);
  apiKey = texto(apiKey);
  if (!projectId || !apiKey) throw new Error("Proyecto y API key son obligatorios");
  PropertiesService.getScriptProperties().setProperties({
    FIREBASE_PROJECT_ID: projectId,
    FIREBASE_API_KEY: apiKey
  }, false);
  const config = obtenerConfiguracionFirebase();
  obtenerTokenFirebase(config);
  return {ok: true, projectId: projectId,
    uidServicio: texto(config.propiedades.getProperty("FIREBASE_UID"))};
}

function obtenerUidServicioFirebase() {
  const config = obtenerConfiguracionFirebase();
  obtenerTokenFirebase(config);
  const uid = texto(config.propiedades.getProperty("FIREBASE_UID"));
  if (!uid) throw new Error("Firebase Auth no devolvió el UID del servicio");
  return {ok: true, uidServicio: uid};
}

/**
 * Instala un único disparador por minuto y ejecuta la recuperación inicial.
 * Requiere FIREBASE_PROJECT_ID y FIREBASE_API_KEY en las propiedades del script.
 */
function instalarSincronizacionFirebase() {
  obtenerConfiguracionFirebase();
  ScriptApp.getProjectTriggers()
    .filter(trigger => trigger.getHandlerFunction() === FUNCION_SINCRONIZACION_FIREBASE)
    .forEach(trigger => ScriptApp.deleteTrigger(trigger));
  ScriptApp.newTrigger(FUNCION_SINCRONIZACION_FIREBASE)
    .timeBased()
    .everyMinutes(1)
    .create();
  return sincronizarDesdeFirebase();
}

/**
 * Vuelve a crear las dos vistas mensuales usando Firebase como única fuente.
 * Al ejecutarla desde el editor, reconstruye el mes actual. También acepta una
 * fecha dd/MM/yyyy cuando se invoca desde otra función.
 */
function reconstruirPlanillasDesdeFirebase(fechaMes) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    fechaMes = texto(fechaMes) || Utilities.formatDate(
      new Date(), Session.getScriptTimeZone() || "America/Buenos_Aires", "dd/MM/yyyy");
    componentesFecha(fechaMes);

    const config = obtenerConfiguracion();
    const firebase = obtenerConfiguracionFirebase();
    const token = obtenerTokenFirebase(firebase);
    const mes = claveMes(fechaMes);
    const personales = leerColeccionCompletaFirestore(firebase, token, "movimientos")
      .filter(documento => claveMesDocumento(documento) === mes)
      .sort(ordenDocumento);
    const llaves = leerColeccionCompletaFirestore(firebase, token, "movimientosLlaves")
      .filter(documento => claveMesDocumento(documento) === mes)
      .sort(ordenDocumento);

    const vistaPersonal = reiniciarVistaPersonal(config, fechaMes);
    reconstruirVistaPersonalEnBloque(vistaPersonal, personales);

    const vistaLlaves = reiniciarVistaLlaves(config, fechaMes);
    reconstruirVistaLlavesEnBloque(vistaLlaves, llaves);
    SpreadsheetApp.flush();

    const resultado = {
      ok: true,
      mes: mes,
      hojaPersonal: vistaPersonal.getName(),
      hojaLlaves: vistaLlaves.getName(),
      movimientosPersonal: personales.length,
      movimientosLlaves: llaves.length
    };
    console.log(JSON.stringify(resultado));
    return resultado;
  } finally {
    lock.releaseLock();
  }
}

function reconstruirVistaPersonalEnBloque(hoja, documentos) {
  const personas = {};
  documentos.forEach(documento => {
    const nombre = texto(documento.nombre);
    const fecha = texto(documento.fecha);
    const clavePersona = texto(documento.personalId) || normal(nombre);
    const movimiento = normal(documento.movimiento);
    if (!clavePersona || !nombre || !fecha
        || !["ingreso", "salida", "anulacioningreso", "anulacionsalida"].includes(movimiento)) {
      throw new Error("Movimiento personal inválido: " + documento.id);
    }
    if (!personas[clavePersona]) personas[clavePersona] = {nombre: nombre, dias: {}};
    personas[clavePersona].nombre = nombre;
    if (!personas[clavePersona].dias[fecha]) {
      personas[clavePersona].dias[fecha] = {entrada: "", salida: ""};
    }
    const dia = personas[clavePersona].dias[fecha];
    const hora = texto(documento.hora).substring(0, 5);
    if (movimiento === "ingreso") dia.entrada = hora;
    else if (movimiento === "salida") dia.salida = hora;
    else if (movimiento === "anulacioningreso") dia.entrada = "";
    else dia.salida = "";
  });

  const fechas = {};
  Object.keys(personas).forEach(clave => {
    const dias = personas[clave].dias;
    Object.keys(dias).forEach(fecha => {
      if (dias[fecha].entrada || dias[fecha].salida) fechas[fecha] = true;
    });
  });
  const listaFechas = Object.keys(fechas).sort(compararFechas);
  const listaPersonas = Object.keys(personas).map(clave => personas[clave])
    .filter(persona => listaFechas.some(fecha => {
      const dia = persona.dias[fecha];
      return dia && (dia.entrada || dia.salida);
    }))
    .sort((a, b) => a.nombre.localeCompare(b.nombre));
  const columnasNecesarias = 1 + listaFechas.length * 2;
  if (columnasNecesarias > hoja.getMaxColumns()) {
    hoja.insertColumnsAfter(hoja.getMaxColumns(), columnasNecesarias - hoja.getMaxColumns());
  }
  listaFechas.forEach((fecha, indice) => {
    formatearBloqueFechaPersonal(hoja, 2 + indice * 2, fecha);
  });
  if (!listaPersonas.length) return;

  const valores = listaPersonas.map(persona => {
    const fila = [persona.nombre];
    listaFechas.forEach(fecha => {
      const dia = persona.dias[fecha] || {entrada: "", salida: ""};
      fila.push(dia.entrada, dia.salida);
    });
    return fila;
  });
  hoja.getRange(3, 1, valores.length, columnasNecesarias).setValues(valores);
  if (columnasNecesarias > 1) {
    hoja.getRange(3, 2, valores.length, columnasNecesarias - 1)
      .setNumberFormat("HH:mm").setHorizontalAlignment("center");
  }
}

function reconstruirVistaLlavesEnBloque(hoja, documentos) {
  const llaves = {};
  const fechas = {};
  const reemplazados = {};
  documentos.forEach(documento => {
    const reemplaza = texto(documento.reemplazaA || documento.anulaA);
    if (reemplaza) reemplazados[reemplaza] = true;
  });
  documentos.forEach(documento => {
    if (reemplazados[documento.id] || texto(documento.anulaA)) return;
    const nombreLlave = texto(documento.llaveNombre);
    const persona = texto(documento.persona);
    const fecha = texto(documento.fecha);
    const hora = texto(documento.hora).substring(0, 5);
    const movimiento = capitalizar(texto(documento.movimiento));
    const claveLlave = texto(documento.llaveId) || normal(nombreLlave);
    if (!claveLlave || !nombreLlave || !persona || !fecha || !hora
        || !["Retiro", "Devolucion"].includes(movimiento)) {
      throw new Error("Movimiento de llave inválido: " + documento.id);
    }
    if (!llaves[claveLlave]) llaves[claveLlave] = {nombre: nombreLlave, dias: {}};
    llaves[claveLlave].nombre = nombreLlave;
    if (!llaves[claveLlave].dias[fecha]) {
      llaves[claveLlave].dias[fecha] = {retiros: [], retiroPersonas: [],
        devoluciones: [], devolucionPersonas: []};
    }
    const dia = llaves[claveLlave].dias[fecha];
    if (movimiento === "Retiro") {
      dia.retiros.push(hora);
      dia.retiroPersonas.push(persona);
    } else {
      dia.devoluciones.push(hora);
      dia.devolucionPersonas.push(persona);
    }
    fechas[fecha] = true;
  });

  const listaFechas = Object.keys(fechas).sort(compararFechas);
  const listaLlaves = Object.keys(llaves).map(clave => llaves[clave])
    .sort((a, b) => a.nombre.localeCompare(b.nombre));
  const columnasNecesarias = 1 + listaFechas.length * 4;
  if (columnasNecesarias > hoja.getMaxColumns()) {
    hoja.insertColumnsAfter(hoja.getMaxColumns(), columnasNecesarias - hoja.getMaxColumns());
  }
  listaFechas.forEach((fecha, indice) => {
    formatearBloqueFechaLlave(hoja, 2 + indice * 4, fecha);
  });
  if (!listaLlaves.length) return;

  const valores = listaLlaves.map(llave => {
    const fila = [llave.nombre];
    listaFechas.forEach(fecha => {
      const dia = llave.dias[fecha] || {retiros: [], retiroPersonas: [],
        devoluciones: [], devolucionPersonas: []};
      fila.push(dia.retiros.join("\n"), dia.retiroPersonas.join("\n"),
        dia.devoluciones.join("\n"), dia.devolucionPersonas.join("\n"));
    });
    return fila;
  });
  hoja.getRange(3, 1, valores.length, columnasNecesarias).setValues(valores);
  hoja.getRange(3, 1, valores.length, 1).setFontWeight("bold").setHorizontalAlignment("left");
  listaFechas.forEach((fecha, indice) => {
    const columna = 2 + indice * 4;
    hoja.getRange(3, columna, valores.length, 4).setWrap(true).setVerticalAlignment("middle");
    hoja.getRange(3, columna, valores.length, 1).setHorizontalAlignment("center");
    hoja.getRange(3, columna + 2, valores.length, 1).setHorizontalAlignment("center");
  });
  hoja.autoResizeRows(3, valores.length);
  for (let fila = 3; fila < 3 + valores.length; fila++) {
    hoja.setRowHeight(fila, Math.max(32, hoja.getRowHeight(fila)));
  }
}

function leerColeccionCompletaFirestore(config, token, coleccion) {
  const resultado = [];
  let ultimoId = "";
  let lotes = 0;
  while (lotes < 100) {
    const documentos = leerPendientesFirestore(config, token, coleccion, ultimoId);
    if (!documentos.length) return resultado;
    Array.prototype.push.apply(resultado, documentos);
    ultimoId = documentos[documentos.length - 1].id;
    lotes++;
    if (documentos.length < TAMANO_LOTE_FIREBASE) return resultado;
  }
  throw new Error("La colección " + coleccion + " excede el límite de reconstrucción");
}

function claveMesDocumento(documento) {
  const fecha = texto(documento && documento.fecha);
  if (!fecha) return "";
  try {
    return claveMes(fecha);
  } catch (error) {
    return "";
  }
}

function reiniciarVistaPersonal(config, fechaMes) {
  const libro = SpreadsheetApp.openById(config.planillaId);
  const nombre = nombreHojaRegistros(fechaMes);
  let hoja = libro.getSheetByName(nombre);
  if (!hoja) hoja = libro.insertSheet(nombre, 0);
  hoja.getRange(1, 1, hoja.getMaxRows(), hoja.getMaxColumns()).breakApart();
  hoja.clear();
  hoja.setFrozenRows(2);
  hoja.setFrozenColumns(1);
  hoja.setHiddenGridlines(true);
  hoja.setColumnWidth(1, 205);
  hoja.getRange("A2").setValue("Nombre").setFontWeight("bold").setBackground("#b6d7a8");
  hoja.getRange(2, 1, hoja.getMaxRows() - 1, 1).setBackground("#b6d7a8")
    .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
  return hoja;
}

function reiniciarVistaLlaves(config, fechaMes) {
  const libro = SpreadsheetApp.openById(config.planillaId);
  const nombre = nombreHojaLlaves(fechaMes);
  let hoja = libro.getSheetByName(nombre);
  if (!hoja) hoja = libro.insertSheet(nombre);
  hoja.getRange(1, 1, hoja.getMaxRows(), hoja.getMaxColumns()).breakApart();
  hoja.clear();
  prepararBaseLlaves(hoja);
  return hoja;
}

/**
 * Firestore es la fuente única. Apps Script relee los hechos inmutables,
 * descarta los IDs ya asentados y copia los pendientes en orden.
 */
function sincronizarDesdeFirebase() {
  const resultado = sincronizarDatosDesdeFirebase();
  const reconstruccion = procesarSolicitudRehacerPlanillas();
  if (reconstruccion) resultado.reconstruccion = reconstruccion;
  return resultado;
}

function sincronizarDatosDesdeFirebase() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) {
    const resultado = {omitida: true, motivo: "Otra ejecución está actualizando las planillas"};
    console.log(JSON.stringify(resultado));
    return resultado;
  }
  let firebase;
  let token;
  try {
    const config = obtenerConfiguracion();
    firebase = obtenerConfiguracionFirebase();
    token = obtenerTokenFirebase(firebase);
    const resultado = {
      personal: sincronizarColeccionFirestore(
        firebase, token, "movimientos", ULTIMO_PERSONAL_FIREBASE,
        documentos => sincronizarMovimientosPersonales(documentos, config)),
      llaves: sincronizarColeccionFirestore(
        firebase, token, "movimientosLlaves", ULTIMO_LLAVES_FIREBASE,
        documentos => sincronizarMovimientosLlaves(documentos, config))
    };
    SpreadsheetApp.flush();
    guardarEstadoSincronizacion(firebase, token, {
      estado: "Correcto",
      ultimaEjecucion: new Date(),
      ultimoError: "",
      copiadosPersonal: resultado.personal,
      copiadosLlaves: resultado.llaves
    });
    try {
      resolverErroresSincronizacion(firebase, token);
    } catch (errorResolucion) {
      console.warn("No se pudieron resolver fallos anteriores: " + mensajeError(errorResolucion));
    }
    console.log(JSON.stringify(resultado));
    return resultado;
  } catch (error) {
    const mensaje = mensajeError(error);
    if (firebase && token) {
      try {
        guardarEstadoSincronizacion(firebase, token, {
          estado: "Error",
          ultimaEjecucion: new Date(),
          ultimoError: mensaje,
          copiadosPersonal: 0,
          copiadosLlaves: 0
        });
        guardarErrorSincronizacion(firebase, token, mensaje);
      } catch (errorRegistro) {
        console.error("No se pudo registrar el fallo: " + mensajeError(errorRegistro));
      }
    }
    throw error;
  } finally {
    lock.releaseLock();
  }
}

function procesarSolicitudRehacerPlanillas() {
  const firebase = obtenerConfiguracionFirebase();
  const token = obtenerTokenFirebase(firebase);
  const solicitud = leerDocumentoFirestore(
    firebase, token, COLECCION_COMANDOS_ADMIN, DOCUMENTO_REHACER_PLANILLAS);
  if (!solicitud || texto(solicitud.estado) !== "Pendiente") return null;

  const base = {
    estado: "Procesando",
    fechaMes: texto(solicitud.fechaMes),
    solicitadoPor: texto(solicitud.solicitadoPor),
    solicitado: solicitud.solicitado instanceof Date ? solicitud.solicitado : new Date()
  };
  escribirDocumentoFirestore(firebase, token, COLECCION_COMANDOS_ADMIN,
    DOCUMENTO_REHACER_PLANILLAS, base);
  try {
    const resultado = reconstruirPlanillasDesdeFirebase(base.fechaMes);
    escribirDocumentoFirestore(firebase, token, COLECCION_COMANDOS_ADMIN,
      DOCUMENTO_REHACER_PLANILLAS, Object.assign({}, base, {
        estado: "Completado",
        completado: new Date(),
        resultado: JSON.stringify(resultado),
        error: ""
      }));
    return resultado;
  } catch (error) {
    const mensaje = mensajeError(error);
    escribirDocumentoFirestore(firebase, token, COLECCION_COMANDOS_ADMIN,
      DOCUMENTO_REHACER_PLANILLAS, Object.assign({}, base, {
        estado: "Error",
        completado: new Date(),
        resultado: "",
        error: mensaje
      }));
    guardarErrorSincronizacion(firebase, token, "Rehacer planillas: " + mensaje);
    throw error;
  }
}

function sincronizarMovimientosPersonales(documentos, config) {
  const datos = obtenerDatosRegistros(config);
  const procesados = idsProcesados(datos);
  let copiados = 0;
  documentos.sort(ordenDocumento).forEach(documento => {
    if (procesados[documento.id]) return;
    aplicarMovimientoPersonal(documento, config, datos);
    procesados[documento.id] = true;
    copiados++;
  });
  return copiados;
}

function sincronizarMovimientosLlaves(documentos, config) {
  const datos = obtenerDatosLlaves(config);
  const procesados = idsProcesados(datos);
  let copiados = 0;
  documentos.sort(ordenDocumento).forEach(documento => {
    if (procesados[documento.id]) return;
    aplicarMovimientoLlave(documento, config, datos);
    procesados[documento.id] = true;
    copiados++;
  });
  return copiados;
}

function idsProcesados(hoja) {
  const resultado = {};
  if (hoja.getLastRow() < 2) return resultado;
  hoja.getRange(2, 1, hoja.getLastRow() - 1, 1).getDisplayValues()
    .forEach(fila => {
      const id = texto(fila[0]);
      if (id) resultado[id] = true;
    });
  return resultado;
}

function ordenDocumento(a, b) {
  const izquierda = texto(a.creado || a._createTime) + "|" + a.id;
  const derecha = texto(b.creado || b._createTime) + "|" + b.id;
  return izquierda.localeCompare(derecha);
}

function obtenerConfiguracionFirebase() {
  const propiedades = PropertiesService.getScriptProperties();
  const projectId = texto(propiedades.getProperty("FIREBASE_PROJECT_ID"));
  const apiKey = texto(propiedades.getProperty("FIREBASE_API_KEY"));
  if (!projectId || !apiKey) {
    throw new Error("Faltan FIREBASE_PROJECT_ID y FIREBASE_API_KEY en Apps Script");
  }
  return {projectId: projectId, apiKey: apiKey, propiedades: propiedades};
}

function obtenerTokenFirebase(config) {
  const refreshToken = texto(config.propiedades.getProperty("FIREBASE_REFRESH_TOKEN"));
  let respuesta;
  if (refreshToken) {
    respuesta = UrlFetchApp.fetch(
      "https://securetoken.googleapis.com/v1/token?key=" + encodeURIComponent(config.apiKey), {
        method: "post",
        contentType: "application/x-www-form-urlencoded",
        payload: {grant_type: "refresh_token", refresh_token: refreshToken},
        muteHttpExceptions: true
      });
    if (respuesta.getResponseCode() >= 400) {
      throw new Error("Firebase Auth no pudo renovar la identidad del servicio. "
        + "Ejecuta configurarSincronizacionFirebase y autoriza el UID devuelto en Firestore. Código "
        + respuesta.getResponseCode());
    }
  }
  if (!respuesta) {
    respuesta = UrlFetchApp.fetch(
      "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="
        + encodeURIComponent(config.apiKey), {
          method: "post",
          contentType: "application/json",
          payload: JSON.stringify({returnSecureToken: true}),
          muteHttpExceptions: true
        });
  }
  if (respuesta.getResponseCode() >= 400) {
    throw new Error("Firebase Auth " + respuesta.getResponseCode()
      + ": " + respuesta.getContentText());
  }
  const contenido = JSON.parse(respuesta.getContentText());
  const token = texto(contenido.id_token || contenido.idToken);
  const nuevoRefresh = texto(contenido.refresh_token || contenido.refreshToken);
  const uid = texto(contenido.localId || contenido.user_id || contenido.userId);
  if (!token) throw new Error("Firebase Auth no devolvió un token");
  if (nuevoRefresh) config.propiedades.setProperty("FIREBASE_REFRESH_TOKEN", nuevoRefresh);
  if (uid) config.propiedades.setProperty("FIREBASE_UID", uid);
  return token;
}

function sincronizarColeccionFirestore(config, token, coleccion, propiedadUltimoId,
    copiarDocumentos) {
  let ultimoId = texto(config.propiedades.getProperty(propiedadUltimoId));
  let copiados = 0;
  let lotes = 0;
  while (lotes < 10) {
    const documentos = leerPendientesFirestore(config, token, coleccion, ultimoId);
    if (!documentos.length) break;
    copiados += copiarDocumentos(documentos);
    ultimoId = documentos[documentos.length - 1].id;
    config.propiedades.setProperty(propiedadUltimoId, ultimoId);
    lotes++;
    if (documentos.length < TAMANO_LOTE_FIREBASE) break;
  }
  return copiados;
}

/**
 * Consulta por nombre de documento para no releer toda la colección cada minuto.
 * Los IDs M000001/L000001 son crecientes e inmutables.
 */
function leerPendientesFirestore(config, token, coleccion, ultimoId) {
  const consulta = {
    from: [{collectionId: coleccion}],
    orderBy: [{field: {fieldPath: "__name__"}, direction: "ASCENDING"}],
    limit: TAMANO_LOTE_FIREBASE
  };
  if (ultimoId) {
    consulta.where = {fieldFilter: {
      field: {fieldPath: "__name__"},
      op: "GREATER_THAN",
      value: {referenceValue: "projects/" + config.projectId
        + "/databases/(default)/documents/" + coleccion + "/" + ultimoId}
    }};
  }
  const url = "https://firestore.googleapis.com/v1/projects/"
    + encodeURIComponent(config.projectId)
    + "/databases/(default)/documents:runQuery";
  const respuesta = UrlFetchApp.fetch(url, {
    method: "post",
    contentType: "application/json",
    headers: {Authorization: "Bearer " + token},
    payload: JSON.stringify({structuredQuery: consulta}),
    muteHttpExceptions: true
  });
  if (respuesta.getResponseCode() >= 400) {
    throw new Error("Firestore " + coleccion + " " + respuesta.getResponseCode()
      + ": " + respuesta.getContentText());
  }
  return JSON.parse(respuesta.getContentText())
    .filter(resultado => resultado.document)
    .map(resultado => {
      const documento = resultado.document;
      return Object.assign({
        id: documento.name.split("/").pop(),
        _createTime: documento.createTime
      }, decodificarCamposFirestore(documento.fields || {}));
    });
}

function guardarEstadoSincronizacion(config, token, estado) {
  escribirDocumentoFirestore(config, token, COLECCION_ESTADO_SINCRONIZACION, "sheets", estado);
}

function guardarErrorSincronizacion(config, token, mensaje) {
  const id = "E" + Date.now() + "_" + Math.floor(Math.random() * 1000000);
  escribirDocumentoFirestore(config, token, COLECCION_ERRORES_SINCRONIZACION, id, {
    origen: "Apps Script",
    mensaje: mensaje.substring(0, 1000),
    ocurrido: new Date(),
    resuelto: false
  });
}

function resolverErroresSincronizacion(config, token) {
  const pendientes = leerColeccionCompletaFirestore(
    config, token, COLECCION_ERRORES_SINCRONIZACION)
    .filter(documento => documento.resuelto !== true);
  pendientes.forEach(documento => marcarErrorResueltoFirestore(config, token, documento.id));
  return pendientes.length;
}

function marcarErrorResueltoFirestore(config, token, id) {
  const url = "https://firestore.googleapis.com/v1/projects/"
    + encodeURIComponent(config.projectId)
    + "/databases/(default)/documents/" + COLECCION_ERRORES_SINCRONIZACION
    + "/" + encodeURIComponent(id) + "?updateMask.fieldPaths=resuelto";
  const respuesta = UrlFetchApp.fetch(url, {
    method: "patch",
    contentType: "application/json",
    headers: {Authorization: "Bearer " + token},
    payload: JSON.stringify({fields: {resuelto: {booleanValue: true}}}),
    muteHttpExceptions: true
  });
  if (respuesta.getResponseCode() >= 400) {
    throw new Error("Firestore resolver error " + respuesta.getResponseCode()
      + ": " + respuesta.getContentText());
  }
}

function escribirDocumentoFirestore(config, token, coleccion, id, datos) {
  const fields = {};
  Object.keys(datos).forEach(clave => {
    fields[clave] = codificarValorFirestore(datos[clave]);
  });
  const url = "https://firestore.googleapis.com/v1/projects/"
    + encodeURIComponent(config.projectId)
    + "/databases/(default)/documents/" + encodeURIComponent(coleccion)
    + "/" + encodeURIComponent(id);
  const respuesta = UrlFetchApp.fetch(url, {
    method: "patch",
    contentType: "application/json",
    headers: {Authorization: "Bearer " + token},
    payload: JSON.stringify({fields: fields}),
    muteHttpExceptions: true
  });
  if (respuesta.getResponseCode() >= 400) {
    throw new Error("Firestore escritura " + respuesta.getResponseCode()
      + ": " + respuesta.getContentText());
  }
}

function leerDocumentoFirestore(config, token, coleccion, id) {
  const url = "https://firestore.googleapis.com/v1/projects/"
    + encodeURIComponent(config.projectId)
    + "/databases/(default)/documents/" + encodeURIComponent(coleccion)
    + "/" + encodeURIComponent(id);
  const respuesta = UrlFetchApp.fetch(url, {
    method: "get",
    headers: {Authorization: "Bearer " + token},
    muteHttpExceptions: true
  });
  if (respuesta.getResponseCode() === 404) return null;
  if (respuesta.getResponseCode() >= 400) {
    throw new Error("Firestore lectura " + respuesta.getResponseCode()
      + ": " + respuesta.getContentText());
  }
  const documento = JSON.parse(respuesta.getContentText());
  return decodificarCamposFirestore(documento.fields || {});
}

function codificarValorFirestore(valor) {
  if (valor instanceof Date) return {timestampValue: valor.toISOString()};
  if (typeof valor === "boolean") return {booleanValue: valor};
  if (typeof valor === "number" && Number.isInteger(valor)) {
    return {integerValue: String(valor)};
  }
  if (typeof valor === "number") return {doubleValue: valor};
  return {stringValue: texto(valor)};
}

function decodificarCamposFirestore(campos) {
  const resultado = {};
  Object.keys(campos).forEach(clave => {
    resultado[clave] = decodificarValorFirestore(campos[clave]);
  });
  return resultado;
}

function decodificarValorFirestore(valor) {
  if (!valor) return null;
  if (Object.prototype.hasOwnProperty.call(valor, "stringValue")) return valor.stringValue;
  if (Object.prototype.hasOwnProperty.call(valor, "booleanValue")) return valor.booleanValue;
  if (Object.prototype.hasOwnProperty.call(valor, "integerValue")) return Number(valor.integerValue);
  if (Object.prototype.hasOwnProperty.call(valor, "doubleValue")) return valor.doubleValue;
  if (Object.prototype.hasOwnProperty.call(valor, "timestampValue")) return valor.timestampValue;
  if (Object.prototype.hasOwnProperty.call(valor, "nullValue")) return null;
  if (valor.mapValue) return decodificarCamposFirestore(valor.mapValue.fields || {});
  if (valor.arrayValue) return (valor.arrayValue.values || []).map(decodificarValorFirestore);
  return null;
}

function mensajeError(error) {
  return texto(error && error.stack ? error.stack : error).substring(0, 2000);
}

function aplicarMovimientoPersonal(documento, config, datos) {
  const movimientoId = texto(documento.id);
  const personalId = texto(documento.personalId);
  const nombre = texto(documento.nombre);
  const fecha = texto(documento.fecha);
  const hora = texto(documento.hora).substring(0, 5);
  const movimiento = normal(documento.movimiento);
  const esAnulacion = ["anulacioningreso", "anulacionsalida"].includes(movimiento);
  if (!/^M\d{6}$/.test(movimientoId) || !personalId || !nombre || !fecha
      || (!esAnulacion && (!hora || !["ingreso", "salida"].includes(movimiento)))) {
    throw new Error("Movimiento personal inválido: " + movimientoId);
  }

  const registros = obtenerRegistros(config, fecha);
  const fila = obtenerFilaRegistro(registros, nombre);
  const entrada = obtenerColumnasFecha(registros, fecha);
  if (movimiento === "anulacioningreso") {
    registros.getRange(fila, entrada).clearContent();
  } else if (movimiento === "anulacionsalida") {
    registros.getRange(fila, entrada + 1).clearContent();
  } else {
    const columna = movimiento === "ingreso" ? entrada : entrada + 1;
    registros.getRange(fila, columna)
      .setNumberFormat("HH:mm").setValue(hora).setHorizontalAlignment("center");
  }
  ordenarRegistros(registros);
  datos.appendRow([movimientoId, fecha, hora, personalId, nombre, movimiento, false]);
}

function aplicarMovimientoLlave(documento, config, datos) {
  const movimientoId = texto(documento.id);
  const llaveId = texto(documento.llaveId);
  const llave = texto(documento.llaveNombre);
  const movimientoNormal = normal(documento.movimiento);
  const movimiento = movimientoNormal === "retiro" ? "Retiro"
    : movimientoNormal === "devolucion" ? "Devolucion" : "";
  const personaId = texto(documento.personaId);
  const persona = texto(documento.persona);
  const fecha = texto(documento.fecha);
  const hora = texto(documento.hora).substring(0, 5);
  const usuario = texto(documento.usuario);
  if (!/^L\d{6}$/.test(movimientoId) || !/^K\d{4}$/.test(llaveId)
      || !(/^P\d{4}$/.test(personaId) || personaId === "")
      || !llave || !persona || persona.length > 120 || !fecha || !hora || !usuario
      || (!movimiento && !["anulacionretiro", "anulaciondevolucion"].includes(movimientoNormal))) {
    throw new Error("Movimiento de llave inválido: " + movimientoId);
  }

  const objetivoId = texto(documento.reemplazaA || documento.anulaA);
  if (objetivoId) {
    const objetivo = obtenerMovimientoLlaveDatos(datos, objetivoId);
    if (!objetivo) throw new Error("No se encontró el movimiento de llave a modificar: " + objetivoId);
    quitarMovimientoVistaLlaves(config, objetivo);
  }

  datos.appendRow([
    movimientoId, fecha, hora, llaveId, llave, movimiento || movimientoNormal,
    personaId, persona, usuario, texto(documento.reemplazaA), texto(documento.anulaA),
    documento.esAjusteAdmin === true
  ]);
  if (!movimiento) return;
  actualizarVistaLlaves(obtenerRegistrosLlaves(config, fecha),
    llave, fecha, hora, movimiento, persona);
}

function obtenerMovimientoLlaveDatos(datos, movimientoId) {
  if (datos.getLastRow() < 2) return null;
  const valores = datos.getRange(2, 1, datos.getLastRow() - 1, 9).getDisplayValues();
  for (let indice = valores.length - 1; indice >= 0; indice--) {
    if (texto(valores[indice][0]) === movimientoId) {
      return {id: valores[indice][0], fecha: valores[indice][1], hora: valores[indice][2],
        llaveId: valores[indice][3], llave: valores[indice][4], movimiento: valores[indice][5],
        personaId: valores[indice][6], persona: valores[indice][7]};
    }
  }
  return null;
}

function quitarMovimientoVistaLlaves(config, objetivo) {
  const hoja = obtenerRegistrosLlaves(config, objetivo.fecha);
  const fila = buscarFilaLlave(hoja, objetivo.llave);
  if (fila < 0) return;
  const columnaFecha = obtenerColumnasFechaLlave(hoja, objetivo.fecha);
  const retiro = normal(objetivo.movimiento) === "retiro";
  quitarParLinea(hoja.getRange(fila, retiro ? columnaFecha : columnaFecha + 2),
    hoja.getRange(fila, retiro ? columnaFecha + 1 : columnaFecha + 3),
    objetivo.hora, objetivo.persona);
}

function quitarParLinea(celdaHora, celdaPersona, hora, persona) {
  const horas = texto(celdaHora.getDisplayValue()).split("\n").filter(Boolean);
  const personas = texto(celdaPersona.getDisplayValue()).split("\n").filter(Boolean);
  let indice = -1;
  for (let i = 0; i < Math.max(horas.length, personas.length); i++) {
    if (texto(horas[i]) === texto(hora) && texto(personas[i]) === texto(persona)) indice = i;
  }
  if (indice < 0) return;
  horas.splice(indice, 1);
  personas.splice(indice, 1);
  celdaHora.setValue(horas.join("\n"));
  celdaPersona.setValue(personas.join("\n"));
}

function obtenerDatosRegistros(config) {
  const libro = SpreadsheetApp.openById(config.planillaId);
  let hoja = libro.getSheetByName(HOJA_REGISTROS_DATOS);
  if (!hoja) hoja = libro.insertSheet(HOJA_REGISTROS_DATOS);
  if (texto(hoja.getRange(1, 1).getDisplayValue()) !== "Movimiento ID") {
    hoja.clear();
  }
  hoja.getRange(1, 1, 1, 7).setValues([[
    "Movimiento ID", "Fecha", "Hora", "Operario ID", "Operario",
    "Movimiento", "Actualizó estado"
  ]]).setFontWeight("bold");
  if (!hoja.isSheetHidden()) hoja.hideSheet();
  return hoja;
}
function obtenerDatosLlaves(config) {
  const libro = SpreadsheetApp.openById(config.planillaId);
  let hoja = libro.getSheetByName(HOJA_LLAVES_DATOS);
  if (!hoja) hoja = libro.insertSheet(HOJA_LLAVES_DATOS);
  if (texto(hoja.getRange(1, 1).getDisplayValue()) !== "Movimiento ID") {
    hoja.clear();
  }
  hoja.getRange(1, 1, 1, 12).setValues([[
    "Movimiento ID", "Fecha", "Hora", "Llave ID", "Llave",
    "Movimiento", "Operario ID", "Operario", "Registrado por",
    "Reemplaza a", "Anula a", "Ajuste Admin"
  ]]).setFontWeight("bold");
  if (!hoja.isSheetHidden()) hoja.hideSheet();
  return hoja;
}

function obtenerRegistrosLlaves(config, fecha) {
  const libro = SpreadsheetApp.openById(config.planillaId);
  const nombreHoja = nombreHojaLlaves(fecha);
  let hoja = libro.getSheetByName(nombreHoja);
  if (!hoja) hoja = libro.insertSheet(nombreHoja);
  prepararBaseLlaves(hoja);
  return hoja;
}

function prepararBaseLlaves(hoja) {
  hoja.getRange("A1").setValue("");
  hoja.getRange("A2").setValue("Llave").setFontWeight("bold");
  hoja.getRange(1, 1, Math.max(hoja.getMaxRows(), 2), 1)
    .setBackground("#b6d7a8")
    .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
  hoja.setFrozenRows(2);
  hoja.setFrozenColumns(1);
  hoja.setHiddenGridlines(true);
  hoja.setColumnWidth(1, 190);
  hoja.setRowHeight(1, 34);
  hoja.setRowHeight(2, 32);

  for (let columna = 2; columna <= hoja.getLastColumn(); columna += 4) {
    const fecha = limpiarFecha(hoja.getRange(1, columna).getDisplayValue());
    if (fecha && limpiarFecha(hoja.getRange(1, columna + 2).getDisplayValue()) !== fecha) {
      formatearBloqueFechaLlave(hoja, columna, fecha);
    }
  }
}function actualizarVistaLlaves(hoja, llave, fecha, hora, movimiento, persona) {
  let fila = buscarFilaLlave(hoja, llave);
  if (fila === -1) {
    fila = Math.max(hoja.getLastRow() + 1, 3);
    hoja.getRange(fila, 1).setValue(llave);
    ordenarVistaLlaves(hoja);
    fila = buscarFilaLlave(hoja, llave);
  }

  const columna = obtenerColumnasFechaLlave(hoja, fecha);
  const esRetiro = movimiento === "Retiro";
  agregarValorCelda(hoja.getRange(fila, esRetiro ? columna : columna + 2), hora);
  agregarValorCelda(hoja.getRange(fila, esRetiro ? columna + 1 : columna + 3), persona);
  aplicarFormatoFilaVistaLlave(hoja, fila);
}

function buscarFilaLlave(hoja, llave) {
  if (hoja.getLastRow() < 3) return -1;
  const valores = hoja.getRange(3, 1, hoja.getLastRow() - 2, 1).getDisplayValues();
  for (let indice = 0; indice < valores.length; indice++) {
    if (normal(valores[indice][0]) === normal(llave)) return indice + 3;
  }
  return -1;
}

function obtenerColumnasFechaLlave(hoja, fecha) {
  const ultima = Math.max(hoja.getLastColumn(), 1);
  for (let columna = 2; columna <= ultima; columna += 4) {
    const fechaExistente = limpiarFecha(hoja.getRange(1, columna).getDisplayValue());
    if (fechaExistente === limpiarFecha(fecha)) {
      return columna;
    }
    if (fechaExistente && compararFechas(fecha, fechaExistente) < 0) {
      hoja.insertColumnsBefore(columna, 4);
      formatearBloqueFechaLlave(hoja, columna, fecha);
      return columna;
    }
  }

  const columna = ultima < 2 ? 2 : ultima + 1;
  const necesarias = columna + 3 - hoja.getMaxColumns();
  if (necesarias > 0) hoja.insertColumnsAfter(hoja.getMaxColumns(), necesarias);
  formatearBloqueFechaLlave(hoja, columna, fecha);
  return columna;
}function formatearBloqueFechaLlave(hoja, columna, fecha) {
  hoja.getRange(1, columna, 1, 4).breakApart().clearContent();
  hoja.getRange(1, columna, 1, 2).merge().setValue("Fecha " + fecha);
  hoja.getRange(1, columna + 2, 1, 2).merge().setValue("Fecha " + fecha);
  hoja.getRange(2, columna, 1, 4).setValues([[
    "Hora retiro", "Retiró", "Hora devolución", "Devolvió"
  ]]);

  hoja.getRange(1, columna, 2, 2)
    .setBackground("#fff2cc")
    .setFontWeight("bold")
    .setHorizontalAlignment("center")
    .setVerticalAlignment("middle")
    .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
  hoja.getRange(1, columna + 2, 2, 2)
    .setBackground("#c9daf8")
    .setFontWeight("bold")
    .setHorizontalAlignment("center")
    .setVerticalAlignment("middle")
    .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);

  hoja.setColumnWidth(columna, 105);
  hoja.setColumnWidth(columna + 1, 230);
  hoja.setColumnWidth(columna + 2, 125);
  hoja.setColumnWidth(columna + 3, 230);

  const filasVacias = hoja.getMaxRows() - 2;
  if (filasVacias > 0) {
    hoja.getRange(3, columna, filasVacias, 2)
      .setBackground("#fff2cc")
      .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
    hoja.getRange(3, columna + 2, filasVacias, 2)
      .setBackground("#c9daf8")
      .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
  }
}function agregarValorCelda(celda, valor) {
  const anterior = texto(celda.getDisplayValue());
  celda.setValue(anterior ? anterior + "\n" + valor : valor).setWrap(true);
}

function aplicarFormatoFilaVistaLlave(hoja, fila) {
  hoja.getRange(fila, 1)
    .setBackground("#b6d7a8")
    .setFontWeight("bold")
    .setHorizontalAlignment("left")
    .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
  for (let columna = 2; columna <= hoja.getLastColumn(); columna += 4) {
    hoja.getRange(fila, columna, 1, 2).setBackground("#fff2cc");
    hoja.getRange(fila, columna + 2, 1, 2).setBackground("#c9daf8");
    hoja.getRange(fila, columna, 1, 4)
      .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID)
      .setVerticalAlignment("middle");
    hoja.getRange(fila, columna).setHorizontalAlignment("center");
    hoja.getRange(fila, columna + 2).setHorizontalAlignment("center");
  }
  hoja.setRowHeight(fila, Math.max(32, hoja.getRowHeight(fila)));
}function ordenarVistaLlaves(hoja) {
  const cantidad = hoja.getLastRow() - 2;
  if (cantidad > 1) {
    hoja.getRange(3, 1, cantidad, Math.max(hoja.getLastColumn(), 1))
      .sort({column: 1, ascending: true});
  }
}

function ordenarRegistros(hoja) {
  const cantidad = hoja.getLastRow() - 2;
  const ultimaColumna = Math.max(hoja.getLastColumn(), 1);
  if (cantidad > 1) hoja.getRange(3, 1, cantidad, ultimaColumna).sort({column: 1, ascending: true});
  if (cantidad > 0 && ultimaColumna > 1) {
    hoja.getRange(3, 2, cantidad, ultimaColumna - 1)
      .setNumberFormat("HH:mm").setHorizontalAlignment("center");
  }
}

function obtenerRegistros(config, fecha) {
  const libro = SpreadsheetApp.openById(config.planillaId);
  const nombreHoja = nombreHojaRegistros(fecha);
  let hoja = libro.getSheetByName(nombreHoja);
  if (!hoja) {
    hoja = libro.insertSheet(nombreHoja, 0);
    hoja.setFrozenRows(2);
    hoja.setFrozenColumns(1);
    hoja.setColumnWidth(1, 150);
    hoja.getRange("A2").setValue("Nombre").setFontWeight("bold").setBackground("#b6d7a8");
    hoja.getRange(2, 1, hoja.getMaxRows() - 1, 1).setBackground("#b6d7a8")
      .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
  }
  return hoja;
}

function obtenerFilaRegistro(hoja, nombre) {
  if (hoja.getLastRow() >= 3) {
    const nombres = hoja.getRange(3, 1, hoja.getLastRow() - 2, 1).getDisplayValues();
    for (let i = 0; i < nombres.length; i++) {
      if (normal(nombres[i][0]) === normal(nombre)) return i + 3;
    }
  }
  const fila = Math.max(hoja.getLastRow() + 1, 3);
  hoja.getRange(fila, 1).setValue(nombre).setBackground("#b6d7a8");
  colorearFila(hoja, fila);
  ordenarRegistros(hoja);
  return obtenerFilaRegistro(hoja, nombre);
}

function obtenerColumnasFecha(hoja, fecha) {
  const ultima = Math.max(hoja.getLastColumn(), 1);
  if (ultima >= 2) {
    const fechas = hoja.getRange(1, 2, 1, ultima - 1).getDisplayValues()[0];
    for (let i = 0; i < fechas.length; i += 2) {
      const fechaExistente = limpiarFecha(fechas[i]);
      if (fechaExistente === limpiarFecha(fecha)) return i + 2;
      if (fechaExistente && compararFechas(fecha, fechaExistente) < 0) {
        const columna = i + 2;
        hoja.insertColumnsBefore(columna, 2);
        formatearBloqueFechaPersonal(hoja, columna, fecha);
        formatearColoresPersonal(hoja);
        return columna;
      }
    }
  }
  let columna = ultima < 2 ? 2 : ultima + 1;
  if (columna % 2 !== 0) columna++;
  formatearBloqueFechaPersonal(hoja, columna, fecha);
  return columna;
}

function formatearBloqueFechaPersonal(hoja, columna, fecha) {
  const color = ((columna - 2) / 2) % 2 === 0 ? "#fff2cc" : "#c9daf8";
  hoja.getRange(1, columna, 1, 2).breakApart().clearContent().merge();
  hoja.getRange(1, columna).setValue("Fecha " + limpiarFecha(fecha));
  hoja.getRange(2, columna, 1, 2).setValues([["Hora Entrada", "Hora Salida"]]);
  hoja.setColumnWidth(columna, 110);
  hoja.setColumnWidth(columna + 1, 110);
  hoja.getRange(1, columna, hoja.getMaxRows(), 2).setBackground(color)
    .setHorizontalAlignment("center")
    .setBorder(true, true, true, true, true, true, "#000000", SpreadsheetApp.BorderStyle.SOLID);
  hoja.getRange(1, columna, 2, 2)
    .setFontWeight("bold")
    .setVerticalAlignment("middle");
}

function formatearColoresPersonal(hoja) {
  for (let columna = 2; columna <= hoja.getLastColumn(); columna += 2) {
    const fecha = limpiarFecha(hoja.getRange(1, columna).getDisplayValue());
    if (fecha) formatearBloqueFechaPersonal(hoja, columna, fecha);
  }
}

function colorearFila(hoja, fila) {
  hoja.getRange(fila, 1).setBackground("#b6d7a8").setBorder(true, true, true, true, true, true);
  for (let columna = 2; columna <= hoja.getLastColumn(); columna += 2) {
    const color = ((columna - 2) / 2) % 2 === 0 ? "#fff2cc" : "#c9daf8";
    hoja.getRange(fila, columna, 1, Math.min(2, hoja.getLastColumn() - columna + 1))
      .setBackground(color).setBorder(true, true, true, true, true, true);
  }
}

function obtenerConfiguracion() {
  const propiedades = PropertiesService.getScriptProperties();
  const planillaId = texto(propiedades.getProperty("ID_PLANILLA"));
  if (!planillaId) throw new Error("Falta ID_PLANILLA en Apps Script");
  return {planillaId: planillaId};
}

function texto(valor) { return String(valor == null ? "" : valor).trim(); }
function normal(valor) { return texto(valor).toLowerCase(); }
function capitalizar(valor) {
  const limpio = texto(valor).toLowerCase();
  return limpio ? limpio.charAt(0).toUpperCase() + limpio.slice(1) : "";
}
function componentesFecha(valor) {
  const fecha = limpiarFecha(valor);
  let coincidencia = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(fecha);
  let dia;
  let mes;
  let anio;
  if (coincidencia) {
    dia = Number(coincidencia[1]);
    mes = Number(coincidencia[2]);
    anio = Number(coincidencia[3]);
  } else {
    coincidencia = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(fecha);
    if (!coincidencia) throw new Error("Fecha inválida: " + fecha);
    anio = Number(coincidencia[1]);
    mes = Number(coincidencia[2]);
    dia = Number(coincidencia[3]);
  }
  const comprobacion = new Date(Date.UTC(anio, mes - 1, dia));
  if (comprobacion.getUTCFullYear() !== anio
      || comprobacion.getUTCMonth() + 1 !== mes
      || comprobacion.getUTCDate() !== dia) {
    throw new Error("Fecha inválida: " + fecha);
  }
  return {anio: anio, mes: mes, dia: dia};
}

function compararFechas(izquierda, derecha) {
  const a = componentesFecha(izquierda);
  const b = componentesFecha(derecha);
  return (a.anio * 10000 + a.mes * 100 + a.dia)
    - (b.anio * 10000 + b.mes * 100 + b.dia);
}

function claveMes(fecha) {
  const partes = componentesFecha(fecha);
  return partes.anio + "-" + String(partes.mes).padStart(2, "0");
}

function nombreHojaRegistros(fecha) {
  const partes = componentesFecha(fecha);
  return PREFIJO_REGISTROS + " " + partes.anio + "-" + partes.mes;
}

function nombreHojaLlaves(fecha) {
  const partes = componentesFecha(fecha);
  return PREFIJO_LLAVES + " " + partes.anio + "-" + partes.mes;
}
function limpiarFecha(valor) { return texto(valor).replace(/^fecha\s*/i, "").replace(/\s/g, ""); }
