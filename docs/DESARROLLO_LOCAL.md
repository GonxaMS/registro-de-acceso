# Desarrollo local

## Estado de este entorno Termux

Herramientas instaladas:

- Git
- GitHub CLI (`gh`)
- OpenJDK 21
- Gradle wrapper descargado por `./gradlew`

La carpeta principal de trabajo es:

```text
/data/data/com.termux/files/home/registro-de-acceso
```

## Generar APK sin Android Studio

Si hay Android SDK local, se puede compilar con:

```sh
./gradlew assembleDebug
```

En este Termux actualmente falta Android SDK. Por eso la alternativa preparada es GitHub Actions:

1. En GitHub, abrir el repositorio.
2. Ir a `Settings` > `Secrets and variables` > `Actions`.
3. Crear un secret llamado `GOOGLE_SERVICES_JSON`.
4. Pegar como valor el contenido completo de `app/google-services.json`.
5. Ir a `Actions` > `Android Debug APK` > `Run workflow`.
6. Descargar el artefacto `registro-de-acceso-debug-apk`.

## Archivos privados necesarios

Estos archivos no deben subirse al repositorio:

- `app/google-services.json`
- `local.properties`

Para trabajar con Google Sheets, copiar `local.properties.example` a `local.properties` y completar:

```properties
sheets.url=
sheets.key=
```

Si se instala Android SDK local, agregar tambien:

```properties
sdk.dir=/ruta/al/android/sdk
```
