# Servicios y adaptadores de infraestructura

Referencia de la implementación revisada el 17 de octubre de 2026. Se muestran responsabilidades y contratos seleccionados; no todos los miembros privados ni las sobrecargas. Las clases externas al paquete se incluyen solo para explicar dependencias.

```mermaid
classDiagram
    class BackendContext {
        +create() BackendContext
        +closeMailConnections() void
        +close() void
    }
    class StartupService {
        +start(Deadline deadline) StartupResult
    }
    class AuthService {
        +login(OAuthProvider provider, CancellationToken cancel) Account
        +restoreSession(Deadline deadline) StartupResult
        +ensureCredentials(UUID id, Deadline deadline) OAuthCredentials
        +logout(UUID id, Deadline deadline) void
        +recoverAccountLifecycle(Deadline deadline) void
    }
    class InboxService {
        +readCached(UUID accountId, InboxCursor cursor) InboxPage
        +refresh(UUID accountId, Deadline deadline) InboxPage
        +loadMore(UUID accountId, InboxCursor cursor, Deadline deadline) InboxPage
        +openHeader(UUID accountId, UUID mailId) MailHeader
        +loadContent(UUID accountId, UUID mailId, Deadline deadline) MailContent
        +reloadContent(UUID accountId, UUID mailId, Deadline deadline) MailContent
        +markRead(UUID accountId, UUID mailId) void
    }
    class ComposeService {
        +validate(ComposeRequest request) ComposeRequest
    }
    class MailSendService {
        +send(ComposeRequest request, Deadline deadline) SendResult
        +recover(UUID accountId, Deadline deadline) void
    }
    class AccountCoordinator {
        +exclusive(UUID accountId, Deadline deadline, Work work) Object
    }
    class MailGateway {
        <<interface>>
        +openInbox(UUID accountId, Deadline deadline) InboxSession
        +openSmtp(UUID accountId, Deadline deadline) SmtpSession
    }
    BackendContext --> StartupService
    BackendContext --> AuthService
    BackendContext --> InboxService
    BackendContext --> MailSendService
    BackendContext --> MailGateway
    StartupService --> DatabaseMigrator
    StartupService --> LegacyImporter
    StartupService --> AccountRepository
    StartupService --> AuthService
    StartupService --> MailSendService
    AuthService --> AccountRepository
    AuthService --> KeyStoreService
    AuthService --> OAuthGateway
    AuthService --> AccountCoordinator
    InboxService --> MailGateway
    InboxService --> MailRepository
    InboxService --> AccountCoordinator
    MailSendService --> MailGateway
    MailSendService --> OutboxRepository
    MailSendService --> ComposeService
    MailSendService --> AccountCoordinator
```

## Por qué se separan estas piezas

Los servicios coordinan casos de uso; los repositorios deciden cómo persistirlos. Las interfaces `MailGateway` y `OAuthGateway` permiten sustituir red por transportes simulados en pruebas. `BackendContext` construye los colaboradores compartidos y se ocupa de cerrar transporte, SQLite y almacén de claves.

`AccountCoordinator.exclusive` mantiene un bloqueo por cuenta durante toda la operación suministrada, incluida la red cuando el servicio la realiza dentro del bloque. No es un planificador de prioridad. `ApplicationCoordinator`, descrito aparte, organiza el trabajo de la sesión y la precarga. Esta distinción explica por qué añadir hilos no garantiza que una apertura adelante a una descarga activa.

## Autenticación y claves

```mermaid
classDiagram
    class OAuthGateway {
        <<interface>>
        +authorize(ProviderConfig config, CancellationToken cancel) AuthorizationResult
        +refresh(config, previous, deadline) OAuthCredentials
    }
    class KeyStoreService {
        <<interface>>
        +create(String keyRef) void
        +read(String keyRef) SecretKey
        +delete(String keyRef) void
    }
    OAuthGateway <|.. OAuthClient
    OAuthClient --> OidcTokenValidator
    KeyStoreService <|.. OsKeyStoreService
    OsKeyStoreService --> SecurityUtil
```

OAuth obtiene autorización y una identidad validada. El almacén del sistema conserva las claves usadas para cifrar datos locales; esos dos tipos de credencial tienen ciclos de vida diferentes. Véase [persistencia y seguridad](../persistencia-y-seguridad.md).

## Transporte de correo

```mermaid
classDiagram
    class InboxSession {
        <<interface>>
        +fetchLatest(int size, Deadline deadline) RemoteInboxPage
        +fetchBefore(InboxCursor cursor, int size, Deadline deadline) RemoteInboxPage
        +fetchContent(RemoteMailId id, Deadline deadline) RemoteMailContent
        +close() void
    }
    class SmtpSession {
        <<interface>>
        +submit(OutboxMessage outgoing, Deadline deadline) Outcome
        +close() void
    }
    MailGateway <|.. MailSessionProvider
    MailSessionProvider --> AuthService
    MailSessionProvider --> AccountRepository
    MailSessionProvider ..> InboxSession
    MailSessionProvider ..> SmtpSession
    InboxSession <|.. ImapInbox
    SmtpSession <|.. SmtpTransport
    ImapInbox ..> HybridMime
```

`ImapInbox` y `SmtpTransport` son clases anidadas de `MailSessionProvider`. `Outcome` pertenece a `SmtpSession` y distingue ACCEPTED, REJECTED y UNKNOWN. La conexión IMAP se presta exclusivamente y se conserva hasta 30 s de inactividad; sus temporizadores llevan generación para evitar cierres de un préstamo posterior. SMTP usa STARTTLS y OAuth; IMAP usa TLS, READ_ONLY y PEEK.

## Preparación del correo

```mermaid
classDiagram
    class MailPresentationService {
        +prepareWithoutImages(MailContent content, Deadline deadline) MailPresentation
        +prepare(MailContent content, Deadline deadline) MailPresentation
        +prepare(content, deadline, progress) MailPresentation
        +close() void
    }
    class ImageProxyClient {
        +configured() boolean
        +download(String url, Deadline deadline) String
    }
    MailPresentationService --> ImageProxyClient
    MailPresentationService ..> HtmlMail
    MailPresentationService ..> MailPresentation
    ImageProxyClient ..> InlineImages
```

`MailPresentationService` prepara un primer cuerpo sin imágenes externas y después resuelve recursos por un grupo reutilizable de tres trabajadores. Publica avances por finalización; `ImageProxyClient` es el único descargador de imágenes externas del mensaje. No existe precarga persistente de esas imágenes.

## Estados duraderos del envío

```mermaid
stateDiagram-v2
    [*] --> PENDING : preparar intento
    PENDING --> SENDING : autenticar y reclamar
    SENDING --> ACCEPTED : servidor acepta y se persiste
    SENDING --> FAILED : rechazo confirmado
    SENDING --> UNKNOWN : incertidumbre o recuperacion
    ACCEPTED --> RECORDED : registrar SENT
    RECORDED --> [*]
    FAILED --> [*]
    UNKNOWN --> [*]
```

La aceptación significa que el servidor SMTP aceptó el envío, no que llegó a la bandeja del destinatario. Si falla la persistencia tras SMTP, la fila puede quedar SENDING aunque el resultado devuelto sea UNKNOWN. El diagrama muestra estados guardados; [envío y recuperación](../envio-y-recuperacion.md) explica esa diferencia y los reintentos.

Fuente: [paquete service](../../../src/main/java/com/juanpablo/evermail/service).
