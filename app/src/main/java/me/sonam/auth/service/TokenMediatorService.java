package me.sonam.auth.service;

import me.sonam.auth.repo.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

public class TokenMediatorService {
    private static final Logger LOG = LoggerFactory.getLogger(TokenMediatorService.class);
    @Value("${issuerUrl}")
    private String issuerUrl;

    @Value("${authorization.root}${authorization.path}")
    private String authorizationEndpoint;

    @Value("${token.root}${token.path}")
    private String tokenEndpoint;

    @Autowired
    private ClientRepository clientRepository;

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
}
