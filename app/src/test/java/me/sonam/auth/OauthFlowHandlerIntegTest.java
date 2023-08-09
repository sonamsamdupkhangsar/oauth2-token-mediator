package me.sonam.auth;


import me.sonam.auth.repo.entity.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class OauthFlowHandlerIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(OauthFlowHandlerIntegTest.class);

    @Value("classpath:sign-in.html")
    private Resource resource;
    @Value("classpath:token-response.json")
    private Resource refreshTokenResource;

    private static MockWebServer mockWebServer;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    ReactiveJwtDecoder jwtDecoder;




    @BeforeAll
    static void setupMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        LOG.info("host: {}, port: {}", mockWebServer.getHostName(), mockWebServer.getPort());
    }

    @AfterAll
    public static void shutdownMockWebServer() throws IOException {
        LOG.info("shutdown and close mockWebServer");
        mockWebServer.shutdown();
        mockWebServer.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws IOException {
        r.add("issuerUrl", () -> "http://my-server:"+mockWebServer.getPort());
        r.add("authorization.root", () -> "http://localhost:"+mockWebServer.getPort());
    }

    @Test
    public void initateOauthFlow() throws Exception {
        LOG.info("reactive webclient");
        assertThat(resource.exists()).isTrue();

        final String resonseType = "code";
        final String clientId = "articles-client";
        final String scope = "articles.read articles.write";
        final String redirectUri = "http://127.0.0.1:8090/login/oauth2/code/articles-client-oidc";
        final String state = "8RtmFZMz8LZXR29ieDTMyVHChjWhmUNE0C-gI7d4E3k";

        StringBuilder stringBuilder = new StringBuilder("/authorize?response_type=")
                .append(resonseType).append("&client_id=").append(clientId).append("&scope=")
                .append(scope).append("&redirect_uri=").append(redirectUri)
                .append("&state=").append(state);

        WebTestClient.ResponseSpec responseSpec = webTestClient.get().uri(stringBuilder.toString()).
                exchange().expectStatus().isTemporaryRedirect();
        final String expectLocation = "http://localhost:"+mockWebServer.getPort()+"/oauth2/authorize?response_type=code" +
                "&client_id=articles-client&redirect_uri=http://127.0.0.1:8090/login/oauth2/code/articles-client-oidc" +
                "&state="+state+"&scope=articles.read%20articles.write";

        LOG.info("location: {}", responseSpec.expectHeader().location(expectLocation));
       // sendLocation(expectLocation);
    }

    @Test
    public void getAccessToken() throws Exception {
        String clientId = saveClient();

        LOG.info("set mock response");
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json;charset=UTF-8")
                .setResponseCode(200).setBody(refreshTokenResource.getContentAsString(StandardCharsets.UTF_8)));


        URI uri = UriComponentsBuilder.fromUriString("/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("redirect_uri", "http://127.0.0.1:8090/login/oauth2/code/articles-client-oidc")
                .queryParam("code", "GieqKyXaViGHZcfX-cwobX9SHnwXTs_nXkjCDEwFiDLp6QBNtPFKIrsPKE_Lml3opmr60O65ixXtGppZ20L51tRGpS75g7qp55OXAyoUiGvv_M4GaDhhy9g2LAymgXKn")
                .queryParam("scope", "message.read message.write")
                .build().encode().toUri();
        LOG.info("send request for access token");
        WebTestClient.ResponseSpec responseSpec = webTestClient.post().uri(uri)
                .headers(httpHeaders -> httpHeaders.add("client_id", clientId))
                .exchange().expectStatus().isOk();



        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).startsWith("/oauth2/token");

        LOG.info("response from service is {}", responseSpec.expectBody(String.class).returnResult().getResponseBody());
    }

    private String saveClient() {
        final String authenticationId = "sonam";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Client client = new Client("myClientId", "secret");
        webTestClient.put().uri("/clients")
                .headers(addJwt(jwt)).contentType(MediaType.APPLICATION_JSON).bodyValue(client)
                .exchange().expectStatus().isOk().expectBody(String.class).consumeWith(stringEntityExchangeResult -> {
                    LOG.info("found clients with clientId: {}", stringEntityExchangeResult.getResponseBody());
                });

        return client.getClientId();
    }

    @Test
    public void updateClient() {
        final String authenticationId = "sonam";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        String clientId = saveClient();

        Client client = new Client(clientId, "secret");
        LOG.info("do an update to client");
        webTestClient.put().uri("/clients")
                .headers(addJwt(jwt)).contentType(MediaType.APPLICATION_JSON).bodyValue(client)
                .exchange().expectStatus().isOk().expectBody(String.class).consumeWith(stringEntityExchangeResult -> {
                    LOG.info("found clients with clientId: {}", stringEntityExchangeResult.getResponseBody());
                });

        LOG.info("get client by clientId");

        webTestClient.get().uri("/clients/"+clientId).headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(Client.class).consumeWith(clientEntityExchangeResult -> {
                    LOG.info("found client: {}", clientEntityExchangeResult.getResponseBody());
                });
    }

    @Test
    public void deleteClient() {
        LOG.info("delete client test");
        String clientId = saveClient();

        final String authenticationId = "sonam";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        LOG.info("send request to delete client");
        webTestClient.delete().uri("/clients/"+clientId)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(String.class).consumeWith(stringEntityExchangeResult -> {
                    LOG.info("delete client response: {}", stringEntityExchangeResult.getResponseBody());
                });

        LOG.info("verify client does not exist");
        webTestClient.get().uri("/clients/"+clientId)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).consumeWith(stringEntityExchangeResult -> {
                    LOG.info("response: {}", stringEntityExchangeResult.getResponseBody().get("error"));
                });

    }


    @Test
    public void getRefreshToken() throws Exception {
        final String oldRefreshToken = "Qt85o6fATJEmq4j7MPUVD4UOw1xu-wVmKEpL55Nl8D_HSLeIVDq75GceC5nsFUE8ZdjcrT6pQFkCM" +
                "-UzrFdbYYf8Zizw9Ioxo4ilO7GHBWj30edwqCcEcwrSST_G6n-Y";

        String clientId = saveClient();
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json;charset=UTF-8")
                .setResponseCode(200).setBody(refreshTokenResource.getContentAsString(StandardCharsets.UTF_8)));


        URI uri = UriComponentsBuilder.fromUriString("/token")
                .queryParam("grant_type", "refresh_token")
                .queryParam("refresh_token", oldRefreshToken)
                .build().encode().toUri();
        WebTestClient.ResponseSpec responseSpec = webTestClient.post().uri(uri)
                .headers(httpHeaders -> httpHeaders.add("client_id", clientId))
                .exchange().expectStatus().isOk();

        LOG.info("response from service is {}", responseSpec.expectBody(String.class).returnResult().getResponseBody());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).startsWith("/oauth2/token");
    }

    private Jwt jwt(String subjectName) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }
}
