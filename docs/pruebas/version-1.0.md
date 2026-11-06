# Verificación de almacenamiento y navegación — versión 1.0

Fecha: 4 de noviembre de 2026.

## Resultado

Con JDK 21 se ejecutó `gradlew.bat test assemble verifyMailRuntime --no-daemon`:

- **196 pruebas, cero fallos, cero errores y cero omitidas.**
- Compilación y ensamblado correctos.
- Arranque de comprobación modular de HTML/CSS y bibliotecas de apariencia de Windows correcto.
- `git diff --check` sin errores.
- No se ejecutaron tareas de `jpackage`, no se creó `.exe` ni instalador y no se modificó `.env`.

Gradle conserva avisos de funciones obsoletas para Gradle 9 y el compilador advierte
sobre archivos compilados implícitamente fuera del procesamiento de anotaciones.
No impidieron la compilación ni la ejecución de las pruebas.

## Casos incorporados

| Área | Evidencia |
| --- | --- |
| Presupuesto total | Aplicación, archivos de datos, WAL/auxiliares, reserva operativa y rutas solapadas; capacidad limitada también por espacio libre del disco. |
| Fallos de medición | Instalación mayor que el presupuesto y disco no consultable impiden continuar de forma conservadora. |
| Limpieza automática | Cuerpos antiguos se retiran bajo presión, incluso dentro de los 50 recientes cuando hace falta; se conservan encabezados y el correo abierto. |
| Datos protegidos | La limpieza conserva enviados, envíos pendientes y claves de cuenta. |
| Reservas y errores | Liberación de reservas al terminar sin guardar; escritura SQLite que excede el límite revierte sus cambios. |
| Espacio físico | El archivo de base de datos se reduce tras limpiar; el límite vuelve a aplicarse al reabrir. |
| Cambio de tamaño de aplicación | Un aumento del tamaño contado provoca limpieza de caché antes de otra escritura. |
| Bases anteriores | Conversión de una base sin auto-vacuum conservando su contenido. |
| Exclusión de procesos | Un segundo proveedor de conexión no puede abrir la misma base para escribir. |
| Anterior/Siguiente | Orden de la bandeja, extremos sin vuelta circular, cruce de páginas ya cargadas y ausencia de paginación automática. |
| Cancelación del lector | Cambios rápidos descartan cuerpos e imágenes tardíos; cambiar de sesión invalida la navegación y se conserva el borrador durante la lectura. |
| Interfaz JavaFX | Los botones actúan sobre el lector y se deshabilitan en los extremos, incluso durante la carga. |

Las pruebas del presupuesto usan archivos reales y SQLite temporal con límites
pequeños inyectados. Esto permite alcanzar los límites sin llenar el disco con 2 GB
ni consultar cuentas reales. No certifican el tamaño de una futura instalación ni
sustituyen una prueba manual en el equipo de distribución. La recuperación de
transacciones se verifica mediante rollback y reapertura; no se simuló un corte de
energía del sistema operativo.

## Revisión de legibilidad

El cálculo de espacio, la aplicación de límites SQLite y la elección de cuerpos
prescindibles están separados en `StorageBudget`, `SqliteStorage` e
`InboxCachePolicy`. Las transacciones y reservas se coordinan en
`TransactionManager`. Se unificó la retención de cuerpos del servicio con esa
política y se eliminó una comprobación de plazo duplicada.

En `MailPresenter`, la recepción del cuerpo y la carga de imágenes se extrajeron
del bloque de callbacks anidados a métodos con responsabilidades separadas.
Anterior/Siguiente reutiliza el flujo de apertura y sus controles de sesión.
También se corrigieron comentarios que describían como futura una integración ya
existente. No se afirma haber auditado cada clase ni implementado otras funciones.

La política de uso y sus límites se describe en
[Descarga y limpieza de la sesión](../desarrollo/descarga-y-limpieza.md).

## Revisión adicional de interfaz, lectura y reautenticación

El 4 de noviembre se unificaron el relleno y el ancho de la barra lateral entre
bandeja y lector. El lector tenía una regla CSS propia que subía el contenido y
una proporción de columna diferente. Las capturas de ambas vistas a 1920×1080
muestran ahora la misma ubicación del logo, navegación, cuenta y cierre de sesión.

Al reautorizar la misma cuenta, el presentador vuelve a la bandeja y solicita una
actualización directamente, conservando la lista y el borrador. El acceso a otra
cuenta mantiene la carga de su caché antes de consultar al proveedor. Los errores
anteriores de bandeja se limpian al iniciar la autorización y no tienen prioridad
sobre un error de sesión nuevo.

Las pruebas añadidas cubren reautenticación desde el lector, rechazo de respuestas
de la sesión anterior, recuperación tras fallo de la actualización y renovación
de credenciales previamente rechazadas sin cerrar sesión ni eliminar correo local.
El recorrido del botón «Volver a autorizar» se verifica también en JavaFX con un
backend simulado. No se reprodujo el incidente exacto contra Google/Microsoft;
estos ajustes y pruebas no identifican por sí solos la causa de aquel episodio.

También se elimina una medición duplicada del disco antes de cada escritura,
conservando el presupuesto y la limpieza automática. Véase el análisis y sus
límites en [Tiempos de lectura](tiempos-lectura.md#revisión-del-4-de-noviembre-de-2026).

Validación final con Temurin 21.0.13:

- `gradlew.bat test assemble verifyMailRuntime --no-daemon`: correcto.
- **201 pruebas en 39 clases, cero fallos, errores u omitidas.**
- Capturas JavaFX revisadas en `build/ui-previews/main-1920.png` y
  `build/ui-previews/reader-1920.png`.
- `git diff --check`: correcto; `.env` intacto, sin ejecutable ni instalador.

Los intentos iniciales con Zulu 21.0.11, que incluye JavaFX 21.0.11 mientras el
proyecto declara JavaFX 21.0.6, terminaron con un fallo nativo del ejecutor
(`-1073741819`), también al probar renderizado por software. Una ejecución visual
aislada se interrumpió para cambiar de runtime. La validación final completa pasó
con Temurin y sin forzar renderizado por software. No se cambió la configuración
persistente del equipo ni se atribuye a ese fallo el incidente OAuth del usuario.
