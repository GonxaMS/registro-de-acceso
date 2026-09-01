# Registro de Acceso

Aplicación Android privada para registrar ingresos, salidas y préstamos de llaves. Firebase es la fuente principal y Google Sheets funciona como copia operativa secundaria.

## Funciones

- Alta, ocultamiento y retiro lógico de operarios sin borrar su historial.
- Ingreso y salida con fecha y hora sin segundos.
- Anulación de ingresos y salidas del día actual para permisos Normal y Administrativo.
- Corrección de horarios y pantalla histórica exclusiva para dispositivos Administrativos.
- Usuario identificable para dejar asentado quién realiza cada operación.
- Catálogo de llaves con estados `Disponible` y `Prestada`.
- Retiro y devolución de llaves vinculados al ID del operario.
- Opción de escribir `APELLIDO NOMBRE`; se convierte a mayúsculas y queda disponible en una lista exclusiva de llaves, sin agregarse a Personal.
- Historial inmutable y correlativo para movimientos de operarios y llaves.
- Copia automática, persistente e idempotente a pestañas mensuales de Google Sheets, con reintentos al recuperar la conexión.
- Permisos remotos `Administrativo`, `Normal` y `Bloqueado` por UID de Firebase.
- Registro en Admin de errores de la app que persisten después de reintentar.
- Panel administrativo unificado con accesos rápidos, estado de Sheets y reconstrucción mensual.
- Avisos inferiores automáticos para operaciones exitosas, sin cuadros que obliguen a cerrarlos.

## Documentación

- [Arquitectura](docs/ARQUITECTURA.md)
- [Uso diario](docs/OPERACION.md)
- [Contrato mensual de Google Sheets](docs/GOOGLE_SHEETS.md)
- [Datos y seguridad](docs/DATOS_Y_SEGURIDAD.md)
- [Instalación y despliegue](docs/INSTALACION_Y_DESPLIEGUE.md)
- [Desarrollo local](docs/DESARROLLO_LOCAL.md)
- [Referencia completa y estado del proyecto](docs/REFERENCIA_COMPLETA.md)
- [Matriz de permisos verificada](docs/MATRIZ_PERMISOS.md)

## Compilar

1. Abrir esta carpeta en Android Studio.
2. Copiar `local.properties.example` como `local.properties` y completar la configuración privada.
3. Colocar `google-services.json` en `app/google-services.json`.
4. Ejecutar `./gradlew assembleDebug` o `.\gradlew.bat assembleDebug`.

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Probar sin tocar producción

Pruebas automáticas de reglas, permisos, operaciones completas, concurrencia, llaves y copia mensual:

```powershell
npm install
npm test
.\gradlew.bat --no-daemon testDebugUnitTest
```

Todas las pruebas de Firebase se ejecutan contra un emulador local aislado.

```powershell
.\tools\start-local-test.ps1 -ResetApp
```

Este comando abre un teléfono virtual y una copia local de Firebase con datos de prueba. Consulta los detalles en [Desarrollo local](docs/DESARROLLO_LOCAL.md).

La aplicación oficial conserva el paquete `com.ejemplo.registroguardias`. Android registra los datos
en Firestore y Apps Script actualiza Google Sheets de forma independiente. Los nombres escritos en
mayúsculas dentro del módulo de llaves se guardan en una lista exclusiva, sin agregarlos a Personal.
La versión mínima compatible es Android 7.0 (API 24).
