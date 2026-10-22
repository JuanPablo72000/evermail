# Aceptación del MVP — 11 de octubre de 2026

## Verificación posterior — 16 de octubre de 2026

- El usuario confirmó con su cuenta real que, tras la corrección de restauración
  de sesión, la bandeja carga automáticamente al abrir Evermail sin pulsar Recargar.
- Las fotos de cuenta y remitentes siguen sin estar implementadas: la interfaz
  muestra iniciales. Esta comprobación no acredita carga de avatares.

## Confirmación del usuario

- Google: acceso al correo y funcionamiento correcto de los flujos, según su prueba.
- Cerrar y reabrir Evermail mantiene la sesión.
- «Cargar más» funciona.
- Lectura con imágenes mediante el proxy ya disponible. El rendimiento variable
  se acepta como limitación para esta versión de uso personal.
- Microsoft personal aún falla al consultar la bandeja y enviar.
- Instalador y ejecutable nativo: aplazados expresamente; no se generó el instalador.

## Diagnóstico de Microsoft

`diagnoseMail` renueva la autorización guardada, consulta un encabezado y autentica
SMTP sin enviar mensajes ni imprimir datos del correo o credenciales.
La primera ejecución obtuvo renovación OAuth correcta, rechazo IMAP con el marcador
`AUTHENTICATED BUT NOT CONNECTED` y rechazo SMTP `5.7.139`.
El usuario activó IMAP en Outlook.com; las comprobaciones posteriores todavía
rechazaron el acceso. Se confirmó el servidor personal `smtp-mail.outlook.com`.
El diagnóstico ampliado confirmó `SMTPCLIENTAUTHENTICATION IS DISABLED` y
`AUTHENTICATION UNSUCCESSFUL`: el servidor deniega la autenticación SMTP, no es
evidencia de que el puerto 587 esté bloqueado. No se ha demostrado por qué la
cuenta tiene esa restricción ni una causa única de ambos fallos.

## Comprobación automatizada de errores

Con Java 21 se completaron las tareas `test`, `assemble` y `verifyMailRuntime`:
171 pruebas, cero fallos, cero errores y cero omitidas. El proceso se interrumpió
después, durante la preparación del empaquetado, por petición del usuario.

| Área | Evidencia |
| --- | --- |
| SMTP: autorización, DNS, TLS, timeout y conexión | `OperationErrorTest` distingue las causas sin exponer mensajes internos; un rechazo de autenticación no se atribuye al puerto 587. |
| Proxy: error HTTP, redirección, formato/tamaño inválidos y demora | `ImageProxyClientTest` verifica rechazo y límites. |
| Conservación del cuerpo y respuestas tardías de imágenes | `MailPresenterTest` verifica estados y aislamiento de solicitudes. |
| Pérdida de red y lectura de caché | `InboxFlowIntegrationTest` usa transporte simulado y SQLite temporal. |
| Envío incierto y reinicio | `OutboxRestartIntegrationTest` verifica persistencia y ausencia de reenvío automático. |
| Bloqueo visual del envío incierto y cierre durante operaciones | `MailWindowTest` verifica controles JavaFX con backend simulado. |

Estas pruebas no acreditan entrega SMTP real ni sustituyen una prueba manual de
fallos de red en la aplicación conectada. Quedan pendientes esa aceptación manual,
resolver Microsoft y confirmar después recepción y envío desde su interfaz.

El archivo `.env` del proyecto no se modificó. La instalación de WiX y la
generación del instalador no se realizaron.
