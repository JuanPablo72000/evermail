# Ajustes de diseño — 9 de noviembre de 2026

- El icono de cerrar sesión conserva el recurso original y deja un margen interior para que el suavizado no quede recortado.
- CC y CCO alternan de forma independiente. Ocultar conserva las direcciones; el botón muestra `•` y una descripción cuando hay destinatarios ocultos. Al enviar correctamente o descartar, el formulario vuelve al estado inicial. Durante un envío o un resultado incierto los controles respetan el bloqueo del borrador.
- Las ventanas decoradas usan la barra superior morada y un contorno azul nativo, incluidas las esquinas redondeadas. Se conservan los controles de Windows para mover, minimizar, maximizar y cerrar. El diálogo transparente de cierre utiliza su propio borde azul.
- El lector conserva `overflow: hidden/visible/clip`, `visibility: visible/hidden/collapse`, opacidad entre 0 y 1 y el atributo HTML `hidden`. Esto permite mantener ocultos los preencabezados y evita que su texto estrecho genere una columna vertical y desplazamiento vacío. Se aplica tanto a estilos en línea como a hojas de estilo, incluso después del saneamiento repetido.

La barra y el borde usan los [atributos DWM de Windows](https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute). Los colores personalizados requieren Windows 11; cuando el sistema no los admite, permanece la decoración del sistema. La apertura del paquete de interfaz a JNA permite invocar el callback nativo también en el arranque modular.

No se permite JavaScript, posiciones absolutas/fijas, fuentes externas ni conexiones directas desde el WebView. Los recursos del correo siguen pasando por la validación y el proxy existentes. El cambio no modifica `.env` ni añade configuración.

## Verificación

Con Java 21:

```powershell
./gradlew.bat test assemble verifyMailRuntime --no-daemon
```

Las pruebas de interfaz revisan tamaños de 360 a 1920 píxeles, alternancia independiente de CC/CCO, conservación de direcciones al ocultar y reinicio tras un envío simulado. Las pruebas del lector verifican un preencabezado de tamaño cero, ausencia de desplazamiento extra, adaptación de columnas y cero solicitudes directas a un servidor de prueba. `verifyMailRuntime` abre una ventana sin cuentas y comprueba los atributos nativos desde el módulo real.

Resultado: 221 pruebas pasaron sin errores ni omisiones. Tras ajustar la reaplicación del marco al terminar de mostrar la ventana, también pasaron la prueba específica de apariencia, `assemble` y `verifyMailRuntime`. Se revisó la previsualización `main` de forma interactiva: barra morada, contorno azul redondeado y CC que se abre y vuelve a ocultarse. La previsualización se cerró al terminar.

El caso de preencabezado reproduce el patrón visual de la captura; no se dispone del HTML original del correo de LinkedIn. No se garantiza corregir todos sus formatos con esta muestra. La caché de bandeja se limpia al reiniciar según la política vigente, por lo que los mensajes se vuelven a preparar con el filtro actualizado.
