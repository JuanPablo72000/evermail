package com.juanpablo.evermail.repository;

import com.juanpablo.evermail.dao.AccountDAO;
import com.juanpablo.evermail.dao.AppProfileDAO;
import com.juanpablo.evermail.dao.EmailAddressDAO;
import com.juanpablo.evermail.exception.CryptoException;
import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.AppProfile;
import com.juanpablo.evermail.model.EmailAddress;
import com.juanpablo.evermail.util.SecurityUtil;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Combines {@link AccountDAO}, {@link AppProfileDAO}, and {@link EmailAddressDAO}
 * into a single domain-aggregate view of an Account, and owns all encryption
 * orchestration around it: no DAO in this aggregate ever sees a SecretKey or
 * an unencrypted token (see uml-dao.md, note 4).
 * <p>
 * The AES key resolved/generated here is stable for the account's entire
 * lifetime — it is created exactly once in {@link #create}, never rotated
 * automatically, because it is the same key MailRepository/DraftRepository
 * use to encrypt every Mail/Draft body belonging to this account. Rotating
 * it on every token refresh would make previously-synced mail content
 * permanently undecryptable.
 */
public class AccountRepository {

    private final AccountDAO accountDAO;
    private final AppProfileDAO appProfileDAO;
    private final EmailAddressDAO emailAddressDAO;
    private final SecurityUtil securityUtil;

    public AccountRepository(AccountDAO accountDAO,
                             AppProfileDAO appProfileDAO,
                             EmailAddressDAO emailAddressDAO,
                             SecurityUtil securityUtil) {
        this.accountDAO = accountDAO;
        this.appProfileDAO = appProfileDAO;
        this.emailAddressDAO = emailAddressDAO;
        this.securityUtil = securityUtil;
    }

    /**
     * Returns the account with its accessToken/refreshToken already decrypted,
     * or null if no account exists with that id.
     */
    public Account getById(int idAccount) throws DatabaseException, CryptoException {
        Account account = accountDAO.findById(idAccount);
        if (account == null) {
            return null;
        }
        return decryptTokens(account);
    }

    /**
     * Returns every account with its tokens already decrypted. Each account
     * resolves its own key independently, since every account has its own
     * dedicated SecretKey in the OS keyring.
     */
    public List<Account> getAll() throws DatabaseException, CryptoException {
        List<Account> accounts = accountDAO.findAll();
        List<Account> decrypted = new ArrayList<>(accounts.size());
        for (Account account : accounts) {
            decrypted.add(decryptTokens(account));
        }
        return decrypted;
    }

    /**
     * Creates a brand-new account (first login for this email address). Resolves
     * id_profile/id_address first, since both are NOT NULL foreign keys in the
     * schema, generates a fresh SecretKey for this account, encrypts the tokens,
     * inserts the Account row, and only then stores the key in the OS keyring
     * (keyed by the now-known idAccount).
     * <p>
     * {@code profile} may already have an idProfile (an existing profile is being
     * reused for a second linked account) or be a new one to insert.
     * {@code emailAddress} is looked up by email first to reuse an existing row
     * (email is UNIQUE), and only inserted if not found.
     *
     * @return the same Account instance, now populated with idAccount,
     *         idProfile, idAddress, and its tokens left in plain text.
     */
    public Account create(Account account, AppProfile profile, EmailAddress emailAddress)
            throws DatabaseException, CryptoException {

        Integer idProfile = profile.getIdProfile();
        if (idProfile == null) {
            idProfile = appProfileDAO.insert(profile);
            profile.setIdProfile(idProfile);
        }

        EmailAddress existingAddress = emailAddressDAO.findByEmail(emailAddress.getEmail());
        Integer idAddress;
        if (existingAddress != null) {
            idAddress = existingAddress.getIdAddress();
            emailAddress.setIdAddress(idAddress);
        } else {
            idAddress = emailAddressDAO.insert(emailAddress);
            emailAddress.setIdAddress(idAddress);
        }

        account.setIdProfile(idProfile);
        account.setIdAddress(idAddress);

        String plainAccessToken = account.getAccessToken();
        String plainRefreshToken = account.getRefreshToken();

        SecretKey key = securityUtil.generateAesKey();
        account.setAccessToken(securityUtil.encrypt(plainAccessToken, key));
        account.setRefreshToken(securityUtil.encrypt(plainRefreshToken, key));

        int idAccount = accountDAO.insert(account);
        account.setIdAccount(idAccount);

        securityUtil.storeAesKey(String.valueOf(idAccount), key);

        account.setAccessToken(plainAccessToken);
        account.setRefreshToken(plainRefreshToken);

        return account;
    }

    /**
     * Updates an existing account (e.g. a token refresh, or an edited signature).
     * Reuses the account's existing SecretKey via retrieveAesKey — never
     * generates a new one. Requires account.idAccount, idProfile, and idAddress
     * to already be set; this method does not create AppProfile/EmailAddress
     * rows (use {@link #create} for that).
     */
    public void save(Account account) throws DatabaseException, CryptoException {
        if (account.getIdAccount() == null) {
            throw new IllegalArgumentException(
                    "Account.idAccount must be set to update an existing account; use create() for a new account.");
        }

        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(account.getIdAccount()));

        String plainAccessToken = account.getAccessToken();
        String plainRefreshToken = account.getRefreshToken();

        account.setAccessToken(securityUtil.encrypt(plainAccessToken, key));
        account.setRefreshToken(securityUtil.encrypt(plainRefreshToken, key));

        accountDAO.update(account);

        account.setAccessToken(plainAccessToken);
        account.setRefreshToken(plainRefreshToken);
    }

    /**
     * Deletes the account. The schema cascades this to every Mail, Draft, and
     * Label belonging to it (ON DELETE CASCADE), so no manual orchestration is
     * needed here. The linked AppProfile/EmailAddress rows are deliberately
     * left untouched (ON DELETE RESTRICT in the schema already prevents
     * deleting an EmailAddress or AppProfile still referenced elsewhere) —
     * cleaning up now-orphaned profile/address rows is out of scope for the
     * MVP and left as a documented TODO.
     * <p>
     * Note: this does not remove the account's SecretKey from the OS keyring.
     * SecurityUtil currently has no deleteAesKey method — also a documented TODO.
     */
    public void delete(int idAccount) throws DatabaseException {
        accountDAO.delete(idAccount);
    }

    private Account decryptTokens(Account account) throws CryptoException {
        SecretKey key = securityUtil.retrieveAesKey(String.valueOf(account.getIdAccount()));
        account.setAccessToken(securityUtil.decrypt(account.getAccessToken(), key));
        account.setRefreshToken(securityUtil.decrypt(account.getRefreshToken(), key));
        return account;
    }
}