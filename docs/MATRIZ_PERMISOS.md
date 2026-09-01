# Matriz de permisos verificada

Estado correspondiente a la versión 3.9.15. La interfaz y las reglas de Firestore deben mantener
esta misma matriz.

| Función | Administrativo | Normal | Bloqueado |
| --- | --- | --- | --- |
| Ver personal, llaves y planilla | Sí | Sí | No |
| Registrar ingreso y salida | Sí | Sí | No |
| Modificar horarios de la fecha operativa actual | Sí | Sí | No |
| Quitar ingreso o salida de la fecha operativa actual | Sí | Sí | No |
| Agregar, ocultar, mostrar o retirar operarios | Sí | Sí | No |
| Prestar y devolver llaves | Sí | Sí | No |
| Agregar, ocultar o mostrar llaves | Sí | Sí | No |
| Cambiar el nombre operativo del usuario | Sí | Sí | No |
| Corregir movimientos de otras fechas | Sí | No | No |
| Corregir movimientos históricos de llaves | Sí | No | No |
| Consultar estado y errores de sincronización | Sí | No | No |
| Marcar errores como resueltos | Sí | No | No |
| Solicitar reconstrucción de planillas | Sí | No | No |
| Asignar roles a otros dispositivos | Sí | No | No |

## Comportamiento de cada rol

### Administrativo

Incluye todas las funciones del rol Normal. Además ve las herramientas históricas, administración de
dispositivos, errores y reconstrucción de planillas. Los movimientos originales son inmutables: una
modificación crea una corrección o anulación vinculada, por lo que “modificar” nunca significa borrar
el historial.

### Normal

Puede realizar toda la operación diaria de personal y llaves, incluida la administración de sus
catálogos. Puede modificar o quitar movimientos de la fecha operativa actual. No ve herramientas
históricas, permisos, errores ni comandos de sincronización.

### Bloqueado

Solo muestra el mensaje para solicitar permiso y el botón Volver a comprobar. No inicia escuchas de
personal o llaves y las reglas de Firebase rechazan la lectura y escritura de datos operativos.

## Verificación técnica

- `AdminAccess.checkRole` decide la pantalla inicial según `administradores` y `dispositivos`.
- `AccessActivity` inicia las consultas únicamente después de aceptar Administrativo o Normal.
- `KeysActivity` vuelve a comprobar el rol antes de consultar datos.
- Todas las pantallas históricas llaman `AdminAccess.check` y se cierran si el UID no es Admin.
- `hasAccess()` en Firestore incluye Administrativo y Normal.
- Las ramas con `isAdmin()` protegen correcciones históricas, dispositivos, errores y comandos.
- Una corrección diaria debe coincidir con la fecha operativa actual guardada en `personal`.
