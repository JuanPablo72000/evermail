# Formato de los correos

Evermail ofrece una vista HTML y una alternativa de texto. La vista HTML conserva
tablas, tipografía, colores, márgenes, bordes y reglas de diseño para distintos
anchos de pantalla. Los estilos propios del visor son valores de respaldo: se
respeta el formato admitido que incluye el mensaje.

## Imágenes y fondos

Las imágenes incrustadas PNG, JPEG y GIF se muestran sin conexiones adicionales.
Las imágenes externas y los fondos usan el [proxy configurado](proxy-imagenes.md).
El cuerpo aparece primero; después se incorporan las imágenes automáticamente.
Durante esa segunda etapa aparece «Cargando imágenes… Ya puedes leer el correo».
Si cambias a texto, la llegada de imágenes no cambia tu modo de lectura. Cambiar de
correo o cerrar sesión cancela esa carga y descarta sus resultados tardíos.
Si una imagen falla, el texto permanece disponible. El servicio de origen puede
identificar una apertura mediante una URL individual, aunque la petición use la
IP de Azure.

## Correos guardados antes de esta actualización

Con el proxy configurado, Evermail intenta recuperar el HTML original del proveedor
al abrir un correo guardado con el filtro anterior. Esto permite recuperar estilos
y fondos que ya se habían eliminado. Una recuperación correcta se guarda cifrada;
no se repite en las aperturas siguientes. Si no hay conexión o falta autorización,
se conserva la copia legible y se puede volver a abrir el mensaje para reintentar.

## Diferencias respecto de Gmail u Outlook

No se ejecutan scripts, formularios, iframes, animaciones ni fuentes remotas. Se
descartan hojas de estilo externas, posicionamiento fijo/absoluto, selectores
complejos y propiedades no admitidas. Las reglas de ancho de pantalla compatibles
se aplican al ancho del lector, no al ancho total de la ventana.

Los fondos pasan por los mismos controles de formato, tamaño y tiempo que las
imágenes. SVG, WebP, redirecciones y recursos que requieren iniciar sesión siguen
sin estar admitidos por el proxy actual. Las plantillas con código exclusivo de
Outlook o estilos fuera del subconjunto permitido pueden verse diferentes.

Si un mensaje se ve mal, prueba ampliar el lector o alternar a texto. Para reportar
un problema visual, compara el mismo mensaje en Evermail y en tu proveedor, ocultando
los datos personales de cualquier captura que compartas.
