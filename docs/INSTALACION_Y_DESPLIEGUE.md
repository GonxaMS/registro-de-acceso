# Instalación y despliegue

## Requisitos

- Android Studio reciente.
- JDK compatible con Android Studio.
- Proyecto de Firebase con Authentication anónimo y Cloud Firestore.
- Una planilla de Google Sheets y un Apps Script si se desea la copia secundaria.

## Preparar Firebase

1. Crear o seleccionar un proyecto Firebase.
2. Registrar una aplicación Android con el identificador del paquete.
3. Descargar `google-services.json` y colocarlo en `app/google-services.json`.
4. Activar Authentication con acceso anónimo.
5. Crear Firestore y publicar `firestore.rules`.

## Compilar la APK

En la carpeta del proyecto Android:

```powershell
.\gradlew.bat assembleDebug
```

La APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Copia a Google Sheets

La aplicación usa un Apps Script como receptor. El script debe validar una clave de conexión, recibir el operario, fecha, hora y movimiento, y reflejarlo en las hojas `Personal` y `Registros`.

No guardar en GitHub el enlace del despliegue, la clave ni el identificador de la planilla de producción. Usar variables privadas o una configuración local cuando el proyecto se comparta.

## Publicar reglas de Firestore

Con Firebase CLI autenticado:

```powershell
firebase deploy --only firestore:rules
```

## Publicar Apps Script

Con clasp autenticado y el proyecto vinculado:

```powershell
clasp push
clasp deploy -i ID_DEL_DESPLIEGUE
```
