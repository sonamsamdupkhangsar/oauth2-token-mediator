package me.sonam.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * Set AccountService methods route for checking active and to actiate acccount
 */
@Configuration
public class Router {
    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    @Bean
    public RouterFunction<ServerResponse> route(OauthFlowHandler handler) {
        LOG.info("building authenticate router function");
        return RouterFunctions.route(GET("/oauth2-token-mediator/authorize").and(accept(MediaType.APPLICATION_JSON)),
                handler::initateOauthFlow)
                .andRoute(POST("/oauth2-token-mediator/token").and(accept(MediaType.APPLICATION_JSON)),
                        handler::getAccessToken)
                .andRoute(PUT("/oauth2-token-mediator/clients").and(accept(MediaType.APPLICATION_JSON)),
                        handler::saveClient)
                .andRoute(DELETE("/oauth2-token-mediator/clients/{clientId}").and(accept(MediaType.APPLICATION_JSON)),
                        handler::deleteClient)
                .andRoute(GET("/oauth2-token-mediator/clients/{clientId}").and(accept(MediaType.APPLICATION_JSON)),
                        handler::getClient);
    }
}
