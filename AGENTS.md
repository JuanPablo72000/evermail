# Preferencias del usuario

- No modificar el archivo `.env` original del proyecto. El usuario administra ese archivo personalmente.
- Documentar las variables nuevas y los cambios de configuración necesarios únicamente en `.env.example`, e indicar al usuario qué debe completar en su `.env`.
- No crear, sobrescribir ni revertir el `.env` como parte de tareas de implementación, pruebas o configuración sin una autorización explícita posterior del usuario.


# Organización de documentación

- Todas las instrucciones, preferencias y notas de contexto para agentes se mantienen en este AGENTS.md; no crear archivos de memoria/contexto en docs.
- docs contiene únicamente documentación dirigida al usuario: guías, requisitos, diagramas y resultados de pruebas.
- Conservar los cambios previos del usuario. No editar .env, tampoco durante pruebas.

# Propuesta para futuras actualizaciones: caché en la nube

- Idea del usuario registrada el 12 de octubre de 2026; pendiente de diseño e implementación. Esta nota no autoriza implementarla ahora.
- Permitir conectar Evermail a Google Drive o OneDrive para almacenar una caché de correo en un único archivo cifrado.
- Al iniciar sesión, descargar en segundo plano los 50 correos más recientes, uno por uno, incluyendo cuerpo e imágenes, sin esperar a que el usuario haga clic en cada correo y sin bloquear la interfaz.
- Mantener una ventana de los 50 correos más recientes disponibles: incorporar los nuevos y retirar los más antiguos de la caché y del archivo cifrado. Retirarlos de la caché no significa eliminar los originales del buzón del proveedor.
- Objetivo: reducir la espera al abrir correos mediante descarga anticipada y conservación de su contenido e imágenes, con respaldo de esa caché en Drive o OneDrive.
- Pendiente de definir en el diseño: relación entre caché local y copia en la nube, gestión y recuperación de claves, actualización del archivo único y sincronización entre dispositivos. La disponibilidad de los 50 correos depende de completar la sincronización y de la conectividad.

# Requisitos propuestos para la caché local y el almacenamiento

- El usuario fija un límite total de 2 GB para Evermail: aplicación instalada y datos locales, incluidos correos, imágenes, base de datos, índices y archivos auxiliares o temporales. No interpretar los 2 GB como presupuesto exclusivo de correos. Pendiente de implementación; esta nota registra el requisito, no autoriza cambios funcionales por sí sola.
- Propuesta de precarga: descargar en segundo plano los 50 correos más recientes, del más nuevo al más antiguo, sin requerir un clic. Priorizar la apertura solicitada por el usuario y aprovechar la caché cuando el contenido ya esté disponible.
- Al cargar otros 50 correos en la bandeja, precargarlos progresivamente mientras haya presupuesto; retirar contenido de la caché al alcanzar el límite, sin borrar mensajes del proveedor.
- Al terminar el uso de la aplicación, conservar como objetivo los 50 más recientes y retirar los adicionales. Distinguir este cierre de la aplicación del cierre de sesión de la cuenta, que actualmente elimina sus datos locales.
- Cuando lleguen correos nuevos, descargar los necesarios para actualizar los 50 más recientes antes de retirar los antiguos, siempre dentro del límite total. El diseño debe reservar espacio para esa sustitución y gestionar falta de espacio, descargas incompletas y cierres inesperados sin prometer 50 completos antes de finalizar la sincronización.
- El presupuesto de caché se calculará descontando del límite total el tamaño real de la aplicación y el espacio de operación necesario. La propuesta anterior del asistente de 250 MiB no es un límite aprobado por el usuario.

## Opciones de implementación de la precarga (pendientes)

Esta propuesta fue solicitada para futuras sesiones; no está implementada. La caché local es la primera etapa propuesta; Drive/OneDrive es una ampliación independiente, no un requisito para acelerar la lectura.

