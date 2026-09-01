# Guía de operación

## Primer uso

La primera apertura solicita el nombre de quien utiliza el dispositivo. Puede cambiarse desde `Cambiar usuario`. Ese texto queda asentado en cada movimiento nuevo.

## Operarios

El menú superior permite agregar, ocultar y volver a mostrar operarios. Retirar un operario lo excluye definitivamente de las listas operativas, pero conserva su documento y su historial.

## Ingresos y salidas

- Solo se admite ingreso cuando el operario está fuera.
- Solo se admite salida cuando está dentro.
- La hora se guarda en formato `HH:mm`.
- Normal y Administrativo pueden quitar el ingreso o la salida del día actual. Si existen ambos,
  primero se quita la salida.
- Normal y Administrativo pueden modificar manualmente la hora de los movimientos del día desde el
  engranaje. Las correcciones de otras fechas continúan reservadas a Administrativo.

## Correcciones de días anteriores

La opción `Administración` aparece únicamente en dispositivos con permiso administrativo activo en Firebase. Desde ese panel se accede a las correcciones de personal y llaves, permisos de dispositivos, estado y errores de sincronización y reconstrucción mensual de planillas.

## Permisos administrativos

El permiso administrativo depende del UID de Firebase del dispositivo, no del nombre operativo.
Un administrador puede abrir `Administración` desde el menú principal, entrar en `Dispositivos · asignar permisos` y asignar los
estados `Administrativo`, `Normal` o `Bloqueado` a otros teléfonos registrados. Los dispositivos
nuevos quedan bloqueados y no pueden leer información operativa hasta recibir autorización.
El permiso del teléfono actual no puede quitarse
desde la propia app, para evitar un bloqueo accidental. Un dispositivo aparece en la lista después
de abrir al menos una vez la versión 3.9.6 o posterior.

Los cambios de rol se comprueban al entrar en la app. Si una app ya está abierta cuando cambia su
permiso, debe cerrarse y abrirse para actualizar completamente la pantalla.

1. Seleccionar el operario. Las jornadas antiguas pendientes aparecen primero de forma automática.
2. Seleccionar una fecha anterior al día actual.
3. Agregar el ingreso o la salida faltante, o corregir la hora existente.
4. Confirmar el cambio.

La corrección crea un movimiento nuevo y conserva el documento anterior. Si se agrega la salida de la misma jornada antigua que mantiene al operario `Dentro`, la app lo deja `Fuera` y habilita un nuevo ingreso. Una corrección de otra fecha no sobrescribe el estado operativo más reciente.
## Registro de llaves

Se abre desde el acceso `Llaves` de la pantalla principal.

- `Retirar`: solo está habilitado si la llave está disponible. Se puede elegir un operario existente o escribir otro nombre.
- `Devolver`: solo está habilitado si está prestada. Se selecciona quién realiza la devolución; puede ser distinto de quien la retiró.
- El nombre escrito debe respetar `APELLIDO NOMBRE`, se guarda en MAYÚSCULAS y vuelve a aparecer como opción `(solo llaves)`. No crea ni modifica registros en Personal.
- Cada tarjeta muestra quién tiene la llave y desde qué fecha y hora.
- Una llave prestada no se puede ocultar.
- Las llaves ocultas conservan su historial y pueden volver a mostrarse.

En cada pestaña mensual de llaves, por ejemplo `Registro Llaves 2026-8`, cada llave ocupa una sola fila. Cada día agrega un bloque de columnas con la hora y el operario del retiro y de la devolución. La llave aparece en verde, el retiro en amarillo y la devolución en azul; el historial técnico permanece oculto.
## Copia automática en la planilla

Después de confirmar un movimiento en Firebase, el registro ya está terminado y no debe repetirse.
Apps Script consulta Firestore periódicamente y reconstruye la copia en Google Sheets. Si una
sincronización falla, el siguiente ciclo vuelve a intentarla; cada movimiento conserva su ID para
evitar duplicados.

Las pestañas se seleccionan por la fecha del movimiento. Los ingresos y salidas usan `Registro Personal AÑO-M` y las llaves usan `Registro Llaves AÑO-M`, en ambos casos sin cero inicial en el mes. Las pestañas antiguas `Registros` y `Registros Llaves` quedan como archivo y no reciben movimientos nuevos.

## Errores persistentes

En las pantallas de llaves, un fallo de carga conserva los datos anteriores y ofrece Reintentar. Si
la carga vuelve a fallar después del reintento, el evento se guarda en `erroresApp` y aparece en la
pantalla de Admin. Marcarlo como resuelto lo oculta de pendientes, pero no elimina el historial.

Las operaciones completadas correctamente muestran un aviso breve en la parte inferior y no exigen cerrar un cuadro. Las confirmaciones para cambios delicados y los mensajes de error sí permanecen en pantalla.
