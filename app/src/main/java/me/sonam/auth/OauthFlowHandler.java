package me.sonam.auth;

import me.sonam.auth.repo.ClientRepository;
import me.sonam.auth.repo.entity.Client;
import me.sonam.auth.service.TokenMediatorService;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class OauthFlowHandler {
    private static final Logger LOG = LoggerFactory.getLogger(OauthFlowHandler.class);

    @Value("${issuerUrl}")
    private String issuerUrl;

    @Value("${authorization.root}${authorization.path}")
    private String authorizationEndpoint;

    @Value("${token.root}${token.path}")
    private String tokenEndpoint;

    @Autowired
    private ClientRepository clientRepository;

    private WebClient.Builder webClientBuilder;

    @Autowired
    private TokenMediatorService tokenMediatorService;

    public OauthFlowHandler(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<ServerResponse> initateOauthFlow(ServerRequest serverRequest) {
        LOG.info("initialize oauth authorize flow");
        final String scopes = serverRequest.queryParams().getFirst("scope");
        final String clientId = serverRequest.queryParams().getFirst("client_id");
        final String redirectUri = serverRequest.queryParams().getFirst("redirect_uri");
        final String state = serverRequest.queryParams().getFirst("state");

        StringBuilder stringBuilder = new StringBuilder(authorizationEndpoint)
                .append("?response_type=code&client_id=").append(clientId);
        stringBuilder.append("&redirect_uri=").append(redirectUri).append("&state=").append(state);


        tokenMediatorService.initiateAuthorizationFlow(clientId, redirectUri, state, scopes)
                .flatMap(uri -> ServerResponse.temporaryRedirect(uri).bodyValue("redirecting to "+ uri));

        if (scopes != null && !scopes.isEmpty()){
            stringBuilder.append("&scope=").append(scopes);
        }

        URI uri = UriComponentsBuilder.fromUriString(stringBuilder.toString()).build().encode().toUri();
        LOG.info("redirect user to {}", uri);

        return ServerResponse.temporaryRedirect(uri)
                .bodyValue("redirecting to "+ stringBuilder);
    }

    public Mono<ServerResponse> getAccessToken(ServerRequest serverRequest) {
        LOG.info("get access token (refresh token) with code");

        if (serverRequest.queryParams().getFirst("grant_type").equals("refresh_token")) {
            LOG.info("grant_type is refresh token");
            return getRefreshToken(serverRequest);
        }

        LOG.info("check server query params: {}, hello header value is: {}",
                serverRequest.queryParams(), serverRequest.headers().header("hello"));

        //articles-client:secret with echo -n 'articles-client:secret' | openssl base64
        //final String base64EncodedUserPassword = "bmV4dGpzLWNsaWVudDpuZXh0anMtc2VjcmV0";

        final String redirectUri = serverRequest.queryParams().getFirst("redirect_uri");
        StringBuilder stringBuilder = new StringBuilder(tokenEndpoint).append("?grant_type=")
                .append(serverRequest.queryParams().getFirst("grant_type"))
                .append("&redirect_uri=").append(redirectUri)
                .append("&code=").append(serverRequest.queryParams().getFirst("code"));

        final String scope = serverRequest.queryParams().getFirst("scope");

        if (scope != null && !scope.isEmpty()){
            stringBuilder.append("&scope=").append(scope);
        }

        LOG.info("requesting token to endpoint: {}", stringBuilder);
        return getToken(stringBuilder.toString(), serverRequest.headers().firstHeader("client_id"));
    }

    public Mono<ServerResponse> getRefreshToken(ServerRequest serverRequest) {
        LOG.info("refresh token");
        //articles-client:secret with echo -n 'articles-client:secret' | openssl base64
        //final String base64EncodedUserPassword = "bmV4dGpzLWNsaWVudDpuZXh0anMtc2VjcmV0";

        StringBuilder stringBuilder = new StringBuilder(tokenEndpoint).append("?grant_type=")
                .append(serverRequest.queryParams().getFirst("grant_type"))
                .append("&refresh_token=").append(serverRequest.queryParams().getFirst("refresh_token"));


        return getToken(stringBuilder.toString(), serverRequest.headers().firstHeader("client_id"));
    }

    private Mono<ServerResponse> getToken(String serviceEndpoint, String clientId) {
        LOG.info("calling token endpoint with serviceEndpoint: {}, clientId: {}",
                serviceEndpoint, clientId);

        StringBuilder clientSecret = new StringBuilder(clientId);
        clientSecret.append(":").append("nextjs-secret");
        Base64.Encoder encoder = Base64.getEncoder();
        String clientSecretb64 = encoder.encodeToString(clientSecret.toString().getBytes(StandardCharsets.UTF_8));
        LOG.info("encoded clientSecretb64: {}", clientSecretb64);


        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post()
                .uri(serviceEndpoint)
                .headers(httpHeaders -> httpHeaders.setBasicAuth(clientSecretb64))
                .retrieve();
        return responseSpec.bodyToMono(String.class).flatMap(s -> {
                    LOG.info("token: {}", s);
                    return ServerResponse.ok().bodyValue(s);
                }
        ).onErrorResume(throwable -> {
            LOG.error("refresh token api call failed: {}", throwable.getMessage());
            return Mono.error(new RuntimeException("Failed to refresh token"));//errorMessage));
        });
    }

    public Mono<ServerResponse> saveClient(ServerRequest serverRequest) {
        LOG.info("save client id and secret");

        return serverRequest.bodyToMono(Client.class).flatMap(client -> {
            return clientRepository.existsById(client.getClientId()).map(aBoolean -> {
                if (aBoolean == true) {
                    return clientRepository.findById(client.getClientId()).map(client1 -> {
                        client1.setClientSecret(client.getClientSecret());
                        return client1;
                    });
                } else {
                    return clientRepository.save(client);
                }
            });
        }).
                flatMap(client ->{

                    ServerResponse.created(URI.create("/clients"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("client saved");});


        return ServerResponse.temporaryRedirect(uri)
                .bodyValue("redirecting to "+ stringBuilder);
    }

}