- Crear un planificador de precarga por cuenta/sesión, con cola acotada y prioridad para el correo seleccionado. Alimentarlo tras actualizar la bandeja y al cargar páginas adicionales. Deduplicar trabajos por cuenta, generación del buzón y UID; si el usuario abre un correo en descarga, compartir/promover el trabajo en curso en lugar de descargarlo otra vez.
- Separar la obtención de cuerpos de la obtención de imágenes. Un trabajador de cuerpos puede recorrer secuencialmente los pendientes; un ejecutor independiente y acotado resuelve imágenes. Una imagen lenta no debe retrasar el cuerpo del siguiente correo ni ocupar el trabajador principal del backend esperando todos los resultados.
- Revisar antes la coordinación existente: `ApplicationCoordinator` tiene un solo trabajador y `AccountCoordinator.exclusive` mantiene un bloqueo por cuenta durante la operación. Añadir un hilo sin cambiar la planificación no evita esas esperas. Opción inicial: operaciones cortas por correo y prioridad entre trabajos. Alternativa a evaluar: un ejecutor de precarga separado con arbitraje explícito del acceso IMAP y de la persistencia; no retirar el aislamiento de sesión ni compartir objetos Jakarta Mail entre hilos sin coordinación.
- Incorporar un gestor de sesión IMAP reutilizable por cuenta, con un único propietario de la conexión. Reconectar ante desconexión, suspensión o fallo de autenticación; cerrar al desconectar la cuenta o la aplicación. Adaptar los cierres programados de `MailSessionProvider`: el temporizador de una operación anterior nunca debe cerrar una conexión prestada a otra. Priorizar entre descargas; interrumpir una descarga activa requiere cancelación segura y podría exigir reconectar.
- La precarga reutilizaría `InboxService.loadContent` y el cifrado de `MailRepository`, pero no `CoordinatedPresentationBackend.content` sin separar su efecto de `markRead`. Precargar no marca leído; conservar IMAP PEEK/READ_ONLY.
- Ampliar la caché para guardar también imágenes externas y fondos validados, como datos binarios cifrados con metadatos/referencias por cuenta, en SQLite o en archivos cifrados administrados. Evaluar ambas opciones; evitar Base64 persistente cuando sea posible y no guardar archivos temporales descifrados. Mantener sanitización, CSP y descarga de recursos del mensaje exclusivamente mediante el proxy.
- Registrar por separado cuerpo disponible, imágenes pendientes, imágenes completas y fallos parciales. Conservar el cuerpo aunque fallen las imágenes; aplicar reintentos acotados, sin bucles inmediatos. La precarga de imágenes externas puede activar URLs de seguimiento antes de que se abra el correo; considerar este efecto al diseñar la opción de precarga.
- Entregar imágenes por orden de finalización y publicar pequeños grupos de actualizaciones en JavaFX. Actualmente `MailPresentationService` recoge futuros por orden de URL y el presentador aplica una única presentación al terminar. Adaptar ambos, respetar modo texto, selección, cancelación y versión de sesión; no recargar todo el visor por cada imagen si provoca parpadeo o pérdida de desplazamiento.
- Mantener un límite compartido de peticiones al proxy entre lectura y precarga. El usuario indica cuatro peticiones simultáneas en Azure; el cliente actual usa tres por carga. Verificar si el límite de Azure es por instancia y su configuración antes de cambiar concurrencia; no crear un grupo nuevo de descargas por cada correo precargado.
- Implementar un administrador de presupuesto total de 2 GB que incluya aplicación, datos, imágenes, índices, SQLite/WAL y temporales cifrados. Reservar bytes antes de descargar y antes de reemplazar contenido; comprobar también espacio libre del disco. Eliminar filas en SQLite no reduce necesariamente el tamaño físico del archivo: prever reutilización de páginas y mantenimiento sin exceder el presupuesto durante compactación.
- Proteger los 50 correos más recientes y temporalmente el abierto; entre los adicionales, proponer eliminación por menor uso reciente. Conservar encabezados y cursores de bandeja al retirar cuerpos/imágenes: no usar `MailRepository.remove`, que elimina el mensaje local. No eliminar originales del proveedor ni registros de envío pendientes como limpieza de caché.
- Persistir progreso y recuperar descargas incompletas. Realizar limpieza gradual durante el uso, al cerrar y en el siguiente arranque tras un cierre inesperado. Cerrar la ventana conserva la cuenta y los 50 recientes; `AuthService.logout` elimina sus datos y claves. Antes de borrar claves, cancelar y esperar escritores de precarga para impedir que reaparezcan datos de una cuenta desconectada.
- Verificar con pruebas de prioridad, deduplicación, aislamiento entre cuentas, cancelación, modo offline, UIDVALIDITY, fallos de imágenes, límite físico de almacenamiento y cierre inesperado. Medir por separado conexión IMAP, descarga de cuerpo, preparación, primer contenido visible y última imagen; distinguir primer arranque sin caché de aperturas posteriores. Usar Java 21 y las verificaciones habituales del proyecto sin modificar `.env`.

## Propuesta adicional: fotos de cuenta y remitentes

