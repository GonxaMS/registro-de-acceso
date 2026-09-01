# Arquitectura

## Componentes

| Componente | Función |
| --- | --- |
| Android | Interfaz de operarios, accesos y préstamos de llaves. |
| Firebase Authentication | Sesión anónima interna; el usuario visible es un nombre configurado en la app. |
| Cloud Firestore | Fuente principal de operarios, llaves, movimientos y contadores. |
| Apps Script | Consulta Firestore cada minuto y actualiza la copia secundaria. |
| Google Sheets | Vista operativa de Personal y pestañas mensuales de registros y llaves. |

## Código Android

- `AccessActivity`: accesos, operarios y navegación al módulo de llaves.
- `KeysActivity`: catálogo, búsqueda, préstamos, devoluciones y administración de llaves.
- `AdminDashboardActivity`: panel único con accesos administrativos, estado de Sheets, errores y reconstrucción mensual.
- `AdminCorrectionsActivity`: consulta y corrección de movimientos de días anteriores.
- `AdminKeysActivity`: correcciones históricas de préstamos y devoluciones.
- `AdminDevicesActivity`: asignación remota de roles por dispositivo.
- `BlockedActivity`: pantalla sin datos operativos para dispositivos sin permiso.
- `AdminAccess`: comprobación de los roles Administrativo, Normal y Bloqueado.
- `AppErrorReporter`: registra fallos de carga persistentes para el panel de Admin.
- `KeysAdapter` y `KeyItem`: presentación del estado de cada llave.
- `SetupActivity`: nombre humano del usuario que registra.

Android escribe únicamente en Firestore. No conserva una segunda cola ni envía datos directamente
a Sheets; Apps Script genera esa copia de forma independiente y la app solo abre la planilla para
consultarla.

## Flujo de una llave

1. El usuario elige un operario por su ID interno.
2. Una transacción lee la llave y el contador `meta/config`.
3. La transacción crea un documento correlativo `L000001`, actualiza la llave y avanza el contador.
4. Las reglas verifican que el movimiento, la llave y el operario coincidan dentro de esa misma operación.
5. Cuando Firebase confirma, la operación queda finalizada para Android.
6. Cada minuto Apps Script consulta Firestore, compara los IDs con las hojas técnicas y copia únicamente los pendientes.
7. El estado y los errores quedan disponibles para la pantalla de Admin.

Firestore siempre es la fuente de verdad. Un error de Sheets no revierte el movimiento: el siguiente ciclo vuelve a encontrar el ID pendiente y lo reintenta.
