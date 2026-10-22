package com.juanpablo.evermail.dao;

import com.juanpablo.evermail.model.*;
import java.sql.*;
import java.util.*;

public class RecipientDAO {
    public List<Recipient> find(Connection c, UUID id, boolean outbox) throws SQLException {
        String table = outbox ? "outbox_recipient" : "mail_recipient";
        String column = outbox ? "id_outbox" : "id_mail";
        return Sql.list(c, "SELECT * FROM " + table + " WHERE " + column + "=? ORDER BY recipient_type,address_key",
                rs -> new Recipient(rs.getString("email"), rs.getString("address_key"),
                        outbox ? null : rs.getString("display_name"), RecipientType.valueOf(rs.getString("recipient_type"))), id);
    }

    public void replace(Connection c, UUID id, List<Recipient> recipients, boolean outbox) throws SQLException {
        String table = outbox ? "outbox_recipient" : "mail_recipient";
        String column = outbox ? "id_outbox" : "id_mail";
        Sql.update(c, "DELETE FROM " + table + " WHERE " + column + "=?", id);
        for (Recipient recipient : recipients) {
            if (outbox) {
                Sql.update(c, "INSERT INTO outbox_recipient VALUES(?,?,?,?)", id, recipient.getAddressKey(),
                        recipient.getEmail(), recipient.getType());
            } else {
                Sql.update(c, "INSERT INTO mail_recipient VALUES(?,?,?,?,?)", id, recipient.getAddressKey(),
                        recipient.getEmail(), recipient.getDisplayName(), recipient.getType());
            }
        }
    }
}
