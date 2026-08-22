function doPost(e) {
    try {
        var libro = SpreadsheetApp.openById("1qi3_vRZ4_HQnN0mlOUSrvjVgn53B7Xkr4BvQ");
            var hoja = libro.getSheetByName("Registros");
                var datos = JSON.parse(e.postData.contents);

                    var id = datos.id;
                        var nombre = datos.nombre;
                            var movimiento = String(datos.movimiento).toUpperCase();

                                var ahora = new Date();
                                    var zona = "America/Argentina/Mendoza";

                                        var fecha = Utilities.formatDate(ahora, zona, "dd/MM/yyyy");
                                            var hora = Utilities.formatDate(ahora, zona, "HH:mm:ss");

                                                hoja.appendRow([
                                                      fecha,
                                                            hora,
                                                                  id,
                                                                        nombre,
                                                                              movimiento
                                                                                  ]);

                                                                                      return ContentService
                                                                                            .createTextOutput(JSON.stringify({
                                                                                                    ok: true,
                                                                                                            mensaje: "Registro guardado",
                                                                                                                    fecha: fecha,
                                                                                                                            hora: hora
                                                                                                                                  }))
                                                                                                                                        .setMimeType(ContentService.MimeType.JSON);

                                                                                                                                          } catch (error) {
                                                                                                                                              return ContentService
                                                                                                                                                    .createTextOutput(JSON.stringify({
                                                                                                                                                            ok: false,
                                                                                                                                                                    mensaje: error.message
                                                                                                                                                                          }))
                                                                                                                                                                                .setMimeType(ContentService.MimeType.JSON);
                                                                                                                                                                                  }
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
