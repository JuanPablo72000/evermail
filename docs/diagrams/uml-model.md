```mermaid
classDiagram
    class Account {
        int id_account PK
        int id_profile FK
        int id_address FK
        string signature
        string accountName
        string accessToken
        string refreshToken
        dateTime tokenExpiresAt
        OAuthProvider provider
    }

    class AppProfile {
        int id_profile PK
        string theme
        int syncIntervalMinutes
        string language
        boolean notificationsEnabled
    }

    class EmailAddress {
        int id_address PK
        string email
        boolean isInternal
    }

    class Mail {
	    int id_mail PK
	    int id_account FK
	    int id_sender_address FK
	    int id_reply_to_mail FK
	    string serverMessageId
	    string subject
	    string bodyPlainText
	    string bodyHTML
	    date dateReceived
	}

    class Draft {
        int id_draft PK
        int id_account FK
        string subject
        string bodyPlainText
        date lastEdited
    }

    class Attachment {
        int id_attachment PK
        int id_mail FK
        string fileName
        string mimeType
        int sizeBytes
        string filePath
    }

    class Label {
        int id_label PK
        int id_account FK
        string name
    }

    class Mail_Label {
        int id_mail PK, FK
        int id_label PK, FK
        boolean isRead
    }

    class Mail_Address {
        int id_mail PK, FK
        int id_address PK, FK
        string recipientType 
    }

    class Draft_Address {
        int id_draft PK, FK
        int id_address PK, FK
        string recipientType
    }

    class OAuthProvider {
        <<Enumeration>>
        GOOGLE
        MICROSOFT
        +getAuthorizationEndpoint() String
        +getTokenEndpoint() String
        +getScopes() List~String~
        +requiresClientSecret() boolean
        +getImapHost() String
        +getImapPort() int
        +getSmtpHost() String
        +getSmtpPort() int
    }

    %% Relations
    AppProfile "1" --> "*" Account : Manages
    EmailAddress "1" --> "0..1" Account : Identifies
    Account "1" --> "*" Label : Has
    Account "1" --> "*" Mail : Contains
    Account "1" --> "*" Draft : Writes
    EmailAddress "1" --> "*" Mail : Sends
    Mail "1" --> "*" MailLabel : Has
    Label "1" --> "*" MailLabel : Groups
    Mail "1" *-- "*" Attachment : Contains
    Mail "1" --> "1..*" MailAddress : Delivered to
    EmailAddress "1" --> "*" MailAddress : Receives
    Mail "*" --> "0..1" Mail : Replies to
    Draft "1" --> "*" DraftAddress : Addressed to
    EmailAddress "1" --> "*" DraftAddress : Receives
```

**Key syntax used here:**
- `<<Abstract>>`: Defines an abstract class or interface.
- `<|--`: **Inheritance.** (E.g. A Customer _is a_ User).
- `*--`: **Composition.** Life-or-death relationship (e.g. an _OrderItem_ makes no sense without its _Order_).
- `o--`: **Aggregation.** Relationship where the objects are independent (e.g. a _Product_ still exists in the store even if the _OrderItem_ is deleted).
- `-->`: **Simple association.**
- `-`, `+`, `#`: Access modifiers (Private, Public, Protected).
