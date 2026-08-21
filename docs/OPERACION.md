# Guía de operación

## Primer uso

Al abrir la aplicación por primera vez se solicita el nombre de la persona que la utiliza. Ese nombre se guarda en el dispositivo y se registra en los nuevos movimientos de Firebase.

El nombre puede cambiarse desde el menú de tres puntos, en `Cambiar usuario`.

## Operarios

Desde el menú superior se puede:

- Agregar operario.
- Mostrar operarios ocultos.
- Cambiar usuario.

Los operarios no se eliminan: al ocultarlos dejan de aparecer en la lista, pero conservan su identificador y su historial. Un operario que está dentro no se puede ocultar hasta registrar su salida.

## Ingresos y salidas

- Un operario fuera puede registrar un ingreso.
- Un operario dentro puede registrar una salida.
- Solo se permite una secuencia de ingreso y salida por día.
- La hora se guarda en formato de 24 horas, sin segundos.

## Engranaje de cada operario

El engranaje abre un menú pequeño junto al operario con estas acciones:

- `Modificar hora`: crea una corrección para un movimiento de hoy. No altera el registro original.
- `Quitar ingreso de hoy`: anula el ingreso efectivo de hoy y deja al operario fuera.
- `Quitar salida de hoy`: anula la salida efectiva de hoy y devuelve al operario dentro.
- `Ocultar operario`: deja de mostrarlo en la lista sin eliminar datos.

Para quitar un ingreso que ya tiene salida, primero se debe quitar la salida. Las anulaciones y correcciones están limitadas al día actual.
