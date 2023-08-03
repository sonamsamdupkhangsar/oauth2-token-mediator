package me.sonam.auth.repo.entity;

import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Oauth Client clientId and secret
 */
public class Client {
    @Id
    private String clientId;
    private String clientSecret;

    public Client() {

    }

    public Client(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getClientId() {
        return this.clientId;
    }
    public String getClientSecret() {
        return this.clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
