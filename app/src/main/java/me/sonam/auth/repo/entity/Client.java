package me.sonam.auth.repo.entity;

import me.sonam.auth.repo.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Oauth Client clientId and secret
 */
public class Client implements Persistable<String> {
    @Transient
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    @Id
    private String clientId;
    private String clientSecret;
    private String salt;

    @Transient
    private boolean isNew;

    @Transient
    private ClientRepository clientRepository;

    public Client() {

    }

    public void setClientRepository(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Client(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.isNew = true;
    }

    @Override
    public String getId() {
        return clientId;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    public String getClientId() {
        return this.clientId;
    }
    public String getClientSecret() {
        return this.clientSecret;
    }

    public Mono<Client> save(String password) {
        LOG.info("saving client with salt generation and encrypted clientSecret");
        this.salt = KeyGenerators.string().generateKey();

        TextEncryptor textEncryptor = Encryptors.text(password, this.salt);
        this.clientSecret = textEncryptor.encrypt(this.clientSecret);
        LOG.info("encryption done");
        return clientRepository.save(this);
    }

    public Mono<String> decryptClientSecret(String password) {
        LOG.info("decrypt clientSecret with password");
        TextEncryptor textEncryptor = Encryptors.text(password, this.salt);
        var decryptedClientSecret = textEncryptor.decrypt(this.clientSecret);
        return Mono.just(decryptedClientSecret);
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public String toString() {
        return "Client{" +
                "clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", salt='" + salt + '\'' +
                ", isNew=" + isNew +
                ", clientRepository=" + clientRepository +
                '}';
    }
}
