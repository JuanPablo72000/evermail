# Persistencia, identidad y seguridad

Esta guía conecta cuatro decisiones que suelen resultar confusas al estudiar el código: separar DAO y repositorios, almacenar claves fuera de SQLite, distinguir OAuth de protocolos de correo y conservar estados intermedios recuperables.

## DAO, repositorio y transacción no son lo mismo

Un DAO sabe consultar filas JDBC. Un repositorio sabe qué cuenta y qué objeto representan, cuándo cifrar y qué cambios deben ser atómicos. `TransactionManager` posee el acceso serializado a una conexión y confirma o revierte el bloque de escritura.

Por ejemplo, guardar una página de bandeja implica encabezados, destinatarios y cobertura de UID. Si se guardara la cobertura antes de terminar los destinatarios, una interrupción podría hacer creer que una página incompleta ya está disponible. El bloque transaccional evita ese estado parcial.

La red se realiza fuera de las transacciones SQL. Sin embargo, puede estar dentro del bloqueo por cuenta de `AccountCoordinator`; son protecciones diferentes. Una evita cambios SQL parciales, la otra evita operaciones incompatibles sobre la cuenta.

El código también usa `Sql` directamente desde repositorios. La separación no significa que cada sentencia tenga obligatoriamente un método de DAO. Los detalles físicos y los límites de los CHECK están en el [ER](../datos/diagramas/er-diagram.md).

## Qué se cifra y por qué importa el contexto

SQLite guarda tokens y cuerpos cifrados con AES-256-GCM. Asuntos, direcciones, fechas y otros metadatos no están cifrados. No se trata de una base completamente cifrada.

GCM produce datos cifrados y una etiqueta que permite detectar alteraciones. Cada operación utiliza un nonce aleatorio de 12 bytes. `CryptoContext` aporta datos autenticados adicionales: cuenta, registro y campo. Si se copia el cuerpo cifrado de un correo a otro identificador, la verificación no debería aceptarlo como contenido del segundo.

Por esa razón `commitSent` no copia literalmente el ciphertext de outbox a mail: descifra y vuelve a cifrar para el contexto del registro SENT. Las versiones del sobre permiten distinguir formatos; un error de autenticidad no se interpreta automáticamente como un formato antiguo.

`KeyStoreService` ofrece crear, leer y eliminar una clave identificada por `key_ref`. `OsKeyStoreService` adapta ese contrato al almacén del sistema. La clave no se sustituye silenciosamente si falta: una clave nueva no podría recuperar datos cifrados con la anterior. Renovar OAuth o reautorizar la misma identidad conserva la clave de cuenta.

## OAuth no es IMAP ni SMTP

OAuth obtiene autorización y tokens. OIDC valida la identidad mediante ID token: emisor, audiencia, firma, expiración y nonce. PKCE vincula el código al intento que lo inició, y state protege la correspondencia del callback. El navegador del sistema realiza el acceso; Evermail no pide la contraseña del correo en un formulario propio.

Después, IMAP y SMTP utilizan el access token mediante XOAUTH2. Haber iniciado sesión no prueba por sí solo acceso al buzón ni permiso de envío. Los scopes, el tipo de cuenta y la configuración del proveedor pueden permitir una operación y rechazar otra. Por eso `MailDiagnostics` separa renovación, lectura IMAP y autenticación SMTP.

El margen de renovación es 60 s. `ensureCredentials` vuelve a comprobar credenciales bajo bloqueo de cuenta para evitar renovaciones incompatibles. Una cuenta que requiere autorización puede conservar sus claves; eso no autoriza seguir conectando con credenciales rechazadas.

La configuración paso a paso está en [OAuth](../desarrollo/configuracion-oauth.md). Esa guía explica qué debe configurar la persona; este documento explica el propósito de la arquitectura.

## Alta y eliminación recuperables

Crear una fila SQLite y crear una clave en el almacén del sistema no forman una sola transacción. El alta registra PROVISIONING, crea la clave y activa la cuenta con tokens cifrados. Si falla, intenta compensar lo creado; si la compensación queda incompleta, el arranque recupera el estado.

Logout marca DISCONNECTING antes de retirar clave y datos. Esto evita que una cuenta parcialmente desconectada se trate como ACTIVE. El arranque vuelve a procesar PROVISIONING y DISCONNECTING. El estado visible de sesión y el estado persistente de cuenta son conceptos relacionados pero distintos.

## Migración y limpieza

El esquema activo tiene versión 2. La migración desde versión 1 añade `body_format` sin reescribir el contenido. Cuando detecta el esquema legado, `DatabaseMigrator` conserva tablas renombradas como `legacy_*`; `LegacyImporter` importa cuentas y permite consultar correo archivado. No inventa UID ni incorpora automáticamente esos correos a la nueva bandeja.

Eliminar filas no reduce necesariamente el archivo físico SQLite. `clearInboxCache` ejecuta DELETE dentro de una transacción y después VACUUM fuera de ella. Esto recupera espacio, pero **no es un administrador de presupuesto total**: no reserva bytes para descarga o compactación ni implementa el límite de 2 GB de aplicación y datos.

La limpieza de ventana conserva cuenta, claves y envíos; logout elimina los datos de la cuenta. Ninguna de estas operaciones borra correos originales de Google o Microsoft. Véase [sesión y cierre](sesion-y-concurrencia.md).

Fuentes: [SecurityUtil](../../src/main/java/com/juanpablo/evermail/util/SecurityUtil.java), [AuthService](../../src/main/java/com/juanpablo/evermail/service/AuthService.java), [TransactionManager](../../src/main/java/com/juanpablo/evermail/repository/TransactionManager.java), [DatabaseMigrator](../../src/main/java/com/juanpablo/evermail/repository/DatabaseMigrator.java).
