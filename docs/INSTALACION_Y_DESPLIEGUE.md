# Instalación y despliegue

## Firebase

1. Registrar la app Android `com.ejemplo.registroguardias`.
2. Colocar el `google-services.json` real en `app/google-services.json`.
3. Activar Authentication anónimo y Cloud Firestore.
4. Abrir la app en el primer dispositivo, localizar su sesión anónima en Firebase Authentication y
   copiar el UID. El nombre registrado en `dispositivos` permite reconocerlo.
5. Crear manualmente `administradores/UID` en Firestore con `activo: true` y un campo descriptivo
   `nombre`. Este paso manual solo es necesario para el primer administrador.
6. Ejecutar `configurarSincronizacionFirebase` en Apps Script y copiar el `uidServicio` devuelto.
7. Crear manualmente `servicios/UID_SERVICIO` con `activo: true` y `nombre: "Apps Script"`.
8. Validar reglas con `firebase deploy --only firestore:rules --dry-run`.
9. Publicarlas con `firebase deploy --only firestore:rules --project registro-guardias-408cb`.

El nombre operativo escrito en la app no concede permisos. El rol administrativo depende únicamente
del UID autenticado y la colección `administradores`. Después del alta inicial, un dispositivo
Administrativo puede asignar roles a otros dispositivos desde la app.
La identidad de Apps Script se autoriza por separado en `servicios`; no concede acceso a las
pantallas administrativas.

Si Apps Script informa `PERMISSION_DENIED`, comparar el UID devuelto por
`obtenerUidServicioFirebase` con el documento activo de `servicios`. Debe existir un único UID de
servicio autorizado. Retirar identidades anteriores después de confirmar una ejecución correcta.

Publicar reglas cambia el acceso del proyecto completo y debe hacerse después de una compilación correcta.

## Apps Script y Sheets

1. Copiar `apps-script/Codigo.gs.js` y `apps-script/appsscript.json` al proyecto Apps Script vinculado.
2. En `Configuración del proyecto > Propiedades del script`, configurar `ID_PLANILLA` y las
   propiedades de la cuenta de servicio indicadas por el asistente `configurarSincronizacionFirebase`.
3. Crear el disparador periódico de sincronización desde Apps Script.
4. Guardar únicamente el enlace visible de la planilla en `local.properties`.

El script crea o mantiene `Personal` y pestañas mensuales como `Registro Personal 2026-8` y `Registro Llaves 2026-8`, además de hojas técnicas ocultas. El cambio de mes es automático y usa la fecha del movimiento, por lo que las correcciones históricas van al mes correspondiente. Todos los movimientos usan `movimientoId` para que los reintentos automáticos no dupliquen ni vuelvan a aplicar registros.

## Configuración Android

```properties
sdk.dir=C:\\Ruta\\Android\\Sdk
sheets.web_url=URL_PRIVADA_DE_LA_PLANILLA
```

## GitHub Actions

Configurar estos secretos del repositorio:

- `GOOGLE_SERVICES_JSON`
- `SHEETS_WEB_URL`

El flujo falla si falta alguno, evitando generar un APK parcialmente configurado.
