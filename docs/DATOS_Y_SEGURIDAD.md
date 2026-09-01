# Datos y seguridad

## Colecciones de Firestore

### `personal`

Documentos `P0001` con nombre, estado, último movimiento, fecha, hora, `activo` y `retirado`.

### `movimientos`

Hechos inmutables `M000001`. Guardan `personalId`, nombre, movimiento, fecha, hora, usuario y marca de tiempo del servidor. Las correcciones usan `reemplazaA`; las anulaciones usan `anulaA`.

### `llaves`

Documentos `K0001` con nombre, estado, operario que la tiene, fecha y hora del retiro y el ID de su último movimiento. Los nombres se muestran en la interfaz; los IDs permiten mantener relaciones estables.

### `operariosLlaves`

Lista separada de nombres escritos al retirar o devolver llaves. Se almacenan en mayúsculas y no pertenecen a la colección `personal`; sirven únicamente para volver a elegirlos en el módulo de llaves.
### `movimientosLlaves`

Hechos inmutables `L000001`. Cada movimiento guarda:

- ID y nombre de la llave.
- ID y nombre del operario. El ID queda vacío cuando se utiliza un nombre escrito que no pertenece a Personal.
- `Retiro` o `Devolucion`.
- Fecha, hora y usuario que registró.
- Marca de tiempo del servidor.

### `meta/config`

Mantiene contadores separados para operarios, movimientos de acceso, llaves y movimientos de llaves.

### `administradores`, `dispositivos` y `servicios`

`administradores/UID` concede el rol Administrativo. `dispositivos/UID` conserva el nombre visible y
el estado Normal o Bloqueado. `servicios/UID` autoriza exclusivamente a Apps Script para leer Firebase
y actualizar el estado de sincronización.

### `sincronizacion`, `erroresSincronizacion` y `erroresApp`

`sincronizacion/sheets` contiene el último resultado de Firebase → Sheets. `erroresSincronizacion`
guarda fallos de Apps Script. `erroresApp` guarda fallos de carga que vuelven a ocurrir después de que
el usuario pulsa Reintentar. Solo Admin puede consultar y marcar estos últimos como resueltos.

## Reglas

- Se requiere una sesión Firebase autenticada.
- Los movimientos no se actualizan ni se eliminan.
- Un préstamo o devolución debe crear su movimiento y actualizar la llave en la misma transacción.
- El operario referenciado debe existir y no estar retirado.
- Los IDs, estados y campos permitidos se validan en Firestore.
- Las llaves prestadas no pueden ocultarse.

## Acceso Admin

El acceso administrativo depende del UID autenticado y de `administradores/UID` con `activo: true`.
El nombre operativo no concede permisos. Un dispositivo nuevo se registra como Bloqueado y debe ser
habilitado desde `Administrar dispositivos` por otro administrador.
## Secretos

No se versionan `google-services.json`, `local.properties`, URLs privadas ni IDs de planillas. Apps
Script obtiene la configuración de Firebase y `ID_PLANILLA` desde sus propiedades privadas.
## Sincronización de Sheets

Android no envía solicitudes a Sheets. Apps Script consulta Firestore cada minuto y usa las hojas técnicas ocultas para omitir IDs ya procesados. Los fallos se guardan en erroresSincronizacion y el último estado en sincronizacion/sheets; la pantalla de Admin muestra ambos.

Apps Script requiere FIREBASE_PROJECT_ID y FIREBASE_API_KEY en sus propiedades privadas. El token anónimo renovable se conserva como FIREBASE_REFRESH_TOKEN.
Si ese token deja de poder renovarse, el sincronizador informa el error y conserva el UID esperado;
no crea automáticamente otra identidad sin autorización. La recuperación requiere ejecutar nuevamente
la configuración y autorizar explícitamente el UID devuelto en `servicios`.
