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
