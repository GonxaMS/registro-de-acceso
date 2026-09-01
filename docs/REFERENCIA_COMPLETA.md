# Referencia completa y estado del proyecto

Documento actualizado para la versión 3.9.15. Describe el comportamiento vigente, las decisiones
aceptadas y los riesgos pendientes. Firebase es la fuente de verdad; Google Sheets es una copia
operativa secundaria.

## Alcance

La app registra ingresos y salidas de personal, préstamos y devoluciones de llaves, correcciones
administrativas e historial inmutable. Funciona desde Android 7.0 (API 24) y usa el paquete
`com.ejemplo.registroguardias`.

## Roles y permisos

La definición detallada y verificada se mantiene en [Matriz de permisos](MATRIZ_PERMISOS.md).

| Acción | Administrativo | Normal | Bloqueado |
| --- | --- | --- | --- |
| Ver personal y llaves | Sí | Sí | No |
| Registrar ingreso y salida | Sí | Sí | No |
| Quitar ingreso o salida del día | Sí | Sí | No |
| Modificar horarios del día | Sí | Sí | No |
| Prestar y devolver llaves | Sí | Sí | No |
| Agregar, ocultar o retirar operarios | Sí | Sí | No |
| Agregar, ocultar o mostrar llaves | Sí | Sí | No |
| Corregir días anteriores | Sí | No | No |
| Administrar dispositivos | Sí | No | No |
| Consultar errores persistentes | Sí | No | No |

La amplitud del rol Normal es una decisión aceptada para la operación actual. El rol se determina por
UID de Firebase; el nombre escrito en la app no concede permisos.

## Flujo de personal

1. La app verifica el estado actual del operario dentro de una transacción.
2. Crea un movimiento correlativo `M000001` y actualiza `personal`.
3. `Ingreso` solo se permite cuando está Fuera; `Salida`, cuando está Dentro.
4. Quitar un movimiento crea `AnulacionIngreso` o `AnulacionSalida` con `anulaA`; no borra el original.
5. Las correcciones administrativas crean un movimiento nuevo con `reemplazaA`.

Si el día tiene ingreso y salida, debe quitarse primero la salida. Las correcciones históricas solo
están disponibles para Administrativo.

## Flujo de llaves

1. Una llave Disponible puede pasar a Prestada mediante un movimiento `Retiro`.
2. Una llave Prestada vuelve a Disponible mediante `Devolucion`.
3. La transacción actualiza `llaves` y crea `movimientosLlaves/L000001` conjuntamente.
4. Una llave prestada no puede ocultarse.
5. Se puede elegir Personal o escribir un nombre exclusivo del módulo de llaves.

Los nombres exclusivos se normalizan en mayúsculas, se guardan en `operariosLlaves` y no crean un
registro en `personal`.

## Firebase y datos

- Authentication usa sesiones anónimas para identificar cada instalación mediante UID.
- Firestore contiene estados actuales, movimientos inmutables, roles, contadores y errores.
- `meta/config` mantiene los siguientes IDs de operarios, movimientos y llaves.
- Las reglas validan autenticación, rol, estructura, referencias y transacciones.
- Un movimiento creado no puede actualizarse ni eliminarse.

Las colecciones y campos se detallan en [Datos y seguridad](DATOS_Y_SEGURIDAD.md).

## Google Sheets y Apps Script

Android no llama a Apps Script ni mantiene una cola secundaria. Apps Script consulta Firestore
periódicamente, reconstruye las pestañas mensuales y evita duplicados por `movimientoId`.

- Personal: `Registro Personal AÑO-M`.
- Llaves: `Registro Llaves AÑO-M`.
- Estado: `sincronizacion/sheets`.
- Fallos de copia: `erroresSincronizacion`.

Una falla de Sheets no revierte un movimiento confirmado en Firebase.

## Manejo de errores

