package me.sonam.auth;

import me.sonam.auth.repo.entity.Client;
import me.sonam.auth.service.TokenMediatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class OauthFlowHandler {
    private static final Logger LOG = LoggerFactory.getLogger(OauthFlowHandler.class);

    @Autowired
    private TokenMediatorService tokenMediatorService;

    public OauthFlowHandler() {
    }

    public Mono<ServerResponse> initateOauthFlow(ServerRequest serverRequest) {
        LOG.info("initialize oauth authorize flow");
        final String scopes = serverRequest.queryParams().getFirst("scope");
        final String clientId = serverRequest.queryParams().getFirst("client_id");
        final String redirectUri = serverRequest.queryParams().getFirst("redirect_uri");
        final String state = serverRequest.queryParams().getFirst("state");

        return tokenMediatorService.initiateAuthorizationFlow(clientId, redirectUri, state, scopes)
                .flatMap(uri -> ServerResponse.temporaryRedirect(uri).bodyValue("redirecting to "+ uri));
    }

    public Mono<ServerResponse> getAccessToken(ServerRequest serverRequest) {
        LOG.info("get access token (refresh token) with code");

        if (serverRequest.queryParams().getFirst("grant_type").equals("refresh_token")) {
            LOG.info("grant_type is refresh token");
            return tokenMediatorService.getRefreshToken(serverRequest.queryParams().getFirst("grant_type"),
                    serverRequest.queryParams().getFirst("refresh_token"),
                    serverRequest.queryParams().getFirst("client_id"))
                    .flatMap(token -> ServerResponse.ok().bodyValue(token))
                    .onErrorResume(throwable -> {
                        LOG.error("failed to get access tokens", throwable);
                        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(getMap(Pair.of("error", throwable.getMessage())));
                    });
        }

        LOG.info("check server query params: {}", serverRequest.queryParams());


        return tokenMediatorService.getAccessToken(
                serverRequest.queryParams().getFirst("client_id"),
                serverRequest.queryParams().getFirst("redirect_uri"),
                serverRequest.queryParams().getFirst("grant_type"),
                serverRequest.queryParams().getFirst("code"),
                serverRequest.queryParams().getFirst("scope"))
                .flatMap(token -> ServerResponse.ok().bodyValue(token))
                .onErrorResume(throwable -> {
                    LOG.error("failed to get access tokens", throwable);
                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(getMap(Pair.of("error", throwable.getMessage())));
                });
    }

    public Mono<ServerResponse> saveClient(ServerRequest serverRequest) {
        LOG.info("save client");

        return serverRequest.bodyToMono(Client.class)
                .flatMap(client -> tokenMediatorService.saveClient(client))
                        .flatMap(count -> {
                            LOG.info("saved client in token mediator");
                           return ServerResponse.ok().bodyValue(
                                    getMap(Pair.of("message", "saved clientId in token-mediator with count: " + count)));
                        })
                                .onErrorResume( throwable -> {
                                    LOG.error("failed to get save client", throwable);
                                    return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(getMap(Pair.of("error", throwable.getMessage())));
                                }
        );
    }

    public Mono<ServerResponse> deleteClient(ServerRequest serverRequest) {
        LOG.info("delete client");
        return tokenMediatorService.deleteClient(serverRequest.pathVariable("clientId"))
                .flatMap(string -> {
                    LOG.info("sending response after client deletion");
                    return ServerResponse.ok().bodyValue(
                            getMap(
                                    Pair.of("message", "deleted clientId in token-mediator: " +
                                            serverRequest.pathVariable("clientId"))));
                }).onErrorResume( throwable -> {
                                        LOG.error("failed to get delete client", throwable);
                                        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(getMap(Pair.of("error", throwable.getMessage())));
                                    }
                            );
                }


    public Mono<ServerResponse> getClient(ServerRequest serverRequest) {
        LOG.info("get client");
        return tokenMediatorService.getClient(serverRequest.pathVariable("clientId"))
                .flatMap(client -> {
                    LOG.info("get client by clientId");
                    return ServerResponse.ok().bodyValue(client);
                })
                .onErrorResume(throwable -> ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(getMap(Pair.of("error", throwable.getMessage()))));
    }

    @SafeVarargs
    public static Map<String, String> getMap(Pair<String, String>... pairs){

        Map<String, String> map = new HashMap<>();

        for(Pair<String, String> pair: pairs) {
            map.put(pair.getFirst(), pair.getSecond());
        }
        return map;

    }
}
