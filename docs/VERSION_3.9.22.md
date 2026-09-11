# Versión 3.9.22

## Ajustes y recordatorios

- Se agrega la pantalla `Ajustes`, accesible desde el menú principal de Personal y Llaves.
- La apariencia permite elegir modo automático, claro u oscuro.
- Se puede elegir si la app inicia en Personal o en Llaves.
- Se puede activar o desactivar la vibración al completar un movimiento.
- Se puede restaurar la apariencia, la pantalla inicial y la configuración de recordatorios.
- Se agrega un recordatorio configurable para revisar las salidas pendientes.

## Recordatorio de salidas

- Está activado por defecto y queda configurado a las 21:00.
- La hora se puede cambiar desde `Ajustes > Recordatorios`.
- Solo se muestra si hay al menos una persona activa, no retirada y con estado `Dentro`.
- Al tocar la notificación se abre Personal filtrado por `Dentro`.
- Usa el programador de Android, por lo que puede ejecutarse aunque la app esté cerrada normalmente o el teléfono se haya reiniciado.
- Android puede retrasar unos minutos la entrega por ahorro de batería. No se ejecuta si el usuario fuerza la detención de la app o bloquea las notificaciones.
- En Android 13 o posterior se solicita permiso para enviar notificaciones.

## Publicación

- `versionCode`: 52
- `versionName`: 3.9.22
- El APK de entrega conserva la firma estable del proyecto para permitir la actualización sobre versiones anteriores.