- Solicitada como investigación para futuras sesiones, sin implementación todavía. Actualmente `MailCell` y `MailWindow` muestran iniciales en etiquetas con estilo `avatar`; los FXML de fila, lector y barra lateral no cargan fotos. `OidcTokenValidator` conserva nombre y correo, pero no la foto.
- Distinguir foto de la cuenta autenticada de foto del remitente. El flujo IMAP actual no obtiene avatares; conocer una dirección de correo no garantiza acceso a la foto de su propietario.
- Google, cuenta propia: evaluar el claim opcional `picture` del ID token validado o UserInfo. `OAuthProvider.GOOGLE` ya solicita `openid email profile`; extender el modelo de identidad/cuenta y persistencia si se incorpora la foto. Admitir ausencia de imagen. Fuente: https://developers.google.com/identity/openid-connect/reference
- Google, remitentes: People API puede buscar contactos del usuario por correo y devolver `photos`, con permiso adicional `contacts.readonly`. La búsqueda es por prefijos: comprobar coincidencia exacta de dirección antes de asignar una imagen. No es un directorio público de cualquier dirección Gmail. Fuente: https://developers.google.com/people/api/rest/v1/people/searchContacts
- Microsoft: Graph permite obtener la foto propia con `/me/photo/$value` y fotos de contactos con `/me/contacts/{id}/photo/$value`; permisos según tipo de cuenta/recurso (por ejemplo `User.Read` para usuario personal y `Contacts.Read` para contactos). Consultar también restricciones de directorio empresarial. Los permisos/tokens actuales de Outlook IMAP/SMTP no deben asumirse válidos para Graph: diseñar consentimiento y adquisición de token para el recurso Graph por separado. Fuente: https://learn.microsoft.com/en-us/graph/api/profilephoto-get?view=graph-rest-1.0
- Proponer un servicio de avatares asíncrono con caché por cuenta e identidad/dirección, deduplicación, caducidad y caché temporal de ausencias para evitar consultas repetidas. Validar raster, tamaño y dimensiones; guardar miniaturas cifradas dentro de los 2 GB. Descargar desde el backend y mostrar bytes con ImageView circular, nunca bloquear la apertura del correo. No reenviar tokens de Graph/Google al proxy ni a URLs arbitrarias.
- Cuando no haya foto o autorización, mantener iniciales; como mejora visual opcional, usar colores estables por remitente. No prometer la misma cobertura de imágenes que Gmail ni consultar servicios externos de avatares automáticamente sin definir esa integración.

# Prioridad para el futuro instalador: desinstalación sencilla

- El usuario prioriza un desinstalador tradicional y fácil de encontrar, accesible como «Desinstalar Evermail» y desde las aplicaciones instaladas de Windows. Pendiente de implementación.
- Ofrecer dos opciones claras: «Desinstalar solo Evermail y conservar mis datos locales» y «Desinstalar Evermail y eliminar mis datos locales». Conservar datos por defecto; la eliminación requiere la elección explícita de la segunda opción.
- La primera opción elimina los archivos de la aplicación y sus accesos directos, pero conserva correos, imágenes, configuración y material de claves necesario para recuperar la caché cifrada al reinstalar bajo el mismo usuario de Windows. No llamar al cierre de sesión destructivo ni borrar claves dejando cuerpos cifrados irrecuperables. Definir por separado el tratamiento de tokens OAuth y si se requiere autenticación al reinstalar.
- La segunda opción elimina además los datos locales administrados por Evermail y sus claves/credenciales asociadas. Mostrar claramente el alcance: no borrar correos originales de Gmail/Outlook ni futuras copias de Drive/OneDrive como efecto de desinstalar localmente.
- Separar el directorio de instalación del directorio de datos del usuario para que desinstalar o actualizar binarios no borre la caché. El cierre previo a desinstalar debe detener y esperar trabajos de fondo y cerrar SQLite antes de limpiar; no anunciar éxito si quedan archivos pendientes o bloqueados.
- Evaluar el soporte de esta elección al seleccionar empaquetador: `jpackage` con runtime de `jlink` es una base propuesta, y un instalador personalizable como Inno Setup es una alternativa a evaluar para el diálogo y la limpieza opcional. No hay herramienta elegida definitivamente.
- Las actualizaciones no deben ejecutar la limpieza opcional de datos. En desinstalación silenciosa, conservarlos salvo una opción explícita de eliminación. Verificar reinstalación con caché conservada y descifrable, eliminación completa de datos propios y preservación de archivos ajenos. No tocar el `.env` original del proyecto.

# Contexto de implementación consolidado

## Estado vigente del lector y validación

- El lector entrega `prepareWithoutImages` primero y pide `PresentationBackend.images` después. El slot IMAGES se cancela al cambiar de correo/sesión; sus fallos conservan el cuerpo y su llegada respeta el modo texto. MailViewState.Reader.imagesLoading diferencia espera de error.
- El proxy resuelve hasta tres imágenes simultáneas con un límite de 24 URLs deduplicadas y memoria agregada acotada. Mantener la sanitización final y CSP; no entregar URLs de origen al WebView.
- IMAP precarga CONTENT_INFO/ENVELOPE y usa bloques de 128 KiB; HybridMime obtiene Content-ID mediante MimePart para aprovechar BODYSTRUCTURE. Sigue abriendo una conexión por descarga. El objetivo de 5 s aún no está garantizado: los resultados dependen de conexión/autorización y la muestra real incluyó un caso lento.

