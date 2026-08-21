# Arquitectura

Registro de Acceso es una aplicación Android privada para controlar los ingresos y salidas de operarios.

## Componentes

| Componente | Función |
| --- | --- |
| Android | Lista de operarios, registro de movimientos, corrección de hora y anulaciones. |
| Firebase Authentication | Inicia sesión anónima internamente para permitir el acceso a Firestore. No se muestra ningún identificador técnico al usuario. |
| Cloud Firestore | Fuente principal de datos: operarios, movimientos y contadores de identificadores. |
| Google Sheets | Copia visual secundaria de Personal y Registros. |
| Apps Script | Recibe la copia desde la aplicación y actualiza la planilla. |

## Código Android

- `AccessActivity`: pantalla principal y operaciones de negocio.
- `SetupActivity`: solicita el nombre del usuario al abrir por primera vez.
- `PeopleAdapter`: presenta la lista y los botones de cada operario.
- `Person`: modelo mínimo de un operario en pantalla.
- `SheetsClient`: envía una copia secundaria a Google Sheets.

## Fuente de verdad

Firestore es la fuente principal. Sheets se usa como copia y vista operativa. Si la copia a Sheets falla por falta de conexión, el movimiento seguirá guardado en Firebase.
