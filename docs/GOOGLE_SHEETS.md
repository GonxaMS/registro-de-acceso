# Contrato de Google Sheets

Este documento define el comportamiento obligatorio de la copia mensual. Firebase continúa siendo la fuente principal; Google Sheets es una vista secundaria.

## Destino mensual

Todos los meses se crean pestañas nuevas dentro del mismo archivo de Google Sheets. No se crea un archivo separado por mes.

| Tipo de movimiento | Formato de pestaña | Ejemplo agosto de 2026 | Ejemplo septiembre de 2026 |
| --- | --- | --- | --- |
| Ingreso y salida | `Registro Personal AÑO-M` | `Registro Personal 2026-8` | `Registro Personal 2026-9` |
| Retiro y devolución de llaves | `Registro Llaves AÑO-M` | `Registro Llaves 2026-8` | `Registro Llaves 2026-9` |

La diferencia de formato es intencional: el registro de operarios usa el mes sin cero inicial y el registro de llaves usa dos dígitos.

## Reglas que no se deben cambiar

1. La pestaña se elige usando la fecha del movimiento, no la fecha actual del dispositivo ni la fecha de ejecución de Apps Script.
2. Una corrección histórica debe escribirse en la pestaña del mes corregido.
3. La pestaña mensual se crea automáticamente cuando llega el primer movimiento de ese mes. No necesita una tarea programada.
4. Las pestañas antiguas `Registros` y `Registros Llaves` son únicamente archivo. Nunca deben volver a usarse como destino.
5. `Registros - Datos` y `Registros Llaves - Datos` son hojas técnicas ocultas globales. Conservan los identificadores utilizados para impedir duplicados.
6. Un reintento con el mismo `movimientoId` no debe crear una segunda copia.
7. El cambio mensual se resuelve solamente en Apps Script. No requiere modificar ni reinstalar la APK.

## Responsabilidades

- Android confirma el movimiento únicamente en Firebase.
- Apps Script valida la solicitud y calcula el nombre mensual mediante `nombreHojaRegistros(fecha)` o `nombreHojaLlaves(fecha)`.
- Google Sheets presenta los datos; no decide el estado operativo de un operario o una llave.

## Instalación del sincronizador

En las propiedades del proyecto de Apps Script configurar FIREBASE_PROJECT_ID y FIREBASE_API_KEY. Después ejecutar manualmente una vez instalarSincronizacionFirebase. La función crea un único disparador por minuto y recupera inmediatamente todos los movimientos que falten.

## Validación antes de publicar

Ejecutar desde la raíz del proyecto:

```powershell
& node .\tools\test-apps-script-monthly-sheets.js
```

La misma prueba se ejecuta automáticamente en GitHub. Verifica los nombres de enero, agosto, septiembre y diciembre, rechaza fechas inválidas y detecta si vuelven a introducirse destinos fijos.

## Despliegue

Apps Script debe actualizarse sobre el mismo identificador de despliegue que ya utiliza la APK. Crear una dirección nueva sin actualizar la app dejaría movimientos pendientes apuntando al despliegue anterior.
