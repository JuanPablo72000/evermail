# Tiempos de lectura — 11 de octubre de 2026

Se midieron tres mensajes reales que todavía no tenían cuerpo en la caché local,
seguidos cada uno por una segunda apertura. Se ejecutó el backend con Java 21
en este equipo, usando la sesión guardada y el proxy configurado.

| Muestra | Conexión/autorización/selección IMAP | Descarga MIME y saneamiento | Obtención del cuerpo completa | Formato e imágenes | Total primera apertura | Total segunda apertura |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 3,488 s | 6,073 s | 10,164 s | 3,760 s | 13,924 s | 1,483 s |
| 2 | 1,128 s | 2,379 s | 4,941 s | 2,110 s | 7,051 s | 1,529 s |
| 3 | 0,647 s | 1,014 s | 1,858 s | 1,531 s | 3,389 s | 1,005 s |

Conexión y descarga son partes de la obtención del cuerpo: no se deben sumar
otra vez al total. La obtención completa incluye además lectura/escritura de
caché, cifrado/descifrado y cierre de la conexión.

En la segunda apertura, el cuerpo tardó 10, 11 y 10 ms; preparar formato e
imágenes tardó 1.472, 1.517 y 995 ms. Las tres muestras tenían HTML. En las muestras
2 y 3 quedaron imágenes bloqueadas/no disponibles en ambas aperturas; el indicador
no identifica la causa ni garantiza que todas las solicitudes de imagen tuvieran éxito.

Los totales corresponden al backend; excluyen encabezado, espera en la cola de
interfaz, marcado local como leído y renderizado del WebView. La primera muestra
tiene también efectos de inicialización de clases, conexiones y clientes. Tres
mensajes distintos no bastan para obtener percentiles ni atribuir la variación
únicamente al tamaño del correo o a Azure.

## Qué muestran los resultados

La caché del cuerpo funciona y es rápida. La apertura inicial concentra tiempo
en la conexión IMAP y en obtener/procesar el MIME. El código abre una conexión
IMAP por descarga. Además, el lector espera a que termine la preparación de las
imágenes antes de entregar el cuerpo a la interfaz. Las imágenes externas se
vuelven a solicitar incluso cuando el cuerpo ya está guardado.

Las mejoras por evaluar son mostrar el cuerpo antes de terminar las imágenes,
medir las solicitudes individuales del proxy, y reducir los viajes de red IMAP
sin descargar adjuntos innecesarios ni superar los límites de tamaño existentes.
Esta medición no modifica esos comportamientos.

## Repetir la medición

Cerrar Evermail y ejecutar con Java 21 desde el proyecto:

```powershell
.\gradlew.bat measureMailReads --console=plain
```

La tarea selecciona hasta tres cuerpos sin descargar entre los encabezados ya
guardados en la primera página local de las cuentas. Cada cuerpo queda guardado
cifrado como al abrirlo normalmente. No borra la caché, no envía correos, no marca
los mensajes como leídos ni imprime asuntos, direcciones, cuerpos, URLs o claves.
Puede renovar la autorización guardada si hace falta y solicita imágenes al
proxy configurado. No modifica `.env`. Si no encuentra candidatos imprime
`NO_UNCACHED_MESSAGES_IN_LOCAL_FIRST_PAGE`; actualizar la bandeja en la aplicación
permite incluir nuevos mensajes antes de repetir. No repite exactamente la misma
muestra, porque los cuerpos medidos ya están en caché.

## Excepción al arrancar

El `NullPointerException` informado no se reprodujo en las pruebas del lector
ni al abrir/cerrar la aplicación real; el usuario confirmó que dejó de aparecer.
No se ha identificado ni corregido una causa de esa excepción.
El aviso independiente de SLF4J se atiende configurando explícitamente
`slf4j-nop`: conserva el comportamiento sin registro de esas bibliotecas y no
suprime las excepciones no capturadas de Java.

## Segunda medición: cuerpo antes de imágenes

Se separaron las dos etapas del lector, se permitieron tres solicitudes de imagen
simultáneas y se redujeron consultas IMAP: precarga de estructura MIME y sobre,
Content-ID desde la estructura y bloques de descarga de 128 KiB. Se conservan
los límites de tamaño, el proxy, la sanitización y el bloqueo de red del WebView.

| Nueva muestra | Cuerpo legible, backend | Imágenes, etapa posterior | Completo, backend | Cuerpo desde caché |
| --- | ---: | ---: | ---: | ---: |
| 1 | 14,558 s | 2,179 s | 16,737 s | 0,070 s |
| 2 | 4,450 s | 0,266 s | 4,717 s | 0,012 s |
| 3 | 2,909 s | 2,243 s | 5,153 s | 0,173 s |

La primera muestra invirtió 8,168 s en conexión/autorización/selección y 5,058 s
en descarga MIME/saneamiento. Las otras invirtieron 1,285/0,916 s y 0,823/1,341 s,
respectivamente. Las muestras 2 y 3 terminaron sin imágenes bloqueadas; la 1
conservó recursos no disponibles. Son mensajes diferentes de la primera medición,
por lo que estas cifras no prueban por sí solas una reducción porcentual.

Dos de tres cuerpos quedaron listos en menos de 5 s, pero **el objetivo todavía
no se cumple en todos los casos**, y falta incluir el renderizado real en esa cifra.
No se ha reducido el timeout a 5 s: abortar una descarga lenta no sería cumplir
el objetivo de mostrar el mensaje. La reutilización de conexiones IMAP queda
pendiente; requiere controlar desconexiones y cerrar los recursos al cambiar de
cuenta o cerrar sesión. El comando de medición ahora informa `READABLE_BACKEND_MS`,
`READABLE_WITHIN_5S` y la cantidad de imágenes incorporadas como datos, además del
total con imágenes. No imprime el contenido ni las URLs de las imágenes.
