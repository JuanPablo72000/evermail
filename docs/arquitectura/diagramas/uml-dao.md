# DAO y filas de persistencia

Referencia de la implementación revisada el 17 de octubre de 2026. Se muestran responsabilidades y contratos seleccionados; no todos los miembros privados ni las sobrecargas. Las clases externas al paquete se incluyen solo para explicar dependencias.

```mermaid
classDiagram
    class AccountDAO {
        +find(Connection c, UUID id) Row
        +findByIdentity(Connection c, Identity identity) Row
        +list(Connection c) List
        +insert(Connection c, Row row) void
        +update(Connection c, Row row) void
    }
    class MailDAO {
        +find(Connection c, UUID accountId, UUID id) Row
        +findRemote(Connection c, UUID accountId, RemoteMailId remote) Row
        +page(c, accountId, validity, before, lower, size) List
        +insert(Connection c, Row row) void
    }
    class InboxStateDAO {
        +find(Connection c, UUID id) Row
        +save(Connection c, UUID id, Row row) void
    }
    class OutboxDAO {
        +find(Connection c, UUID accountId, UUID id) Row
        +insert(Connection c, Row row) void
        +transition(c, accountId, id, expected, next, error) boolean
        +recoverable(Connection c, UUID accountId) List
    }
    class RecipientDAO {
        +find(Connection c, UUID id, boolean outbox) List
        +replace(Connection c, UUID id, List recipients, boolean outbox) void
    }
    class Sql {
        +update(Connection c, String sql, Object[] arguments) int
        +list(Connection c, String sql, Mapper mapper, Object[] arguments) List
        +one(Connection c, String sql, Mapper mapper, Object[] arguments) Object
    }
    AccountDAO ..> Sql
    MailDAO ..> Sql
    InboxStateDAO ..> Sql
    OutboxDAO ..> Sql
    RecipientDAO ..> Sql
    Sql ..> Connection
    AccountDAO ..> AccountRow
    MailDAO ..> MailRow
    OutboxDAO ..> OutboxRow
    InboxStateDAO ..> InboxStateRow
```

## Cómo leer los tipos

`AccountRow`, `MailRow`, `OutboxRow` e `InboxStateRow` son alias visuales de las clases anidadas `AccountDAO.Row`, `MailDAO.Row`, `OutboxDAO.Row` e `InboxStateDAO.Row`; no son archivos independientes. `List`, `Object` y `Object[]` abrevian genéricos y varargs para mantener legible el diagrama. `Sql.Mapper` convierte cada fila de JDBC.

Los DAO reciben una `Connection` ya coordinada: no deciden cuándo confirmar, revertir ni cerrar. Esto permite guardar una página, sus destinatarios y su cobertura dentro de la misma transacción. `RecipientDAO` atiende ambas tablas de destinatarios mediante el parámetro `outbox`; no hay dos DAO separados.

`MailDAO.Row` combina encabezado, cuerpo cifrado y formato de cuerpo. Los repositorios realizan parte de las actualizaciones directamente con `Sql`; el DAO no concentra todo el SQL del proyecto. `Sql` parametriza valores; la elección de tablas en `RecipientDAO` procede del booleano, no de texto introducido por el usuario.

La infraestructura de conexión y transacciones se muestra en [repositorios](uml-repositories.md). El [ER](../../datos/diagramas/er-diagram.md) describe tablas, no objetos Java.

Fuente: [paquete dao](../../../src/main/java/com/juanpablo/evermail/dao).
