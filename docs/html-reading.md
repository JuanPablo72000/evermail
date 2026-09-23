# Lectura híbrida de Evermail

La primera versión mantiene cuatro funciones: acceso/cierre de sesión, bandeja de entrada, lectura de correos y envío. La lectura admite HTML y texto; la composición y SMTP siguen enviando exclusivamente texto plano UTF-8.

## Recorrido implementado

`MailSessionProvider → HybridMime → RemoteMailContent → MailRepository → MailContent → MailPresentationService → MailReaderPane`.

- `multipart/alternative` conserva las representaciones HTML y texto separadas, sin duplicarlas en pantalla. Si no hay texto, se obtiene una alternativa legible.
- `multipart/related` identifica la raíz y resuelve únicamente imágenes CID referenciadas. Se omiten los adjuntos ordinarios y los recursos no utilizados.
- `HtmlMail` utiliza [jsoup](https://jsoup.org/news/release-1.22.2) para analizar y reconstruir un subconjunto permitido de HTML y estilos en línea. Conserva párrafos, tablas, colores y formato básico; no promete reproducción idéntica de todas las plantillas de Gmail/Outlook.
- Scripts, formularios, iframes, SVG, hojas de estilo externas, CSS con recursos y navegación directa no llegan al visor. Los enlaces HTTP/HTTPS quedan inertes hasta un clic, que se entrega al manejador de la aplicación; no se abren dentro del mensaje.
- PNG, JPEG y GIF incrustados se validan y se convierten a referencias de datos en memoria. No se crean archivos descifrados. Las imágenes externas permanecen bloqueadas en esta versión, sin descarga automática ni botón que prometa una función inexistente.

## Caché y migración

La versión SQLite pasa de 1 a 2 dentro de una transacción. Añade `mail.body_format`; no elimina ni reescribe cuerpos existentes. El formato 0 conserva el texto cifrado anterior y el contexto criptográfico `body`. El formato 1 guarda un sobre JSON cifrado con texto, HTML seguro e indicador de imágenes bloqueadas, bajo el contexto autenticado `body:hybrid-v1`.

Una apertura normal nunca necesita red cuando el cuerpo está guardado. Esto incluye cuentas en `REAUTH_REQUIRED` que aún tienen su clave local. La expiración de un token de acceso no equivale siempre a pedir acceso de nuevo: el backend puede renovarlo; la reautorización es necesaria cuando ya no puede hacerlo.

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