Las pantallas de llaves conservan sus datos anteriores cuando falla una consulta y muestran
Reintentar. Si el mismo origen falla nuevamente después del reintento, se crea un documento en
`erroresApp`. Admin ve esos fallos junto con los de sincronización y puede marcarlos como resueltos.
Todas estas herramientas están reunidas en la opción `Administración` del menú principal. Los éxitos operativos se notifican con un aviso inferior temporal; solo las confirmaciones delicadas y los errores usan cuadros que requieren respuesta.
Los errores de la app no se resuelven automáticamente por una ejecución exitosa de Apps Script.

## Configuración y secretos

Archivos privados no versionados:

- `app/google-services.json`.
- `local.properties`, con `sdk.dir` y `sheets.web_url`.
- Propiedades privadas de Apps Script para Firebase e `ID_PLANILLA`.

GitHub Actions requiere `GOOGLE_SERVICES_JSON` y `SHEETS_WEB_URL`.

## Compilación y entrega

La versión se controla en `app/build.gradle.kts`. Cada APK aumenta `versionCode` y usa un
`versionName` visible. El archivo entregado se llama `RegistroAcceso-vX.Y.Z.apk`.

Comprobaciones actuales:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
firebase deploy --only firestore:rules --dry-run --project registro-guardias-408cb
```

La compilación ejecuta análisis Android, pero todavía no existen casos de prueba Java activos. Las
reglas deben validarse localmente antes de publicarse y nunca se debe probar contra producción con
datos ficticios.

## Problemas pendientes clasificados

### Alta

- Las APK actuales usan firma de desarrollo. Antes de una distribución definitiva debe configurarse
  una firma de producción estable; cambiar de firma después impide actualizar encima de instalaciones
  anteriores.
- El nombre del usuario es texto local editable. Puede utilizarse el nombre de otra persona y el UID
  del dispositivo no se guarda actualmente en cada movimiento.

### Media

- Los cambios de rol no cierran inmediatamente una app que ya estaba abierta. Firebase rechaza las
  operaciones, pero los datos previamente cargados pueden seguir visibles hasta reiniciar.
- `fecha` y `hora` proceden del reloj del teléfono. `creado` usa la hora oficial del servidor como
  respaldo, pero no valida ni reemplaza los valores operativos.
- No hay pruebas automáticas con casos reales; la validación funcional sigue siendo manual.
- Borrar los datos o reinstalar desde cero puede producir un UID anónimo nuevo y requerir autorización.

### Baja

- Las reglas de `meta/config` permiten cambios más amplios que el incremento habitual de contadores.
- Persisten advertencias de mantenimiento: textos no centralizados, algunos recursos sin usar,
  orientación vertical forzada y APIs antiguas.
- La configuración de compilación Java es antigua y genera avisos, aunque compila correctamente.

## Decisiones actuales

- Mantener los permisos amplios del rol Normal.
- Mantener fecha y hora operativas basadas en el teléfono.
- No endurecer por ahora los contadores internos.
- Posponer las pruebas automáticas.
- Continuar auditando antes de crear una versión final firmada.

## Prueba manual mínima por versión

1. Abrir con un dispositivo Administrativo, Normal y Bloqueado.
2. Registrar ingreso y salida; quitar primero salida y luego ingreso.
3. Prestar y devolver una llave con Personal y con un nombre exclusivo.
4. Agregar, ocultar y restaurar un operario y una llave.
5. Realizar una corrección histórica desde Admin.
6. Cambiar el rol de otro dispositivo y reiniciar la app para verificarlo.
7. Confirmar Firebase → Sheets y ausencia de duplicados.
8. Simular un fallo de carga, pulsar Reintentar y comprobar su aparición en Admin si persiste.

## Documentos relacionados

- [Arquitectura](ARQUITECTURA.md)
- [Guía de operación](OPERACION.md)
- [Google Sheets](GOOGLE_SHEETS.md)
- [Datos y seguridad](DATOS_Y_SEGURIDAD.md)
- [Instalación y despliegue](INSTALACION_Y_DESPLIEGUE.md)
- [Desarrollo local](DESARROLLO_LOCAL.md)
