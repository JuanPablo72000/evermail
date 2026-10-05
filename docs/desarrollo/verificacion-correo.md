# Verificación de recepción y envío — 4 de octubre de 2026

## Resultado observado

La prueba contra la sesión de Google guardada en este equipo devolvió una respuesta
de renovación OAuth cuyo campo `scope` no contiene `https://mail.google.com/`.
Por eso esa autorización no permite usar Gmail por IMAP ni SMTP. No se imprimieron
tokens, direcciones de usuario ni contenido de mensajes.

Solo había una cuenta guardada, de Google. Microsoft queda pendiente de una sesión
real para comprobar sus permisos efectivos y la disponibilidad del buzón.
No se ha enviado ningún correo de prueba ni se ha confirmado entrega real.

## Requisitos y comprobación en el código

| Requisito | Google | Microsoft |
| --- | --- | --- |
| Aplicación | Cliente OAuth de escritorio | Cliente público de escritorio; cuentas personales y organizativas si se necesitan ambas |
| Permisos de correo solicitados | `https://mail.google.com/` | `https://outlook.office.com/IMAP.AccessAsUser.All` y `https://outlook.office.com/SMTP.Send` |
| Identidad y renovación | `openid email profile`; `access_type=offline` | `openid email profile offline_access` |
| Concesión efectiva | Se comprueba el campo `scope` si está presente al iniciar sesión y renovar | Se comprueban ambos permisos de correo si `scope` está presente |
| Autenticación del correo | XOAUTH2 con el access token | XOAUTH2 con el access token |
| Entrada | `imap.gmail.com:993`, TLS | `outlook.office365.com:993`, TLS |
| Salida | `smtp.gmail.com:587`, STARTTLS obligatorio | Personal: `smtp-mail.outlook.com:587`; organización: `smtp.office365.com:587`; STARTTLS obligatorio |
| Certificados | Verificación de identidad del servidor activada | Verificación de identidad del servidor activada |

La selección del servidor SMTP personal de Microsoft usa el emisor validado del
token de identidad, no el dominio de la dirección de correo. Las sesiones antiguas
sin identidad validada deben volver a autorizarse para identificar su tipo.

Los permisos de Graph `Mail.Read` y `Mail.Send` no reemplazan los permisos de IMAP
y SMTP que solicita esta implementación. La aplicación trabaja con INBOX para la
bandeja de entrada; esta revisión no agrega exploración de otras carpetas remotas.

## Ajustes externos que no se deducen del inicio de sesión

- En Google, la cuenta debe conceder el permiso de Gmail en la pantalla de
  autorización. Agregarlo en Google Cloud no amplía una autorización ya guardada.
  En modo de pruebas, la cuenta debe estar entre los usuarios de prueba.
- En Gmail personal IMAP está siempre activo. En Google Workspace pueden existir
  restricciones del administrador sobre IMAP y aplicaciones externas.
- En Outlook.com hay que habilitar IMAP en Configuración → Correo → Reenvío e IMAP.
- En Microsoft 365 debe existir un buzón de Exchange Online y el administrador
  debe permitir IMAP y SMTP AUTH con OAuth para ese buzón. El consentimiento en
  Azure no habilita por sí mismo esos protocolos.
- La red debe permitir HTTPS, IMAP TLS (993) y SMTP STARTTLS (587).

## Cambios realizados

- Rechazo explícito de respuestas OAuth con permisos de correo insuficientes,
  tanto en el inicio de sesión como en la renovación.
- Una renovación sin permisos activa la opción de volver a autorizar, conservando
  la cuenta y los correos locales.
- Servidor SMTP específico para cuentas personales de Microsoft.
- Límites de red: 30 segundos para bandeja/contenido y 60 para envío. El anterior
  límite de 5 segundos no era la causa del rechazo de autorización confirmado.
- Avisos de conexión más específicos y diagnóstico sin imprimir credenciales.

## Diagnóstico reproducible

Desde el proyecto, con Java 21:

```powershell
.\gradlew.bat diagnoseMail
```

Usa las cuentas guardadas del usuario de Windows actual, renueva y guarda los
tokens cuando el proveedor lo permite, prueba la lectura de un encabezado y
autentica SMTP. No envía mensajes, no guarda el encabezado consultado y no imprime
identificadores de cuenta, tokens ni datos del correo. No migra ni recupera el
estado de envíos pendientes. `SMTP_AUTH_ONLY=OK` confirma autenticación, no entrega.

Si no hay sesión guardada de un proveedor, iniciar sesión en Evermail antes de
repetir el diagnóstico. Un resultado FAILED sigue requiriendo investigación aunque
la tarea de Gradle termine correctamente.

## Referencias oficiales

- [Google: OAuth para IMAP y SMTP](https://developers.google.com/workspace/gmail/imap/xoauth2-protocol)
- [Google: comprobar los permisos concedidos](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Gmail: acceso desde otros clientes](https://support.google.com/mail/answer/7126229)
- [Microsoft: OAuth para IMAP y SMTP](https://learn.microsoft.com/en-us/exchange/client-developer/legacy-protocols/how-to-authenticate-an-imap-pop-smtp-application-by-using-oauth)
- [Outlook.com: servidores y activación de IMAP](https://support.microsoft.com/en-us/outlook/pop-imap-and-smtp-settings-for-outlook-com)
- [Microsoft 365: disponibilidad de SMTP AUTH](https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/authenticated-client-smtp-submission)
