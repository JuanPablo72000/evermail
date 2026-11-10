# Fotos de remitentes de Google

Evermail muestra las fotos disponibles de los remitentes en la bandeja y en el
lector. Si no hay foto, aparece la inicial. Esta función consulta los contactos
guardados y «Otros contactos» de tu cuenta de Google; no permite obtener la foto
de cualquier dirección ni reproduce necesariamente todas las fotos de Gmail.
La foto de tu propia cuenta en la barra lateral y Microsoft siguen pendientes.

## Activación

La configuración necesaria de Google está documentada en los comentarios de
[`.env.example`](../../.env.example), junto a las credenciales de Google.

Inicia Evermail y carga la bandeja. Si tu sesión anterior no tiene permiso de
contactos, aparecerá un aviso con **Volver a autorizar**. Pulsa el botón y
acepta los permisos en Google. No necesitas cerrar sesión ni borrar tu cuenta.

No hay credenciales ni variables nuevas que completar en `.env`. Reutilizamos
el cliente OAuth del proyecto. Añadir los permisos en la consola no los concede
por sí solo al token guardado: también debes aceptar la nueva autorización.
Si no aceptas contactos, puedes continuar usando el correo con iniciales.

## Comportamiento y límites

- Solo se descargan las fotos de remitentes solicitados por las filas visibles
  o el lector. Una cola de hasta 100 direcciones y un trabajador independiente
  evitan ocupar el trabajador de descarga de correos.
- Se solicitan únicamente direcciones y fotos. Se compara la dirección completa,
  sin eliminar puntos ni etiquetas `+`. Las fotos predeterminadas se omiten.
- Cada directorio de Google se pagina hasta 10 páginas de 1.000 contactos, con un
  máximo de 10.000 direcciones con foto por fuente. Una colección mayor puede
  tener remitentes sin foto en Evermail. Los directorios se renuevan aproximadamente
  cada hora al consultar; no se mantiene una sincronización continua.
- Se validan PNG, JPEG y GIF, con un máximo de 1 MiB descargado por foto y límites
  de dimensiones. Solo se conserva una miniatura PNG de 96×96, sin animación.
- Hay hasta 128 resultados en memoria y 128 filas en SQLite. Las fotos caducan
  tras un día; las ausencias, tras 10 minutos. Los errores transitorios tienen
  una espera mínima de un minuto. **Actualizar** permite volver a solicitar los
  resultados visibles respetando esas caducidades, sin reintentos continuos.
- La caché en disco se cifra con la clave de la cuenta y forma parte de los 2 GB
  totales. Bajo presión se eliminan fotos antes que cuerpos. Una foto nueva no
  elimina cuerpos para conseguir espacio; puede mantenerse la inicial si no cabe.
- Cerrar la ventana limpia la caché de fotos y bandeja. Cerrar sesión elimina los
  datos de la cuenta. Las fotos no se conservan entre ejecuciones como archivo
  personal de contactos.
- Los tokens se envían solo a `people.googleapis.com`. Las fotos se descargan
  sin token desde HTTPS de `googleusercontent.com`, sin seguir redirecciones.
  No se usan servicios externos de avatares ni el proxy de imágenes de mensajes.

## Si no aparece una foto

La persona puede no estar en tus contactos, no tener una foto accesible, usar una
dirección distinta, o devolver Google una imagen predeterminada. También puede
haber un límite de API, un fallo de conexión o falta de espacio. Se mantienen las
iniciales y la lectura sigue funcionando. Si Google indica que falta permiso,
Evermail ofrece volver a autorizar; si People API está deshabilitada, revisa la
consola de Google y después vuelve a actualizar.

Al pasar el cursor por las iniciales se indica si Google no ofrece una foto para
esa dirección, si falta autorización o si la consulta falló temporalmente.
La ausencia de coincidencias no se resuelve concediendo de nuevo los mismos permisos.

## Verificación

Las pruebas locales usan transportes simulados, cuentas de prueba y SQLite
temporal. Cubren paginación, coincidencias exactas, permisos parciales, límites,
validación raster, cifrado, aislamiento, migración desde el esquema 2, cancelación,
conservación de cuerpos y actualización de los avatares JavaFX. No consultan tus
contactos ni sustituyen la comprobación final con tu cuenta autorizada.

Verificación local con Java 21: `gradlew.bat test assemble`.
La [comprobación con Google](../pruebas/fotos-remitentes.md) distingue los permisos
concedidos de la cobertura real de fotos en una bandeja.

Referencias oficiales: [configurar consentimiento](https://developers.google.com/workspace/guides/configure-oauth-consent),
[listar contactos](https://developers.google.com/people/api/rest/v1/people.connections/list)
y [listar otros contactos](https://developers.google.com/people/api/rest/v1/otherContacts/list).
