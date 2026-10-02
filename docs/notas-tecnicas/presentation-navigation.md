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
