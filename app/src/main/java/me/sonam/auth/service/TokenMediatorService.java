package me.sonam.auth.service;

import me.sonam.auth.repo.ClientRepository;
import me.sonam.auth.repo.entity.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
@Service
public class TokenMediatorService {
    private static final Logger LOG = LoggerFactory.getLogger(TokenMediatorService.class);

    @Value("${authorization.root}${authorization.authorize}")
    private String authorizationEndpoint;

    @Value("${authorization.root}${authorization.token}")
    private String tokenEndpoint;

    @Value("${password}")
    private String password;

    @Autowired
    private ClientRepository clientRepository;

    private TextEncryptor textEncryptor;
    private WebClient.Builder webClientBuilder;

    public TokenMediatorService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<URI> initiateAuthorizationFlow(String clientId, String redirectUri, String state, String scopes) {
        StringBuilder stringBuilder = new StringBuilder(authorizationEndpoint)
                .append("?response_type=code&client_id=").append(clientId);
        stringBuilder.append("&redirect_uri=").append(redirectUri).append("&state=").append(state);


        if (scopes != null && !scopes.isEmpty()){
            stringBuilder.append("&scope=").append(scopes);
        }

        URI uri = UriComponentsBuilder.fromUriString(stringBuilder.toString()).build().encode().toUri();
        LOG.info("redirect user to {}", uri);
        return Mono.just(uri);
    }

    public Mono<String> getAccessToken(String clientId, String redirectUri, String grantType, String code, String scope) {
        LOG.info("building request with clientId: {}", clientId);

        StringBuilder stringBuilder = new StringBuilder(tokenEndpoint).append("?grant_type=")
                .append(grantType)
                .append("&redirect_uri=").append(redirectUri)
                .append("&code=").append(code);

        if (scope != null && !scope.isEmpty()){
            stringBuilder.append("&scope=").append(scope);
        }

        LOG.info("requesting token to endpoint: {}", stringBuilder);
        return getToken(stringBuilder.toString(), clientId);
    }

    public Mono<String> getRefreshToken(String grantType, String refreshToken, String clientId) {
        LOG.info("refresh token");

        StringBuilder stringBuilder = new StringBuilder(tokenEndpoint).append("?grant_type=")
                .append(grantType)
                .append("&refresh_token=").append(refreshToken);


        return getToken(stringBuilder.toString(), clientId);
    }

    private Mono<String> getToken(String serviceEndpoint, String clientId) {
        LOG.info("calling token endpoint with serviceEndpoint: {}, clientId: {}",
                serviceEndpoint, clientId);

        return clientRepository.findById(clientId)
                .switchIfEmpty(Mono.error(new RuntimeException("No client with clientId")))
                .flatMap(client -> client.decryptClientSecret(password))
                .flatMap(decryptedClientSecret -> {
                    StringBuilder clientSecret = new StringBuilder(clientId);

                    clientSecret.append(":").append(decryptedClientSecret);
                    Base64.Encoder encoder = Base64.getEncoder();
                    String clientSecretb64 = encoder.encodeToString(clientSecret.toString().getBytes(StandardCharsets.UTF_8));
                    LOG.info("encoded clientSecretb64: {}", clientSecretb64);
                    return Mono.just(clientSecretb64);
                })
                .flatMap(clientSecretb64 ->webClientBuilder.build().post()
                .uri(serviceEndpoint)
                .headers(httpHeaders -> httpHeaders.setBasicAuth(clientSecretb64))
                .retrieve().bodyToMono(String.class));
    }

    public Mono<Integer> saveClient(Client userClientCopy) {
        LOG.info("save client id and secret");

        return clientRepository.findById(userClientCopy.getClientId())
                .switchIfEmpty(Mono.just(new Client(userClientCopy.getClientId(), userClientCopy.getClientSecret())))
                .map(client1 -> {client1.setClientRepository(clientRepository); return client1;})
                .map(client1 -> {
                    if (userClientCopy.getClientSecret().startsWith("{")) {
                        int indexOf = userClientCopy.getClientSecret().indexOf("}");
                        final String secretWithoutEncoder = userClientCopy.getClientSecret()
                                .substring(indexOf+1, userClientCopy.getClientSecret().length());

                        client1.setClientSecret(secretWithoutEncoder);
                        LOG.info("client secret without encoder: '{}'", secretWithoutEncoder);
                    }
                    return client1;
                })
                .flatMap(client1 -> {LOG.info("save client"); return client1.save(password);})
                .flatMap(client1 -> clientRepository.countByClientId(client1.getClientId()));
    }

    public Mono<String> deleteClient(String clientId) {
        LOG.info("delete clientId: {}", clientId);
        return clientRepository.deleteById(clientId).thenReturn(clientId);
    }
    public Mono<Client> getClient(String clientId) {
        LOG.info("get client by clientId");
        return clientRepository.findById(clientId).switchIfEmpty(
                Mono.error(new RuntimeException("No client with clientId: "+ clientId)))
                .flatMap(client ->


                    client.decryptClientSecret(password)
                            .map(decryptedSecret -> {
                                client.setClientSecret(decryptedSecret);
                                LOG.info("decrypt client secret before returing to user: decrypted: {}, actual: {}",
                                        decryptedSecret, client.getClientSecret());
                                return client;
                            }));
    }
}
