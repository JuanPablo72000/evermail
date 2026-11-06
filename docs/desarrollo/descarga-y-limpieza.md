# Descarga y limpieza de la sesión

Evermail prepara en segundo plano los cuerpos de los correos de la página de bandeja recibida. Cada página contiene hasta 50 mensajes. Las descargas avanzan una por una, del más reciente al más antiguo, sin marcar los mensajes como vistos. Al cargar otra página se incorporan más pendientes, con una cola acotada a 100 identificadores. Una actualización de bandeja sustituye los pendientes anteriores.

Abrir un mensaje tiene prioridad sobre iniciar otra precarga. Si ya se está descargando, la apertura aprovecha el cuerpo guardado al terminar. Una descarga activa puede retrasar la acción; no se interrumpe arbitrariamente una operación IMAP para adelantar otra. La conexión se reutiliza y se cierra tras 30 segundos sin uso; los errores la invalidan para que la siguiente operación establezca otra. Ante un fallo de precarga se detienen los pendientes, sin reintentos continuos; actualizar o cargar más vuelve a alimentar la cola.

Las imágenes externas se descargan al abrir el correo, exclusivamente por el proxy configurado. Hasta tres solicitudes comparten un grupo de trabajadores, separado de la descarga de cuerpos. Las imágenes rápidas pueden aparecer mientras otra sigue pendiente: se publican grupos de tres o los resultados disponibles después de 150 ms de espera. El documento se actualiza sin recargar todo el visor, manteniendo JavaScript deshabilitado y la política de recursos. Cambiar de correo, de cuenta o a texto impide aplicar resultados visuales fuera de contexto.

## Almacenamiento y cierre

La versión 1.0 limita el espacio total a **2 GB decimales (2.000.000.000 bytes)**, incluyendo aplicación, runtime, datos y espacio de operación. También mantiene un máximo de 100 cuerpos de bandeja. El límite de bytes puede hacer que se conserven menos.

La respuesta habitual a la falta de espacio es automática: se retiran cuerpos e imágenes incrustadas de correos de la caché, manteniendo encabezados, cursores y marcas locales de visto. Primero se eligen los adicionales a los 50 más recientes, dando preferencia a los menos utilizados. Si no basta, se retiran los más antiguos de esos 50. El correo abierto y las descargas con reserva activa quedan protegidos. Abrir un cuerpo retirado requiere descargarlo otra vez; si no hay conexión, puede no estar disponible.

No se borran correos de Gmail/Outlook, enviados, registros de envíos pendientes, cuentas ni claves para hacer espacio. Los archivos desconocidos se cuentan si están en el directorio de datos, pero no se eliminan. Si solo quedan datos protegidos o falta espacio para una limpieza segura, se informa de que no se puede continuar; nunca se simula un guardado correcto.

### Cómo se calcula y aplica el presupuesto

- En una instalación de `jpackage`, se mide la carpeta del ejecutable. Durante desarrollo o ejecución con `jlink`, se cuentan el runtime y las rutas de clases/módulos utilizados; no se toma todo el repositorio como aplicación instalada.
- Se cuenta el directorio de datos completo, incluidos SQLite y sus archivos auxiliares, y las ubicaciones identificables de bibliotecas extraídas de JavaFX, JNA y SQLite. Las rutas solapadas no se cuentan dos veces. Las cachés compartidas pueden producir una estimación conservadora; no se limpian archivos de otras aplicaciones.
- Se reservan **32 MiB de operación** para bibliotecas en carga, credenciales del sistema y sobrecarga de archivos. Además se reserva hasta el equivalente a **dos copias adicionales de SQLite**, necesario para poder efectuar journal y conversiones/compactaciones sin agotar el límite. Esto reduce deliberadamente el espacio disponible para correos; no es un límite fijo de 250 MiB.
- La capacidad de la base es, conservadoramente, `(2 GB − aplicación − otros archivos − reserva operativa) / 3`. También se comprueba el espacio libre del disco. Una limpieza sin reconstrucción de la base puede utilizar la reserva de una sola copia adicional para recuperar margen después de un aumento del tamaño de la aplicación.
- Antes de descargar un cuerpo se reserva una estimación de hasta 24 MiB para su representación cifrada y sus índices. La red no escribe archivos de correo temporales. Al guardar se recalcula la necesidad con el contenido real; las reservas se liberan también ante fallo o cancelación. Las escrituras grandes de bandeja y enviados anticipan su crecimiento.
- SQLite tiene un límite de páginas comprobado antes de escribir, usa journal DELETE y temporales SQL en memoria. Un bloqueo de archivo impide que dos procesos de Evermail escriban simultáneamente en esa base.
- `auto_vacuum=FULL` devuelve páginas libres al disco al confirmar la limpieza. Las bases anteriores se convierten una vez con VACUUM, conservando sus tablas y datos, y solo si cabe el espacio temporal necesario. Si una base preexistente o archivos externos ya ocupan demasiado, puede ser necesario liberar espacio antes de abrirla: no se realiza una conversión insegura ni se eliminan datos protegidos.

El presupuesto se aplica a las escrituras administradas por Evermail; no controla que otros programas llenen el disco o modifiquen sus carpetas. El instalador todavía no se ha generado ni validado. Antes de distribuirlo habrá que comprobar también el tamaño real del paquete instalado. Las imágenes externas actuales solo ocupan memoria durante la lectura, no una caché persistente en disco.

Cerrar la ventana cancela los trabajos, espera su terminación y elimina los mensajes de bandeja almacenados localmente, incluidos cuerpos, imágenes incrustadas, destinatarios y marcas de visto. SQLite devuelve páginas libres al disco al confirmar la transacción. Las imágenes externas no se guardan en disco. La cuenta, sus credenciales y claves, los enviados y los registros necesarios para resolver envíos pendientes se conservan. El cierre de sesión de la cuenta mantiene su comportamiento de eliminación de los datos de esa cuenta.

La limpieza no borra mensajes ni cambia el estado de lectura en Gmail/Outlook. Tras cerrar y abrir Evermail, los mensajes de la bandeja vuelven a aparecer sin visto local. Si el proceso se termina de forma forzada, la siguiente inicialización limpia la caché restante. Hasta ese próximo arranque puede permanecer en disco. Si falla la limpieza, Evermail informa del error.

Como consecuencia, después de reiniciar hace falta conexión para recuperar la bandeja y sus cuerpos. Durante una misma ejecución se puede seguir leyendo contenido ya descargado si se pierde la red.

## Validación

Las pruebas usan SQLite temporal y proveedores/proxy simulados: prioridad entre cuerpos, cancelación al salir, limpieza física, conservación de cuenta y envíos pendientes, temporizadores de conexión, imágenes por orden de finalización y actualización del documento WebView sin recarga. Ejecutar con Java 21: `gradlew.bat test assemble`.

Los tiempos reales de Gmail/Outlook y del proxy dependen de red, autenticación y contenido; no se establece una garantía de apertura en cinco segundos. No hay cambios de configuración ni variables nuevas para `.env`.

Referencias del mecanismo: [límites de páginas y auto-vacuum de SQLite](https://www.sqlite.org/pragma.html), [extracción de bibliotecas de SQLite JDBC](https://github.com/xerial/sqlite-jdbc/blob/master/USAGE.md), [caché nativa de JavaFX 21](https://github.com/openjdk/jfx/blob/jfx21/modules/javafx.graphics/src/main/java/com/sun/glass/utils/NativeLibLoader.java).
