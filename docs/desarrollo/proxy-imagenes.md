# Imágenes externas mediante Azure

En el `.env` local de Evermail, configura:

```dotenv
IMAGE_PROXY_URL=https://evermail-images-juanp-hjeyfehagffreba3.mexicocentral-01.azurewebsites.net/api/image
IMAGE_PROXY_KEY=
```

Completa `IMAGE_PROXY_KEY` con la clave de la función `image` y reinicia Evermail.
No publiques la clave ni la incluyas en Git. No se necesita Docker para ejecutar el cliente.

Al abrir un mensaje HTML, Java envía `POST {"url":"https://..."}` al endpoint
configurado, con la clave en `x-functions-key`. El visor no recibe la clave ni carga
recursos de Internet: solo recibe imágenes PNG/JPEG/GIF validadas como URI `data:`.
No se siguen redirecciones ni se recurre a descargar directamente desde el origen.

Las referencias externas de imágenes y fondos se guardan inertes dentro del cuerpo cifrado. Los cuerpos
guardados por versiones anteriores que habían perdido las referencias o los estilos se recuperan
del proveedor al abrirlos si el proxy está configurado. Si falla esa recuperación,
se conserva la copia legible; se puede volver a intentar abriendo el correo de nuevo.
La versión de referencias se guarda en el sobre cifrado, sin migrar el esquema SQL.

La descarga se realiza después de entregar el cuerpo legible: hasta 15 segundos para todas las
imágenes, hasta 9 segundos por petición, tres peticiones simultáneas y 24 URLs distintas por apertura. Se dejan
500 ms dentro del presupuesto de imágenes y al menos 1 segundo del presupuesto de
preparación para completar el contenido. El cliente mantiene los límites de imágenes
del visor (512 KiB por imagen, 2 MiB agregados entre imágenes y fondos, y límites de dimensiones/fotogramas).
Azure admite hasta 2 MiB por imagen, pero el límite local es más estricto.

Las imágenes descargadas viven en la presentación en memoria; se vuelven a solicitar
al abrir el mensaje. Sin conexión, sin clave, con errores o formatos no admitidos,
el texto sigue disponible y se muestra un aviso de imágenes no disponibles. HTTP,
SVG, WebP y URLs que requieren redirección no se admiten en este flujo.

El servidor de la imagen ve la IP de Azure, pero una URL de seguimiento individual
todavía puede identificar la apertura del correo. No distribuyas una clave compartida
en una aplicación pública; para eso se necesita autenticación por usuario en el proxy.

Validación local: `gradlew.bat test assemble` con Java 21. Las pruebas del cliente usan
un servidor simulado, sin enviar claves reales ni solicitudes a Azure.
