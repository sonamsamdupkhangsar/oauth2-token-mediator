package me.sonam.auth;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;

public class EncryptTest {
    private static final Logger LOG = LoggerFactory.getLogger(EncryptTest.class);

    @Test
    public void encryptAndDecrypt() {

        String salt = KeyGenerators.string().generateKey();
        final String password = "mypassword";

        LOG.info("salt: {}, password: {}", salt, password);
        TextEncryptor textEncryptor = Encryptors.text(password, salt);
        String encryptedText = textEncryptor.encrypt("hello, hello");
        LOG.info("ecnrypted text is {}", encryptedText);

        String decryptedText = textEncryptor.decrypt(encryptedText);
        LOG.info("decrypted text is {}", decryptedText);

    }
}
