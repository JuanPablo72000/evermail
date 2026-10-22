package com.juanpablo.evermail.service;

import com.juanpablo.evermail.exception.CryptoException;
import javax.crypto.SecretKey;

public interface KeyStoreService {
    void create(String keyRef) throws CryptoException;
    SecretKey read(String keyRef) throws CryptoException;
    void delete(String keyRef) throws CryptoException;
}
