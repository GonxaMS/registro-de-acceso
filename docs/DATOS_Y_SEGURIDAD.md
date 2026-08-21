# Datos y seguridad

## Colecciones de Firestore

### `personal`

Cada documento representa un operario. El identificador tiene el formato `P0001`.

Campos principales:

- `nombre`
- `estado`: `Dentro` o `Fuera`
- `ultimoMovimiento`
- `fecha`
- `hora`
- `activo`: define si se muestra en la lista

### `movimientos`

Cada documento representa un hecho inmutable y tiene un identificador como `M000001`.

Campos principales:

- `movimientoId`
- `personalId`
- `nombre`
- `movimiento`: ingreso, salida, corrección o anulación
- `fecha` y `hora`
- `usuario`: nombre humano de quien realizó la operación
- `creado`: fecha técnica del servidor

Una corrección contiene `reemplazaA`. Una anulación contiene `anulaA`. Esto conserva el historial y evita conflictos con registros ya creados.

### `meta/config`

Guarda los próximos números de operario y movimiento para que los identificadores sean correlativos.

## Reglas

Las reglas de Firestore permiten leer y crear solo a clientes autenticados de forma anónima. No permiten eliminar documentos de operarios ni movimientos. Las actualizaciones de un operario están limitadas a su estado operativo.

## Información que no se publica

El repositorio excluye `google-services.json`, `local.properties`, APKs, configuraciones locales y el Apps Script de producción. Antes de publicar, nunca se deben subir claves, enlaces privados ni identificadores reales de planillas.
