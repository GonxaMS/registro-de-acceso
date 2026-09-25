# Versión 3.10.8

## Herramientas de desarrollo local

- Se reemplazaron los scripts PowerShell de prueba local por `tools/local-test.mjs`.
- El nuevo comando `start` inicia Firebase, el emulador Android, carga datos aislados, compila,
  instala y abre la aplicación.
- Los comandos `seed` y `stop` permiten cargar datos o detener el entorno de forma independiente.
- La herramienta usa el `firebase-tools` instalado en el proyecto y el Android SDK indicado en
  `local.properties`, sin depender de rutas globales ni de una shell específica.

## Compatibilidad

- No cambia el esquema de producción de Firebase ni el contrato de Apps Script.
- No modifica el paquete Android ni requiere migración de datos.
- La aplicación conserva la firma estable utilizada para las actualizaciones.

## Publicación

- `versionCode`: 62
- `versionName`: 3.10.8
- El tag `v3.10.8` activa la compilación y publicación de la APK mediante GitHub Actions.
