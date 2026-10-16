# Descarga y limpieza de la sesión

Evermail prepara en segundo plano los cuerpos de los correos de la página de bandeja recibida. Cada página contiene hasta 50 mensajes. Las descargas avanzan una por una, del más reciente al más antiguo, sin marcar los mensajes como vistos. Al cargar otra página se incorporan más pendientes, con una cola acotada a 100 identificadores. Una actualización de bandeja sustituye los pendientes anteriores.

Abrir un mensaje tiene prioridad sobre iniciar otra precarga. Si ya se está descargando, la apertura aprovecha el cuerpo guardado al terminar. Una descarga activa puede retrasar la acción; no se interrumpe arbitrariamente una operación IMAP para adelantar otra. La conexión se reutiliza y se cierra tras 30 segundos sin uso; los errores la invalidan para que la siguiente operación establezca otra. Ante un fallo de precarga se detienen los pendientes, sin reintentos continuos; actualizar o cargar más vuelve a alimentar la cola.

Las imágenes externas se descargan al abrir el correo, exclusivamente por el proxy configurado. Hasta tres solicitudes comparten un grupo de trabajadores, separado de la descarga de cuerpos. Las imágenes rápidas pueden aparecer mientras otra sigue pendiente: se publican grupos de tres o los resultados disponibles después de 150 ms de espera. El documento se actualiza sin recargar todo el visor, manteniendo JavaScript deshabilitado y la política de recursos. Cambiar de correo, de cuenta o a texto impide aplicar resultados visuales fuera de contexto.

## Almacenamiento y cierre

Durante el uso se conservan hasta 100 cuerpos descargados por el servicio. Al retirar uno, permanecen su encabezado y su marca local de visto; abrirlo después puede requerir otra descarga. Este límite de cantidad no sustituye un presupuesto físico total: el administrador de los 2 GB incluyendo aplicación, datos y espacio temporal sigue pendiente.

Cerrar la ventana cancela los trabajos, espera su terminación y elimina los mensajes de bandeja almacenados localmente, incluidos cuerpos, imágenes incrustadas, destinatarios y marcas de visto. SQLite se compacta para recuperar espacio. Las imágenes externas no se guardan en disco. La cuenta, sus credenciales y claves, los enviados y los registros necesarios para resolver envíos pendientes se conservan. El cierre de sesión de la cuenta mantiene su comportamiento de eliminación de los datos de esa cuenta.

La limpieza no borra mensajes ni cambia el estado de lectura en Gmail/Outlook. Tras cerrar y abrir Evermail, los mensajes de la bandeja vuelven a aparecer sin visto local. Si el proceso se termina de forma forzada, la siguiente inicialización limpia la caché restante. Hasta ese próximo arranque puede permanecer en disco. Si falla la limpieza, Evermail informa del error.

Como consecuencia, después de reiniciar hace falta conexión para recuperar la bandeja y sus cuerpos. Durante una misma ejecución se puede seguir leyendo contenido ya descargado si se pierde la red.

## Validación

Las pruebas usan SQLite temporal y proveedores/proxy simulados: prioridad entre cuerpos, cancelación al salir, limpieza física, conservación de cuenta y envíos pendientes, temporizadores de conexión, imágenes por orden de finalización y actualización del documento WebView sin recarga. Ejecutar con Java 21: `gradlew.bat test assemble`.

Los tiempos reales de Gmail/Outlook y del proxy dependen de red, autenticación y contenido; no se establece una garantía de apertura en cinco segundos. No hay cambios de configuración ni variables nuevas para `.env`.
