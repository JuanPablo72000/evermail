```mermaid
erDiagram
    Account {
        int id_account PK
        int id_profile FK
        int id_address FK
        string signature
        string accountName
        string accessToken
        string refreshToken
        dateTime tokenExpiresAt
    }

    AppProfile {
        int id_profile PK
        string theme
        int syncIntervalMinutes
        string language
        boolean notificationsEnabled
    }

    EmailAddress {
        int id_address PK
        string email
        boolean isInternal
    }

    Mail {
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

    Draft {
        int id_draft PK
        int id_account FK
        string subject
        string bodyPlainText
        date lastEdited
    }

    Attachment {
        int id_attachment PK
        int id_mail FK
        string fileName
        string mimeType
        int sizeBytes
        string filePath
    }

    Label {
        int id_label PK
        int id_account FK
        string name
    }

    Mail_Label {
        int id_mail PK, FK
        int id_label PK, FK
        boolean isRead
    }

    Mail_Address {
        int id_mail PK, FK
        int id_address PK, FK
        string recipientType 
    }

    Draft_Address {
        int id_draft PK, FK
        int id_address PK, FK
        string recipientType
    }

    %% Relations
    AppProfile ||--o{ Account : "Manages"
    EmailAddress ||--|| Account : "Associated with"
    Account ||--o{ Label : "Has"
    Mail ||--o{ Mail_Label : "Has"
    Label ||--o{ Mail_Label : "Groups"
    Mail ||--o{ Attachment : "Contains"
    Mail ||--|{ Mail_Address : "Delivered to"
    EmailAddress ||--o{ Mail_Address : "Receives"
    Account ||--o{ Mail : "Contains" 
    EmailAddress ||--o{ Mail : "Sends" 
    Mail ||--o| Mail : "Replies to"
    Account ||--o{ Draft : "Writes"
    Draft ||--o{ Draft_Address : "Addressed to"
    EmailAddress ||--o{ Draft_Address : "Receives"
```

**Key syntax used here:**
- `<<Abstract>>`: Defines an abstract class or interface.
- `<|--`: **Inheritance.** (E.g. A Customer _is a_ User).
- `*--`: **Composition.** Life-or-death relationship (e.g. an _OrderItem_ makes no sense without its _Order_).
- `o--`: **Aggregation.** Relationship where the objects are independent (e.g. a _Product_ still exists in the store even if the _OrderItem_ is deleted).
- `-->`: **Simple association.**
- `-`, `+`, `#`: Access modifiers (Private, Public, Protected).