- El proxy de Azure está integrado; la configuración se documenta en `.env.example` y solo el usuario edita `.env`.
- `HtmlMail` limpia HTML con jsoup y `MailCss` analiza CSS con ph-css. Conservar únicamente propiedades, selectores y media queries permitidos. No habilitar JavaScript ni conexiones directas desde WebView.
- Las imágenes y los fondos comparten validación raster, deduplicación y presupuesto. Las referencias de fondos pendientes usan `evermail-remote:` y se resuelven solo mediante el cliente del proxy.
- El sobre cifrado guarda `imageReferencesVersion=2` para distinguir el HTML con estilos conservados del generado por filtros anteriores. La recuperación mantiene la copia legible cuando falla la red.
- Mantener el código de red y preparación fuera del hilo JavaFX. La cascada CSS y las reglas de ancho se aplican dentro del visor aislado, sin posicionamiento absoluto/fijo ni recursos activos.
- Validar con Java 21: `gradlew.bat test assemble`. `HtmlMailCssTest` verifica CSS y recursos; `MailHtmlLayoutTest` verifica cascada, adaptación y ausencia de solicitudes directas. Sus capturas están en `build/ui-previews/mail-html-900.png` y `mail-html-360.png`.
- Después de cambiar dependencias o `module-info.java`, ejecutar también `gradlew.bat verifyMailRuntime` (incluido en `check`). Este arranque modular sin cuentas detecta módulos no resueltos que JUnit puede ocultar. Mantener `requires org.slf4j` y su dependencia explícita: ph-css/ph-commons necesitan LoggerFactory al inicializarse.
- `slf4j-nop` es el proveedor explícito de registro: mantiene el comportamiento silencioso previo sin el aviso de proveedor ausente. No equivale a corregir excepciones. El NullPointerException aislado de Thread-1 no se reprodujo el 11 de octubre; no hay causa confirmada.
- `measureMailReads` mide hasta tres correos sin cuerpo en caché y su reapertura, guarda los cuerpos cifrados y no los marca como leídos. Los resultados y límites están en `docs/pruebas/tiempos-lectura.md`; no confundir tiempos del backend con renderizado completo.
- Las notas históricas que siguen se conservan por trazabilidad; este estado vigente y las instrucciones posteriores del usuario tienen prioridad.

Las siguientes notas se trasladaron completas desde docs/notas-tecnicas. Describen etapas sucesivas; las menciones a integración futura, ausencia de proxy y límites anteriores son históricas y no deben interpretarse como restricciones vigentes. El código y las instrucciones actuales del usuario determinan el estado vigente.


## Nota trasladada: application-coordination.md

# Coordinación de aplicación y sesión

El paquete `application` prepara la integración sin crear pantallas, controladores ni FXML. `App` conserva su comportamiento actual; esta etapa todavía no conecta el coordinador al arranque visual.

## Responsabilidades

- `ApplicationCoordinator`: crea el backend de forma diferida, restaura la sesión, coordina acceso/cierre de sesión y ejecuta operaciones de cuenta.
- `BackendAccess`: adapta los servicios existentes de `BackendContext`. Permite sustituirlos por dobles de prueba sin OAuth, SQLite ni almacén de claves real.
- `SessionSnapshot`: estado inmutable con cuenta y versión de sesión; distingue inicio, acceso requerido, autorización, sesión disponible, modo sin conexión, reautorización, cierre y fallo recuperable mediante un nuevo arranque.
- `OperationHandle`: resultado asíncrono, cancelación y versión de sesión.
- `OperationResult` y `OperationError`: resultado o mensaje seguro con el código de error original. Los mensajes internos de excepciones no se muestran al usuario.

## Ejecución y cancelación

Un trabajador ejecuta secuencialmente las operaciones. El límite predeterminado es de 32 operaciones pendientes, incluida la activa. Se reserva una plaza adicional para la transición de sesión, de modo que cerrar sesión siga siendo posible con la cola llena. No hay reintentos automáticos.

`submit(budget, operation)` entrega los servicios, la cuenta capturada y un `Deadline` creado al comenzar la ejecución. La operación debe pasar ese mismo plazo a los servicios. Las fachadas JavaFX existentes también crean sus plazos dentro de `Task.call()`; `presentationTask` conserva el plazo compartido suministrado por su consumidor.

Cancelar solicita la interrupción y señala el token de cancelación; no equivale a deshacer una acción ya realizada. El resultado se completa cuando el trabajador deja de utilizar sus recursos. Una llamada externa que no responda inmediatamente a la interrupción puede retrasar la finalización.

Al iniciar una transición de sesión se invalidan las operaciones anteriores. Aunque una llamada termine tarde, su resultado no restaura una sesión ni entrega contenido de la cuenta anterior. La cancelación individual de un cierre de sesión no se ofrece porque puede haber empezado a borrar los datos locales.

`closeAsync()` invalida la sesión, cancela tareas pendientes y espera en segundo plano a que termine la operación activa antes de cerrar el backend. Es idempotente y comunica también los fallos de cierre. El futuro punto de salida de la aplicación debe esperar su finalización de forma asíncrona antes de terminar el proceso; no debe bloquear el hilo JavaFX.

## Integración posterior

Los resultados no se publican automáticamente en JavaFX. La futura capa de presentación deberá trasladarlos al hilo de interfaz y volver a comparar `handle.sessionVersion()` con `coordinator.session().version()` al aplicarlos; la sesión puede haber cambiado mientras el evento esperaba en la cola visual. Las continuaciones deben ser breves y no bloquear esperando otras operaciones del mismo coordinador.

