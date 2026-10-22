# Modelos de dominio y transporte

Referencia de la implementación revisada el 17 de octubre de 2026. Se muestran responsabilidades y contratos seleccionados; no todos los miembros privados ni las sobrecargas. Las clases externas al paquete se incluyen solo para explicar dependencias.

```mermaid
classDiagram
    class Account {
        -UUID id
        -OAuthProvider provider
        -String providerSubject
        -String email
        -String displayName
        -String keyRef
        -AccountStatus status
    }
    class AccountStatus {
        <<enumeration>>
        PROVISIONING
        ACTIVE
        REAUTH_REQUIRED
        DISCONNECTING
    }
    class Identity {
        -OAuthProvider provider
        -String subject
        -String email
        -String displayName
    }
    class OAuthProvider {
        <<enumeration>>
        GOOGLE
        MICROSOFT
    }
    class OAuthCredentials {
        -String accessToken
        -String refreshToken
        -Instant expiresAt
    }
    class AuthorizationResult {
        -Identity identity
        -OAuthCredentials credentials
    }
    class CryptoContext {
        -UUID accountId
        -UUID recordId
        -String fieldName
    }
    class StartupResult {
        -Account account
        -StartupStatus status
    }
    class StartupStatus {
        <<enumeration>>
        LOGIN_REQUIRED
        READY
        OFFLINE
        RECOVERY_REQUIRED
    }
    Account --> OAuthProvider
    Account --> AccountStatus
    Identity --> OAuthProvider
    AuthorizationResult --> Identity
    AuthorizationResult --> OAuthCredentials
    StartupResult --> Account
    StartupResult --> StartupStatus
```

## Identidad y credenciales

Los modelos de este paquete son valores; Lombok genera getters en las clases anotadas con `@Value`. `Identity` representa la identidad validada por OIDC; `Account` añade identidad local, estado y referencia de clave. `AuthorizationResult` une identidad y credenciales durante el acceso. `CryptoContext` identifica cuenta, registro y campo para autenticar el cifrado.

`OAuthProvider` también contiene configuración de endpoints, scopes y servidores. Se muestran sus dos valores para no mezclar configuración de proveedor con todos los campos de dominio. Las credenciales no se entregan a los controles ni se incluyen en `toString`.

## Bandeja, contenido y representación

```mermaid
classDiagram
    class MailHeader {
        -UUID id
        -UUID accountId
        -MailDirection direction
        -RemoteMailId remoteId
        -String messageId
        -UUID outboundId
        -String senderEmail
        -String senderName
        -String subject
        -Instant occurredAt
        -boolean read
        -boolean bodyCached
    }
    class MailDirection {
        <<enumeration>>
        INBOX
        SENT
    }
    class RemoteMailId {
        -long uidValidity
        -long uid
    }
    class MailContent {
        -UUID mailId
        -String plainText
        -String html
        -boolean blockedRemoteImages
        -boolean legacyTextOnly
        -List recipients
        -boolean legacyImageReferences
    }
    class Recipient {
        -String email
        -String addressKey
        -String displayName
        -RecipientType type
    }
    class RecipientType {
        <<enumeration>>
        TO
        CC
        BCC
    }
    class InboxCursor {
        -UUID accountId
        -long uidValidity
        -long beforeUid
    }
    class InboxPage {
        -List items
        -InboxCursor nextCursor
        -boolean hasMore
        -boolean stale
        -boolean needsRemote
    }
    class RemoteMailHeader {
        -RemoteMailId remoteId
        -String messageId
        -String senderEmail
        -String senderName
        -String subject
        -Instant occurredAt
    }
    class RemoteMessage {
        -RemoteMailHeader header
        -List recipients
    }
    class RemoteInboxPage {
        -long uidValidity
        -long lowerUid
        -long upperUid
        -List messages
        -boolean hasMore
    }
    class RemoteMailContent {
        -String plainText
        -String html
        -boolean blockedRemoteImages
        -List recipients
    }
    class MailPresentation {
        -MailContent content
        -String document
        -DisplayNode body
    }
    MailHeader --> MailDirection
    MailHeader --> RemoteMailId
    MailContent --> Recipient
    Recipient --> RecipientType
    InboxPage --> MailHeader
    InboxPage --> InboxCursor
    RemoteMailHeader --> RemoteMailId
    RemoteMessage --> RemoteMailHeader
    RemoteMessage --> Recipient
    RemoteInboxPage --> RemoteMessage
    RemoteMailContent --> Recipient
    MailPresentation --> MailContent
    MailPresentation --> DisplayNode
    class DisplayNode {
        +String tag
        +String text
        +Map attributes
        +List children
    }
    DisplayNode --> DisplayNode : hijos
```

`List` y `Map` abrevian colecciones genéricas; las flechas indican el tipo de elemento relevante. `DisplayNode` es el record anidado `MailPresentation.DisplayNode`: captura el DOM saneado para actualizar imágenes en JavaFX sin preparar HTML en el hilo visual.

Los tipos `Remote*` proceden de IMAP y no tienen el UUID de un correo local. El repositorio vincula `RemoteMailId` —UIDVALIDITY y UID— con la cuenta y el UUID local. `InboxCursor` incluye cuenta, generación y límite de UID: no es un número de página intercambiable entre buzones.

`bodyCached` indica disponibilidad del cuerpo; no garantiza imágenes externas descargadas. `legacyTextOnly` permite identificar cuerpos antiguos en texto, y `legacyImageReferences` señala HTML cuyo sobre tiene referencias anteriores a la versión 2. `MailPresentation` es un resultado para mostrar, no una tabla persistente.

## Composición y envío

```mermaid
classDiagram
    class ComposeRequest {
        -UUID submissionId
        -UUID accountId
        -String subject
        -String plainText
        -List recipients
    }
    class OutboxMessage {
        -UUID id
        -UUID accountId
        -String messageId
        -String subject
        -String plainText
        -List recipients
        -DeliveryState state
        -Instant createdAt
        -Instant updatedAt
        -ErrorCode lastError
    }
    class DeliveryState {
        <<enumeration>>
        PENDING
        SENDING
        ACCEPTED
        RECORDED
        FAILED
        UNKNOWN
    }
    class SendResult {
        -UUID submissionId
        -DeliveryState state
        -UUID sentMailId
        -ErrorCode error
    }
    ComposeRequest --> Recipient
    OutboxMessage --> Recipient
    OutboxMessage --> DeliveryState
    OutboxMessage --> ErrorCode
    SendResult --> DeliveryState
    SendResult --> ErrorCode
```

`ComposeRequest` lleva `submissionId` para reconocer el mismo intento. `OutboxMessage` es el intento persistido y recuperable; `SendResult` informa su resultado, que puede ser incierto. `sentMailId` solo existe cuando hay registro local del enviado. Mantener separados estos modelos evita tratar un formulario editable como si ya fuera una entrega confirmada.

La estructura física está en el [ER](../../datos/diagramas/er-diagram.md). El motivo de los estados de envío se explica en [envío y recuperación](../envio-y-recuperacion.md). Fuente: [paquete model](../../../src/main/java/com/juanpablo/evermail/model).
