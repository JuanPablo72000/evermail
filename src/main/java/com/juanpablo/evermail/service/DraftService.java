package com.juanpablo.evermail.service;

import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Draft;
import com.juanpablo.evermail.model.DraftAddress;
import com.juanpablo.evermail.model.EmailAddress;
import com.juanpablo.evermail.repository.AccountRepository;
import com.juanpablo.evermail.repository.DraftRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic around Drafts. A draft starts as a plain in-memory object
 * ({@link #createDraft}) and is persisted for the first time on the first
 * {@link #updateDraft} call — {@code DraftRepository.save} decides insert vs.
 * update by {@code idDraft == null}, so an empty compose window never leaves
 * a ghost row in the database if the user closes it without saving.
 *
 * <p>Deletion is exposed twice on purpose (see uml-service.md, note 3):
 * {@link #discardDraft} is the user-triggered "Discard" action, while
 * {@link #delete} is the internal cleanup invoked by MailSendService after a
 * successful send. Both share the same implementation.
 *
 * <p>All methods are synchronous and blocking; the facade layer wraps them
 * in Task<T>.
 */
public class DraftService {

    private final DraftRepository draftRepository;
    private final AccountRepository accountRepository;

    public DraftService(DraftRepository draftRepository, AccountRepository accountRepository) {
        this.draftRepository = draftRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Starts a new compose session: an in-memory Draft bound to the account,
     * stamped with today's date. Nothing is written to the database yet.
     */
    public Draft createDraft(Account account) {
        Draft draft = new Draft();
        draft.setIdAccount(account.getIdAccount());
        draft.setLastEdited(LocalDate.now());
        return draft;
    }

    /**
     * Persists the draft's current state (subject, body) and replaces its
     * recipient list wholesale — the compose screen always submits the full
     * current list, not a delta. Refreshes lastEdited on every save.
     */
    public void updateDraft(Draft draft, List<DraftAddress> recipients)
            throws DatabaseException, CryptoException {
        draft.setLastEdited(LocalDate.now());
        draftRepository.save(draft, recipients);
    }

    /**
     * Turns a raw email string typed in the compose screen into a
     * DraftAddress ready to be saved, resolving (or creating) the underlying
     * email_address row. Recipients are external by default.
     */
    public DraftAddress resolveRecipient(String email, String recipientType) throws DatabaseException {
        EmailAddress address = accountRepository.resolveOrCreateAddress(email, false);
        DraftAddress recipient = new DraftAddress();
        recipient.setIdAddress(address.getIdAddress());
        recipient.setRecipientType(recipientType);
        return recipient;
    }

    /**
     * User-triggered discard: deletes the draft without leaving a trace.
     */
    public void discardDraft(Draft draft) throws DatabaseException {
        delete(draft);
    }

    /**
     * Internal deletion used by MailSendService after a successful send.
     * A draft that was never persisted (idDraft == null) is a no-op.
     */
    public void delete(Draft draft) throws DatabaseException {
        if (draft.getIdDraft() == null) {
            return;
        }
        draftRepository.delete(draft.getIdDraft());
    }
}