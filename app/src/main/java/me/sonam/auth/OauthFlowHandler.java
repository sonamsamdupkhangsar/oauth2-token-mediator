package me.sonam.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

@Service
public class OauthFlowHandler {
    private static final Logger LOG = LoggerFactory.getLogger(OauthFlowHandler.class);

    @Value("${issuerUrl}")
    private String issuerUrl;

    @Value("${authorizationEndpoint}")
    private String authorizationEndpoint;

    @Value("${tokenEndpoint}")
    private String tokenEndpoint;

    private WebClient.Builder webClientBuilder;

    public OauthFlowHandler(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<ServerResponse> initateOauthFlow(ServerRequest serverRequest) {

        final String scopes = serverRequest.queryParams().getFirst("scope");
        final String clientId = serverRequest.queryParams().getFirst("client_id");
        final String redirectUri = serverRequest.queryParams().getFirst("redirect_uri");

        StringBuilder stringBuilder = new StringBuilder(authorizationEndpoint)
                .append("?response_type=code&client_id=").append(clientId);
        stringBuilder.append("&redirect_uri=").append(redirectUri);

        if (scopes != null && !scopes.isEmpty()){
            stringBuilder.append("&scope=").append(scopes);
        }

        URI uri = UriComponentsBuilder.fromUriString(stringBuilder.toString()).build().encode().toUri();
        LOG.info("redirect user to {}", uri);

        return ServerResponse.temporaryRedirect(uri)
                .bodyValue("redirecting to "+ stringBuilder);
    }

    public Mono<ServerResponse> accessToken(ServerRequest serverRequest) {
        LOG.info("get access token (refresh token) with code: {}", serverRequest.pathVariable("code"));

        final String redirectUri = serverRequest.pathVariable("redirect_uri");

        StringBuilder stringBuilder = new StringBuilder(tokenEndpoint).append("?grant_type=").append("authorization_code")
                .append("redirect_uri=").append(redirectUri)
                .append("&code=").append(serverRequest.pathVariable("code"));

        final String scopes = serverRequest.pathVariable("scopes");

        if (scopes != null && !scopes.isEmpty()){
            stringBuilder.append("&scope").append(scopes);
        }

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(stringBuilder.toString()).retrieve();
        return ServerResponse.ok().bodyValue(responseSpec.bodyToMono(String.class));
    }
}
