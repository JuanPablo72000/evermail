```mermaid
classDiagram
    class ErrorCode {
        <<Enumeration>>
        OAUTH_TOKEN_EXPIRED
        OAUTH_CONNECTION_FAILED
        OAUTH_INVALID_CREDENTIALS
        SMTP_CONNECTION_FAILED
        SMTP_SEND_REJECTED
        IMAP_CONNECTION_FAILED
        IMAP_FOLDER_NOT_FOUND
        IMAP_FETCH_FAILED
        DB_CONNECTION_FAILED
        DB_QUERY_FAILED
        ATTACHMENT_NOT_FOUND
        ATTACHMENT_TOO_LARGE
        CRYPTO_OPERATION_FAILED
    }

    class EvermailException {
        <<Abstract>>
        -ErrorCode errorCode
        +getErrorCode() ErrorCode
    }

    class EvermailRuntimeException {
        <<Abstract>>
        -String fieldName
        +getFieldName() String
    }

    class OAuthAuthenticationException {
        <<Leaf>>
    }

    class MailSendException {
        <<Leaf>>
    }

    class MailFetchException {
        <<Leaf>>
    }

    class DatabaseException {
        <<Leaf>>
    }

    class AttachmentException {
        <<Leaf>>
    }

    class CryptoException {
        <<Leaf>>
    }

    class InvalidFieldException {
        <<Leaf>>
    }

    class InvalidEmailAddressException {
        <<Leaf>>
    }

    class EmptyFieldException {
        <<Leaf>>
    }

%% Checked branch inheritance
    EvermailException <|-- OAuthAuthenticationException
    EvermailException <|-- MailSendException
    EvermailException <|-- MailFetchException
    EvermailException <|-- DatabaseException
    EvermailException <|-- AttachmentException
    EvermailException <|-- CryptoException
    EvermailException ..> ErrorCode : uses

%% Unchecked branch inheritance
    EvermailRuntimeException <|-- InvalidFieldException
    InvalidFieldException <|-- InvalidEmailAddressException
    InvalidFieldException <|-- EmptyFieldException
```

**Design notes:**

1. `EvermailException` extends `Exception` (checked) and `EvermailRuntimeException` extends `RuntimeException` (unchecked) — standard Java classes, not drawn as their own nodes.

2. **All leaf classes declare their own 4 standard constructors manually** (no-arg-equivalent, message, cause, message+cause), each one delegating to the corresponding `super(...)` constructor in their parent class. Lombok's `@StandardException` annotation is **not used** here: it generates constructors that call a parameterless `super()`, which does not exist in `EvermailException` or `EvermailRuntimeException` — both require `errorCode`/`fieldName` as a mandatory first argument on every constructor. Writing these 4 constructors by hand is therefore required for the project to compile, and is reflected in this diagram by the `<<Leaf>>` stereotype instead of `<<@StandardException>>`.

3. `errorCode` lives in the base class `EvermailException` and is inherited by all checked exceptions (OAuth, sending, fetching, database, attachments, crypto). Every checked leaf constructor requires an `ErrorCode` as its first parameter.

4. `fieldName` lives in `EvermailRuntimeException`, for the field-validation branch (`InvalidFieldException` and its children). It does not use `ErrorCode` because it represents internal business/validation logic, not an external protocol error — it is documented via comments/javadoc in the class instead.

5. `ErrorCode` is a single, general-purpose enum (not one per exception) to keep the code simple, grouping codes by prefix: `OAUTH_`, `SMTP_`, `IMAP_`, `DB_`, `ATTACHMENT_`, `CRYPTO_`.

6. `InvalidEmailAddressException` and `EmptyFieldException` extend `InvalidFieldException` rather than `EvermailRuntimeException` directly — their constructors delegate to `InvalidFieldException`'s constructors, which in turn delegate to `EvermailRuntimeException`'s, so `fieldName` propagates correctly through the full chain.

7. Lombok is still used across this package where compatible (e.g. `@Getter` on `EvermailException`/`EvermailRuntimeException` to generate `getErrorCode()`/`getFieldName()`), reducing boilerplate wherever the constructor requirement does not conflict with `@StandardException`'s assumptions.

8. **`CryptoException` was added as its own leaf**, separate from `OAuthAuthenticationException`. Originally, `SecurityUtil` reported every failure — key generation, keyring access, AES-GCM encrypt/decrypt — as `OAuthAuthenticationException`, inherited from when the class only handled OAuth tokens. Once `SecurityUtil`'s scope grew to cover mail bodies and attachments, that naming became misleading: a `Service` catching `OAuthAuthenticationException` to redirect the user to the login screen could end up doing so for an unrelated local encryption failure (e.g. corrupted ciphertext), sending the user to re-authenticate for a problem that has nothing to do with their session. `CryptoException` now covers every failure originating in `SecurityUtil`, regardless of whether the key being handled originated from OAuth.