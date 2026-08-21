# Registro de Acceso

Aplicación Android privada para registrar ingresos y salidas de operarios. Mantiene el estado de cada operario en Firebase y envía una copia visual a Google Sheets.

## Funciones

- Alta y ocultamiento de operarios sin borrar su historial.
- Ingreso y salida con fecha y hora sin segundos.
- Estado visible: dentro, fuera o jornada completada.
- Usuario identificable por dispositivo para dejar asentado quién registró el movimiento.
- Corrección manual de la hora durante el día actual.
- Anulación de ingreso o salida durante el día actual, conservando el historial en Firebase.
- Copia automática a una planilla de Google Sheets.

## Documentación

- [Arquitectura](docs/ARQUITECTURA.md)
- [Uso diario](docs/OPERACION.md)
- [Datos y seguridad](docs/DATOS_Y_SEGURIDAD.md)
- [Instalación y despliegue](docs/INSTALACION_Y_DESPLIEGUE.md)

## Abrir y compilar

1. Abrir esta carpeta en Android Studio.
2. Copiar `local.properties.example` como `local.properties` y completar la ruta del SDK y, si corresponde, la conexión de Sheets.
3. Colocar el archivo de Firebase propio en `app/google-services.json`.
4. Ejecutar:

```powershell
.\gradlew.bat assembleDebug
```

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Importante antes de publicar

Este repositorio no incluye datos privados del entorno de producción: la configuración local, el archivo de Firebase, APKs y la conexión de Sheets están excluidos por `.gitignore`.