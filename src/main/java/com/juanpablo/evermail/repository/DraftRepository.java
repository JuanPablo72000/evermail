package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.DraftAddressDAO;
import com.juanpablo.evermail.dao.DraftDAO;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Draft;
import com.juanpablo.evermail.model.DraftAddress;
import com.juanpablo.evermail.util.SecurityUtil;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Combines DraftDAO and DraftAddressDAO into a single domain-aggregate view
 * of a Draft, and owns all encryption orchestration around bodyPlainText
 * (see uml-dao.md, note 4).
 * <p>
 * Always uses {@code SecurityUtil.retrieveAesKey} — never generates a key —
 * since by the time any Draft is saved, its Account already exists and its
 * key was generated once in {@link AccountRepository#create}.
 */
public class DraftRepository {

    private final DraftDAO draftDAO;
    private final DraftAddressDAO draftAddressDAO;
    private final SecurityUtil securityUtil;

    public DraftRepository(DraftDAO draftDAO, DraftAddressDAO draftAddressDAO, SecurityUtil securityUtil) {
        this.draftDAO = draftDAO;
        this.draftAddressDAO = draftAddressDAO;
        this.securityUtil = securityUtil;
    }

    /**
     * Returns the draft with bodyPlainText already decrypted, or null if no
     * draft exists with that id.
     */
    public Draft getById(int idDraft) throws DatabaseException, CryptoException {
        Draft draft = draftDAO.findById(idDraft);
        if (draft == null) {
            return null;
        }
        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(draft.getIdAccount()));
        return decryptBody(draft, key);
    }

    /**
     * Returns every draft for the account (already decrypted), most recently
     * edited first. The account's key is resolved once and reused across
     * every draft in the result — a single keyring access instead of one per
     * draft.
     */
    public List<Draft> getByAccount(Account account) throws DatabaseException, CryptoException {
        List<Draft> drafts = draftDAO.findByAccount(account.getIdAccount());
        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(account.getIdAccount()));

        List<Draft> decrypted = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            decrypted.add(decryptBody(draft, key));
        }
        return decrypted;
    }

    /**
     * Creates or updates a draft, deciding by {@code draft.idDraft}: null
     * means insert, non-null means update — same convention used by
     * {@link AccountRepository#save}.
     * <p>
     * {@code recipients} is not a field on the Draft model, so it travels as
     * a separate parameter. On insert, it is written as-is. On update, the
     * existing Draft_Address rows for this draft are replaced wholesale
     * (delete + insertBatch) rather than diffed, since a compose screen
     * always submits the full current recipient list, not a delta.
     *
     * @param recipients may be empty but not null.
     */
    public Draft save(Draft draft, List<DraftAddress> recipients) throws DatabaseException, CryptoException {
        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(draft.getIdAccount()));

        String plainBodyText = draft.getBodyPlainText();
        draft.setBodyPlainText(plainBodyText != null ? securityUtil.encrypt(plainBodyText, key) : null);

        if (draft.getIdDraft() == null) {
            int idDraft = draftDAO.insert(draft);
            draft.setIdDraft(idDraft);

            for (DraftAddress recipient : recipients) {
                recipient.setIdDraft(idDraft);
            }
            draftAddressDAO.insertBatch(recipients);

        } else {
            draftDAO.update(draft);

            draftAddressDAO.delete(draft.getIdDraft());
            for (DraftAddress recipient : recipients) {
                recipient.setIdDraft(draft.getIdDraft());
            }
            draftAddressDAO.insertBatch(recipients);
        }

        draft.setBodyPlainText(plainBodyText);
        return draft;
    }

    /**
     * Deletes the draft. The schema cascades this to its Draft_Address rows
     * (ON DELETE CASCADE), so no manual orchestration is needed here. Used
     * both by the user-triggered "Discard" action and, indirectly, by
     * MailSendService as part of a successful send (see uml-service.md,
     * note 3) — both paths share this same implementation.
     */
    public void delete(int idDraft) throws DatabaseException {
        draftDAO.delete(idDraft);
    }

    private Draft decryptBody(Draft draft, SecretKey key) throws CryptoException {
        if (draft.getBodyPlainText() != null) {
            draft.setBodyPlainText(securityUtil.decrypt(draft.getBodyPlainText(), key));
        }
        return draft;
    }
}