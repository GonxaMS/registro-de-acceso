# Desarrollo local

## Requisitos

- Android Studio o Android SDK.
- JDK 21 para el entorno actual.
- Node.js 22 para las pruebas de Firestore y Google Sheets.
- Android 7.0 (API 24) o posterior en el dispositivo.
- `google-services.json` y `local.properties` privados.

## Verificación

### Pruebas automáticas

La batería completa usa exclusivamente el emulador local de Firestore y no puede escribir en producción:

```powershell
npm install
npm test
.\gradlew.bat --no-daemon testDebugUnitTest
```

`npm test` valida permisos Administrativo, Normal, Bloqueado y servicio; formatos e inmutabilidad
de movimientos; creación y ocultamiento de llaves; préstamos atómicos; administración de
dispositivos; y el contrato de pestañas mensuales de Google Sheets. También reproduce ingresos,
salidas, retiros y devoluciones completos, y enfrenta dos dispositivos simultáneos para comprobar
que solo una operación pueda modificar a la misma persona o llave. Gradle valida la normalización de
estados y datos que muestra Android.

Las pruebas de integración también interrumpen realmente el canal local hacia Firestore antes y
durante una confirmación. Comprueban que no haya éxito anticipado, escrituras parciales ni duplicados,
y que la operación finalice una sola vez al recuperar la conexión. Finalmente agregan latencia
artificial e informan cuánto demoró la confirmación.

Para ejecutar únicamente los recorridos completos y de concurrencia:

```powershell
npm run test:integration
```

Una ejecución correcta termina con `0 fallidas`, `Contrato mensual de Google Sheets: OK` y
`BUILD SUCCESSFUL`. Los mensajes `PERMISSION_DENIED` que acompañan casos marcados `OK` son
rechazos deliberados: la prueba está comprobando que una operación peligrosa sea bloqueada.

Las mismas pruebas se ejecutan automáticamente en GitHub cuando cambian la app, las reglas o Apps
Script.

### Entorno aislado con emuladores

Para iniciar Firebase local, el teléfono Android virtual, cargar datos de prueba, compilar, instalar y abrir la aplicación:

```powershell
.\tools\start-local-test.ps1 -ResetApp
```

La prueba local contiene tres operarios y dos llaves. Cada inicio limpia los datos anteriores para que la prueba sea repetible. La aplicación usa únicamente Firebase local y desactiva la copia a Google Sheets, por lo que no modifica producción.

El estado local se puede inspeccionar en http://127.0.0.1:4000.

Para detener todo:

```powershell
.\tools\stop-local-test.ps1 -StopAndroidEmulator
```

### Compilación normal

```powershell
.\gradlew.bat --no-daemon clean assembleDebug
```

Antes de publicar:

```powershell
git diff --check
firebase deploy --only firestore:rules --dry-run --project registro-guardias-408cb
```

## Archivos privados

- `app/google-services.json`
- `local.properties`

Nunca modificar el `package_name` dentro de `google-services.json` para simular otra aplicación. Firebase debe tener registrada exactamente la aplicación `com.ejemplo.registroguardias`.

## Versiones

- `versionCode` aumenta en cada APK distribuida.
- `versionName` usa `mayor.menor.parche`.
- La versión actual documentada es `3.9.12` (`versionCode 42`).
- Los APK entregados usan el nombre `RegistroAcceso-vX.Y.Z.apk`.
- Las compilaciones actuales son de desarrollo; antes de una distribución definitiva debe configurarse
  una firma de producción estable.
