package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.LabelDAO;
import com.juanpablo.evermail.dao.MailLabelDAO;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Label;

import java.util.List;

/**
 * Combines LabelDAO and MailLabelDAO into a single domain-aggregate view of
 * a Label. Unlike AccountRepository/MailRepository/DraftRepository, this
 * Repository has no SecurityUtil dependency: neither Label nor Mail_Label
 * carry encrypted fields (see uml-repositories.md, note 3).
 */
public class LabelRepository {

    private final LabelDAO labelDAO;
    private final MailLabelDAO mailLabelDAO;

    public LabelRepository(LabelDAO labelDAO, MailLabelDAO mailLabelDAO) {
        this.labelDAO = labelDAO;
        this.mailLabelDAO = mailLabelDAO;
    }

    public Label getById(int idLabel) throws DatabaseException {
        return labelDAO.findById(idLabel);
    }

    public List<Label> getByAccount(Account account) throws DatabaseException {
        return labelDAO.findByAccount(account.getIdAccount());
    }

    /**
     * Counts unread mails under a label — aggregates over MailLabelDAO
     * (rows with is_read = 0) rather than living in MailLabelDAO itself,
     * since "counting unread" is a UI/business need (a badge count), not a
     * pure CRUD operation (see uml-repositories.md, note 6).
     */
    public int getUnreadCount(int idLabel) throws DatabaseException {
        return mailLabelDAO.countUnreadByLabel(idLabel);
    }

    /**
     * Creates a new label. LabelDAO exposes no update() — per the current
     * schema/DAO, labels are created and deleted, never renamed — so this
     * is always an insert, unlike AccountRepository/DraftRepository's
     * insert-or-update save().
     */
    public Label save(Label label) throws DatabaseException {
        int idLabel = labelDAO.insert(label);
        label.setIdLabel(idLabel);
        return label;
    }

    /**
     * Deletes the label. The schema cascades this to its Mail_Label rows
     * (ON DELETE CASCADE), so no manual orchestration is needed here.
     */
    public void delete(int idLabel) throws DatabaseException {
        labelDAO.delete(idLabel);
    }
}