La reautorización conserva la cuenta para permitir lectura de caché. Un fallo al cerrar sesión invalida la cuenta visible y exige ejecutar de nuevo el arranque y su recuperación, en lugar de reutilizar una cuenta parcialmente eliminada.

Un `OperationResult<SendResult>` exitoso solo significa que el servicio devolvió un resultado: la presentación debe interpretar `SendResult.state`, incluidos `UNKNOWN` y `ACCEPTED` con guardado pendiente. Nunca debe deducir que el mensaje fue entregado por el simple hecho de completar la tarea.

## Pruebas

Las pruebas del paquete `application` son unitarias: utilizan un backend simulado, sincronización con señales y un ejecutor privado por prueba. No inicializan JavaFX, no usan cuentas reales y no reemplazan las pruebas existentes de servicios y persistencia. Se verifica aislamiento de sesiones, cancelación, cola llena, recuperación de errores, envío incierto y cierre seguro de recursos.


## Nota trasladada: presentation-navigation.md

# Presentación y navegación sin FXML

Esta etapa añade `presentation` y `navigation`. No cambia `App`, las pantallas aprobadas ni los FXML existentes. La integración visual queda para la siguiente etapa.

## Puntos de entrada

- `NavigationRules` decide las rutas de carga, acceso, bandeja, lector, recuperación y cierre a partir de la sesión. Las rutas privadas requieren una cuenta. Una cuenta sin conexión o pendiente de reautorización puede seguir consultando su caché.
- `MailPresenter` recibe las acciones de usuario, mantiene el estado y publica `MailViewState` inmutables. Bandeja y redacción comparten la vista principal, como en Figma.
- `CoordinatedPresentationBackend` conecta las operaciones tipadas con `ApplicationCoordinator` y los servicios existentes. `PresentationBackend` permite probar la presentación sin infraestructura real.
- `ComposeDraft` mantiene Para, CC, CCO, Asunto y Mensaje. De se obtiene exclusivamente de la cuenta activa. Las direcciones simples se separan por coma o punto y coma; los nombres de destinatario con sintaxis compleja quedan fuera de esta entrada. La validación final sigue en el backend.

## Contrato de uso

El futuro controlador construirá un coordinador, su adaptador y un presentador. Debe invocar las acciones del presentador y sus notificaciones en un mismo ejecutor serial; en JavaFX, las acciones estarán en el hilo de aplicación y el dispatcher será `Platform::runLater`. El listener debe ser breve, no lanzar excepciones ni iniciar acciones reentrantes durante el renderizado. La primera vista puede leer `state()` y luego llamar a `start()`.

Las operaciones asíncronas se validan de nuevo al procesar la notificación: se comprueban la versión de sesión y el número de solicitud por operación. Esto descarta tanto un correo anterior que termina tarde como eventos pendientes de una cuenta que ya cerró sesión. El contenido se limpia al cerrar sesión o la aplicación.

## Flujos preparados

- Arranque: carga → acceso, bandeja o recuperación; `start()` permite reintentar un arranque fallido.
- Acceso: Google/Microsoft, estado de autorización y cancelación. La reautorización de la misma cuenta conserva el borrador; cambiar de cuenta lo limpia.
- Bandeja: muestra caché primero y después actualiza si la sesión está lista. Un fallo de red conserva lo visible y marca la caché como antigua. Cargar más agrega los resultados, elimina duplicados por ID y conserva el cursor ante errores. El tamaño de página de 50 lo impone el servicio existente.
- Lectura: encabezado primero y contenido después. Preparación de HTML seguro fuera del hilo de interfaz, alternativa de texto, reintento y retorno a la misma lista. Cambiar de correo o volver invalida solicitudes anteriores. El adaptador marca el mensaje como leído cuando la preparación termina. El renderizador JavaFX seguirá necesitando los controles de seguridad y de tiempo del lector existente; esta etapa no reemplaza su WebView.
- Redacción: bloqueo durante el envío y confirmación para descartar. Un fallo previo de conexión permite reintentar con la misma identidad de envío. Un rechazo confirmado permite un nuevo intento explícito. La entrega registrada limpia el borrador; un resultado incierto o aceptado con guardado pendiente conserva el texto y bloquea reenviar o editar hasta que el usuario lo descarte conscientemente. No hay reenvíos automáticos.
- Cierre de sesión: requiere `requestLogout()` y `confirmLogout()`; cancelar la confirmación conserva los datos. El cierre de la aplicación devuelve el resultado de `closeAsync()` para que la futura integración espere asíncronamente y gestione errores de cierre.

Las funciones futuras del diseño (notas, calendario, enviados, borradores persistentes, búsqueda, adjuntos, anterior/siguiente, responder, reenviar, eliminar y archivar) no se implementan ni se eliminan. Sus controles podrán mantenerse sin acción al crear los FXML. Los avisos y confirmaciones son estados de presentación; no se ha añadido ni modificado contenido en Figma.

