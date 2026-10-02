# Pendientes para futuras versiones

Acuerdos registrados con el usuario el 27 de septiembre de 2026. Este documento conserva decisiones de alcance; no indica que las funciones estén implementadas.

## Próxima versión

- Incorporar el envío de adjuntos junto con mensajes de texto plano. Incluir selector de archivos, drag and drop y lista con nombre, tamaño y opción de quitar archivos. La integración debe contemplar validación, conservación de archivos para la cola de envío y reintentos, y limpieza posterior.
- Habilitar los controles **Anterior** y **Siguiente** en el lector para cambiar entre correos sin volver a la bandeja de entrada. Referencia visual aportada por el usuario: flecha izquierda junto a «Anterior» y flecha derecha junto a «Siguiente».
- Hacer funcional la pestaña **Enviados**, para consultar los correos enviados.

## Fix futuro, no crítico

- Revisar el error al repetir una dirección en campos de destinatarios distintos (Para, CC y CCO). El comportamiento deseado aún está por definir.

## Conservación de datos al cerrar sesión

- No borrar inmediatamente la información local de una cuenta cuando el usuario cierre sesión, para reducir la fricción al volver a iniciar sesión.
- Contemplar un plazo de conservación por cuenta de correo: borrar los datos después de un período sin iniciar sesión con esa cuenta específica.
- Dejar por definir la duración del plazo y si la política será fija o configurable por el usuario en una versión futura. No se ha acordado todavía un plazo concreto ni la versión de implementación.
- Conservar datos no implica mantener la sesión iniciada.

## Alcance de la versión actual

- Envío de texto plano sin adjuntos. El acuerdo posterior de conservación del diseño sustituye la indicación anterior de quitar controles: se mantiene el espacio vacío de adjuntos y los controles futuros sin acción. Véase [Requisitos del sistema](requisitos-del-sistema.md), sección 3.
- Campos de redacción acordados: De, Para, CC, CCO, Asunto y Mensaje.
- Mostrar CC y CCO mediante botones desplegables fue una sugerencia, no una decisión confirmada.
