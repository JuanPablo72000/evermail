# Comprobación de fotos de remitentes — 6 de noviembre de 2026

Se investigó una bandeja que seguía mostrando iniciales después de autorizar Google.
Las consultas reales devolvieron estos resultados, sin registrar identidades ni URLs:

| Comprobación | Resultado |
| --- | --- |
| Contactos guardados | HTTP 200; 68 registros; 0 direcciones con foto utilizable |
| Otros contactos | HTTP 200; 9 registros; 5 direcciones con foto utilizable |
| Descarga y preparación de una foto de muestra | AVAILABLE |
| Últimos 50 correos | 21 remitentes distintos; 0 correos con coincidencia de foto |

En esta muestra los permisos y la descarga funcionaron. Las direcciones de los
remitentes recientes no coincidieron con las direcciones con foto proporcionadas
por Google. Por tanto, las iniciales de estos correos corresponden a una ausencia
de foto accesible mediante People API; no demuestran un fallo de autorización.
Estos números describen una muestra puntual, no la cobertura futura.

## Repetir el diagnóstico

Con Java 21 y una cuenta de Google activa guardada:

```powershell
.\gradlew.bat diagnoseAvatars --no-daemon
```

Consulta People API y descarga una miniatura disponible para comprobar su formato.
Para comparar además con los encabezados de los últimos 50 correos:

```powershell
.\gradlew.bat diagnoseAvatars --args="--inbox" --no-daemon
```

La base se abre en solo lectura y se cierra antes de consultar la red. Se leen las
credenciales mediante el almacén de Windows; una renovación necesaria se utiliza
solo en memoria. IMAP usa READ_ONLY y PEEK y obtiene ENVELOPE, sin descargar
cuerpos ni modificar mensajes o marcas. Solo se imprimen estados y cantidades;
no se guardan fotos, contactos, direcciones, tokens ni respuestas de Google.

La interfaz distingue ausencia, falta de permiso y fallo temporal mediante el
texto que aparece al pasar el cursor sobre las iniciales.