## Pruebas

Las pruebas de navegación son puras. Las del presentador usan una cola de eventos controlada para reproducir respuestas tardías y cambios de sesión sin esperas artificiales, JavaFX ni cuentas reales. Las del adaptador usan el coordinador real con servicios sustituidos y verifican selección de cuenta, conversión del formulario y preparación de contenido. Complementan las pruebas existentes de servicios, persistencia y lector.


## Nota trasladada: html-reading.md

# Lectura híbrida de Evermail

La primera versión mantiene cuatro funciones: acceso/cierre de sesión, bandeja de entrada, lectura de correos y envío. La lectura admite HTML y texto; la composición y SMTP siguen enviando exclusivamente texto plano UTF-8.

## Recorrido implementado

`MailSessionProvider → HybridMime → RemoteMailContent → MailRepository → MailContent → MailPresentationService → MailReaderPane`.

- `multipart/alternative` conserva las representaciones HTML y texto separadas, sin duplicarlas en pantalla. Si no hay texto, se obtiene una alternativa legible.
- `multipart/related` identifica la raíz y resuelve únicamente imágenes CID referenciadas. Se omiten los adjuntos ordinarios y los recursos no utilizados.
- `HtmlMail` utiliza [jsoup](https://jsoup.org/news/release-1.22.2) para analizar y reconstruir un subconjunto permitido de HTML y estilos en línea. Conserva párrafos, tablas, colores y formato básico; no promete reproducción idéntica de todas las plantillas de Gmail/Outlook.
- Scripts, formularios, iframes, SVG, hojas de estilo externas, CSS con recursos y navegación directa no llegan al visor. Los enlaces HTTP/HTTPS quedan inertes hasta un clic, que se entrega al manejador de la aplicación; no se abren dentro del mensaje.
- PNG, JPEG y GIF incrustados se validan y se convierten a referencias de datos en memoria. No se crean archivos descifrados. Las imágenes externas HTTPS se conservan como referencias inertes y se descargan mediante el proxy de Azure cuando está configurado; véase [configuración y límites](docs/desarrollo/proxy-imagenes.md). El visor nunca descarga directamente desde el origen.

## Caché y migración

La versión SQLite pasa de 1 a 2 dentro de una transacción. Añade `mail.body_format`; no elimina ni reescribe cuerpos existentes. El formato 0 conserva el texto cifrado anterior y el contexto criptográfico `body`. El formato 1 guarda un sobre JSON cifrado con texto, HTML seguro e indicador de imágenes bloqueadas, bajo el contexto autenticado `body:hybrid-v1`.

El texto guardado se puede leer sin red, incluso en cuentas `REAUTH_REQUIRED` que conservan su clave local. Con el proxy configurado, las imágenes externas se solicitan al abrir el mensaje. Los cuerpos anteriores que habían eliminado sus referencias intentan recuperar el HTML del proveedor; un fallo conserva la copia legible. La expiración de un token de acceso no equivale siempre a pedir acceso de nuevo: el backend puede renovarlo; la reautorización es necesaria cuando ya no puede hacerlo.

Los cuerpos anteriores solo en texto se identifican con `legacyTextOnly`. `reloadContent` y el botón «Recuperar formato» permiten recuperarlos explícitamente del proveedor, sin bloquear la consulta offline. Un fallo de descarga genérico conserva la copia anterior. El caso remoto `MAIL_NOT_FOUND` conserva la reconciliación existente, que elimina el mensaje de la caché.

El cierre de sesión elimina los datos de la cuenta mediante el flujo existente. La pantalla contenedora debe llamar a `clear()` antes de desconectar y a `close()` al destruir el lector para descartar resultados tardíos y referencias al contenido.

## Límites y presupuestos

Se mantienen arranque 5 s, bandeja 5 s, encabezado 2 s, contenido 2 s adicionales y envío 4 s. El lector comparte el mismo `Deadline` entre obtención, preparación y renderizado del contenido, sin reiniciar el presupuesto en cada paso. El trabajo de red, descifrado y limpieza ocurre en un ejecutor acotado fuera del hilo JavaFX. Si se agota el tiempo se informa y se ofrece reintento o texto disponible; no se simula éxito.

Límites de recursos actuales:

- 1 MiB de texto/HTML decodificado en conjunto; 256 partes MIME y 30 niveles de anidamiento.
- 20 000 elementos HTML.
- 512 KiB por imagen y 2 MiB agregados; dimensiones máximas 4096 por eje y 8 millones de píxeles.
- GIF: hasta 32 fotogramas y 8 millones de píxeles agregados.
- Imágenes grandes/no soportadas: marcador de contenido no disponible. Mensajes que exceden los límites estructurales: error recuperable.

Los controles de tiempo y las pruebas locales no garantizan latencia de proveedores reales. Sigue pendiente medir los cuatro flujos completos con cuentas reales y el equipo de referencia.

## Integración del visor

`MailReaderPane(MailFacade, Runnable volver, Consumer<URI> abrirEnlace)` es un componente reutilizable, creado en el hilo JavaFX. `open(accountId, mailId)` obtiene el encabezado y después el contenido; permite alternar HTML/texto y recuperar formato antiguo. El contenedor debe decidir cómo abrir enlaces confirmados por el usuario en el navegador del sistema.

El componente configura [WebEngine](https://openjfx.io/javadoc/21/javafx.web/javafx/scene/web/WebEngine.html) con JavaScript y ventanas emergentes deshabilitados, una política de recursos restrictiva y sin puente JavaScript/Java.

`App` sigue abriendo la pantalla de acceso original. Esta entrega no implementa la navegación completa del MVP ni reemplaza las pantallas aprobadas: el lector queda listo para incorporarse a esa bandeja.

## Validación

Las pruebas cubren extracción MIME, alternativas, caracteres, CID, rechazo de contenido activo y recursos externos, cifrado, migración repetible desde versión 1, lectura local con sesión que requiere autorización, recuperación de formato, envío en texto y visor JavaFX sin solicitudes HTTP al abrir el mensaje. Ejecutar con Java 21: `gradlew.bat test assemble`.


## Nota trasladada: javafx-views.md

# Vistas JavaFX basadas en las capturas

Se implementan las cinco composiciones entregadas el 2 de octubre de 2026:
carga (inicial y completa), acceso, bandeja con redacción, lector y cierre de
sesión. Los estados de los botones proceden de las variantes visibles en las
capturas. No se han modificado archivos de Figma.

## Archivos

Todos los FXML están en `src/main/resources/com/juanpablo/evermail/fxml` y los
estilos en la carpeta vecina `css`.

| Vista / componente | FXML | CSS |
| --- | --- | --- |
| Acceso | Login-Screen.fxml | Login-Styles.css |
| Carga | Loading-Screen.fxml, Loading-Complete.fxml | Loading.css |
| Principal | Main-Screen.fxml | Main.css |
| Navegación y cuenta | Sidebar.fxml | Sidebar.css |
| Bandeja | Inbox.fxml | Inbox.css |
| Fila de correo | Mail-Row.fxml | Mail-Row.css |
| Redacción | Compose.fxml | Compose.css |
| Lector | Reader-Screen.fxml | Reader.css |
| Confirmación de salida | Logout-Dialog.fxml | Logout.css |

`Common.css` contiene colores, tipografía, campos, avatares, barras de
desplazamiento y estados comunes. `Reader-Sample.fxml` contiene exclusivamente
el mensaje de demostración de la captura; se carga en el modo de previsualización.

## Distribución adaptable

No se utilizan `layoutX`, `layoutY`, `translateX`, `translateY` ni posicionamiento
absoluto de controles. Las dimensiones preferidas de ventana, logos, iconos y
espaciados son referencias de diseño; los paneles crecen mediante restricciones
de GridPane y prioridades de crecimiento.

`ResponsiveGrid` usa tres columnas a partir de 1100 px para la vista principal.
Entre 760 y 1099 px muestra navegación y bandeja en la primera fila, y redacción
debajo. Por debajo de 760 px apila los tres paneles. El lector pasa de dos
columnas a una por debajo de 760 px; el acceso apila sus dos bloques por debajo
de 900 px, para reservar espacio suficiente al formulario y sus textos.
El panel de acceso mantiene un ancho máximo de 326 px y una altura calculada
según su contenido (439 px como mínimo de diseño). No rellena la altura de la
ventana: `LoginCard` limita su máximo con `Region.USE_PREF_SIZE`, y la cuadrícula
conserva el centrado vertical. Este valor especial se configura desde Java,
no como `-Infinity` en CSS. Las barras de herramientas y los botones de los diálogos se
redistribuyen con FlowPane. Las ventanas bajas o estrechas ofrecen desplazamiento
vertical, sin ocultar funciones para hacerlas caber. El ancho mínimo de ventana
es de 360 px. `ViewController` solo adapta el contenido al área visible.

## Interacción visual

`AnimatedButton` anima la escala al entrar/salir y pulsar, durante 130 ms, sin
mover el espacio asignado al control. Las animaciones se interrumpen suavemente
cuando cambia el estado. CSS define hover, pulsación, foco de teclado y estado
deshabilitado. Se puede desactivar el movimiento con la propiedad JVM
`-Devermail.reduceMotion=true`; los colores y el foco siguen funcionando.

## Previsualizar sin cuentas reales

Con JDK 21, la ejecución normal inicia el backend y restaura la sesión o muestra el acceso. Para abrir una
composición concreta:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
./gradlew.bat run --args="--preview=main"
```

Valores disponibles: `login`, `loading`, `loaded`, `main`, `reader`, `logout`.
`PreviewData` añade 50 filas ficticias en la bandeja y permite alternar texto y
formato en el lector de demostración. No consulta cuentas, SQLite, credenciales
ni proveedores. Los nombres y textos del diseño son muestras, no sesión real.

## Integración funcional

`MailWindow` conecta los controles al `MailPresenter`, a través de
`CoordinatedPresentationBackend` y `ApplicationCoordinator`. Las notificaciones
se procesan con `Platform.runLater`. La aplicación reemplaza los textos de muestra
por la cuenta activa y los resultados del backend antes de presentar vistas
privadas. La carga refleja una operación pendiente sin simular porcentajes.
Los controles futuros permanecen visibles y deshabilitados, y los adjuntos vacíos.

`SafeMailBody` muestra en `bodyHost` el documento saneado por el backend, sin
iniciar otra carga de correo. Conserva el bloqueo de scripts, popups y recursos
remotos y la alternativa de texto. El diálogo de cierre es modal y confirma
la operación del presentador. El cierre de ventana espera `closeAsync()` sin
bloquear JavaFX. La [guía de configuración OAuth](docs/desarrollo/configuracion-oauth.md)
explica la preparación necesaria y las garantías y límites de seguridad.

## Fuentes visuales y límites

La revisión del 4 de octubre de 2026 consulta directamente
[Evermail Desing](https://www.figma.com/design/H0AVEbg2EFwkm0n3h3ucYv/Evermail-Desing?node-id=0-1).
Se comparan acceso (8:249), principal (8:282), lector (48:66), carga (20:2),
carga completa (20:10) y cierre (102:316).

`images/figma` contiene los recursos originales descargados. Los SVG se conservan
junto a sus PNG rasterizados a 3× para ImageView; `MailIcon` utiliza esos recursos,
sin redibujar sus trazados. `FigmaImage` mantiene sus proporciones en contenedores
nativos. Inter, Poppins e Inria Serif se incluyen en `fonts`, con sus licencias OFL,
y se cargan antes de aplicar los estilos. No se necesitan fuentes instaladas ni
URLs temporales de Figma durante la ejecución.

Los tamaños de la bandeja y el lector parten de 1920×1080. `ViewController` ajusta
la tipografía base entre 13.33 y 20 px; las dimensiones CSS en em acompañan esa
escala. Los paneles siguen redistribuyéndose con GridPane, VBox, HBox y FlowPane.
`ReaderDetails` apila remitente, fecha, destinatarios y formatos por debajo de
650 px de ancho disponible, y recupera dos columnas al ampliar la ventana.

La equivalencia no es píxel por píxel: el efecto Glass de Figma se aproxima con
GaussianBlur y una capa translúcida en `LoginCard`; JavaFX tiene métricas y
suavizado de texto propios. El cuerpo de ejemplo permanece como contenido
adaptable; los correos reales conservan su HTML saneado y sus estilos. Las
funciones futuras siguen deshabilitadas en la aplicación y los datos de cuenta
son dinámicos. No se modificó el archivo de Figma.

## Verificación

### Ajustes de interfaz del 6 de octubre de 2026

- El hover y la selección se pintan en la tarjeta redondeada del correo; su
  ancho descuenta el relleno de la celda para evitar recortes laterales.
- CC y CCO empiezan ocultos y sin ocupar espacio. Cada botón revela su campo;
  al enviar correctamente o descartar se restablecen. Editar un campo vacío
  no debe cerrarlo ni quitarle el foco. `ComposeController` aplica este estado
  desde la carga del FXML, también en previsualización. Los botones permanecen
  visibles y el contenido del borrador no despliega campos automáticamente.
- La X cierra el aviso superior hasta que cambia su mensaje o estado de sesión.
- El icono de cierre conserva su recurso original y adopta el color del texto.
- El diálogo de cierre parte de 570×550, sin decoraciones de Windows y con fondo
  transparente: el logo sobresale de la tarjeta sobre la ventana principal.
  Conserva botones en paralelo, se centra sobre Evermail y admite Cancelar o Escape.
  En pantallas pequeñas permite desplazamiento desde la parte superior.
- El lector amplía los destinatarios y centra una fecha de mayor tamaño.

Quedan pendientes por decisión del usuario: el punto 7 (formato del cuerpo,
imágenes externas y caracteres especiales) y el punto 8 (lentitud al abrir
correos). No se modifica su procesamiento ni se añade un proxy de imágenes.

`FxmlViewsTest` carga las seis vistas principales y sus componentes en JavaFX,
comprueba ausencia de desbordamiento horizontal de los contenedores y genera
capturas a 1920×1080, 1280×720, 960×540, 760×730, 600×900 y 360×800. También verifica
los colores hover, la alternancia del ejemplo de lectura, el redimensionamiento
de una misma vista y que los textos del cierre de sesión no pierdan altura al
ajustarse. Las capturas se guardan en
`build/ui-previews`, incluidas las partes inferiores de las vistas estrechas.

```powershell
./gradlew.bat test assemble --no-daemon
```

Estas pruebas visuales se complementan con `MailWindowTest`, `SafeMailBodyTest`
y las pruebas del transporte OAuth; ninguna sustituye la validación con cuentas reales.
