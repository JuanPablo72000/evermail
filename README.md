# Evermail

A native desktop email client built with JavaFX, supporting IMAP/SMTP protocols with OAuth 2.0 authentication for Google and Microsoft accounts.

## Overview

Evermail is a **client-side only** email application — there is no intermediary server. All processing and storage happen locally on the user's machine, using SQLite as a local cache for emails and contacts.

## Features (MVP)

- **OAuth 2.0 Login** — Sign in with an existing Google or Microsoft account (no plaintext passwords).
- **Inbox View** — Browse the last 50 received or sent emails, displaying plain text only (HTML tags and images are stripped from the body preview).
- **Plain Text Composition** — Send plain text emails through a secure SMTP connection, with recipient address validation before enabling the send button.

## Tech Stack

- **Language:** Java
- **GUI:** JavaFX (FXML + CSS)
- **Build Tool:** Gradle (Kotlin DSL)
- **Local Database:** SQLite
- **Mail Protocols:** Jakarta Mail (IMAP/SMTP)
- **Authentication:** OAuth 2.0 (Google & Microsoft)
- **Environment Config:** dotenv-java
- **Credential Storage:** OS-native keyring (Windows Credential Manager / macOS Keychain / Linux Secret Service) via java-keyring
- **Boilerplate Reduction:** Lombok

## Architecture

The project follows a layered architecture with a strict one-way dependency chain:

controller → facade → service → repository → dao

Each layer has a single, well-defined responsibility:

- **`controller`** — JavaFX controllers bound 1:1 to FXML views; handles UI events only.
- **`facade`** — Wraps synchronous service calls into JavaFX `Task<T>` objects to keep the UI thread responsive.
- **`service`** — Business logic layer, protocol-aware but unaware of JavaFX.
- **`repository`** — Domain-level aggregation of one or more DAOs.
- **`dao`** — Direct access to a single SQLite table, no domain logic.
- **`util`** — Stateless, protocol-agnostic helper classes shared across layers.
- **`config`** — Application-wide configuration and constants.
- **`exception`** — Custom checked and unchecked exceptions.

## Project Status

🚧 **In active development** — currently in the architecture and design phase.

- [x] MVP definition and user stories
- [x] Entity-Relationship model
- [x] Figma wireframes
- [x] Full UML class diagrams (exception, util, config, model, service, repository, dao, controller, facade)
- [ ] Gradle project setup
- [ ] Core implementation

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Author

**Juan Pablo Rodríguez Hurtado**
Computer Systems Engineering