package me.sonam.auth;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
    @LocalServerPort
    private int randomPort;

    private WebClient webClient;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    public void setUp() {
        LOG.info("randomPort: {}", randomPort);
        webClient = new WebClient();
        this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(true);
        this.webClient.getOptions().setRedirectEnabled(true);
        this.webClient.getCookieManager().clearCookies();	// log out
    }

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
        r.add("token.root", () -> "http://my-server:"+mockWebServer.getPort());
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

        StringBuilder stringBuilder = new StringBuilder("/token-mediator/oauth/authorize?response_type=")
                .append(resonseType).append("&client_id=").append(clientId).append("&scope=")
                .append(scope).append("&redirect_uri=").append(redirectUri)
                .append("&state=").append(state);

        WebTestClient.ResponseSpec responseSpec = webTestClient.get().uri(stringBuilder.toString()).
                exchange().expectStatus().isTemporaryRedirect();
        final String expectLocation = "http://my-server:9001/oauth2/authorize?response_type=code" +
                "&client_id=articles-client&redirect_uri=http://127.0.0.1:8090/login/oauth2/code/articles-client-oidc" +
                "&state="+state+"&scope=articles.read%20articles.write";

        LOG.info("location: {}", responseSpec.expectHeader().location(expectLocation));
       // sendLocation(expectLocation);
    }

    @Test
    public void getAccessToken() throws Exception {
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json;charset=UTF-8")
                .setResponseCode(200).setBody(refreshTokenResource.getContentAsString(StandardCharsets.UTF_8)));


        URI uri = UriComponentsBuilder.fromUriString("/token-mediator/oauth/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("redirect_uri", "http://127.0.0.1:8090/login/oauth2/code/articles-client-oidc")
                .queryParam("code", "GieqKyXaViGHZcfX-cwobX9SHnwXTs_nXkjCDEwFiDLp6QBNtPFKIrsPKE_Lml3opmr60O65ixXtGppZ20L51tRGpS75g7qp55OXAyoUiGvv_M4GaDhhy9g2LAymgXKn")
                .queryParam("scope", "message.read message.write")
                .build().encode().toUri();
        WebTestClient.ResponseSpec responseSpec = webTestClient.post().uri(uri)
                .headers(httpHeaders -> httpHeaders.add("clientId", "nextjs-client"))
                .exchange().expectStatus().isOk();

        LOG.info("response from service is {}", responseSpec.expectBody(String.class).returnResult().getResponseBody());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).startsWith("/oauth2/token");
    }

    @Test
    public void getRefreshToken() throws Exception {
        final String oldRefreshToken = "Qt85o6fATJEmq4j7MPUVD4UOw1xu-wVmKEpL55Nl8D_HSLeIVDq75GceC5nsFUE8ZdjcrT6pQFkCM" +
                "-UzrFdbYYf8Zizw9Ioxo4ilO7GHBWj30edwqCcEcwrSST_G6n-Y";

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json;charset=UTF-8")
                .setResponseCode(200).setBody(refreshTokenResource.getContentAsString(StandardCharsets.UTF_8)));


        URI uri = UriComponentsBuilder.fromUriString("/token-mediator/oauth/token")
                .queryParam("grant_type", "refresh_token")
                .queryParam("refresh_token", oldRefreshToken)
                .build().encode().toUri();
        WebTestClient.ResponseSpec responseSpec = webTestClient.post().uri(uri)
                .headers(httpHeaders -> httpHeaders.add("client_id", "nextjs-client"))
                .exchange().expectStatus().isOk();

        LOG.info("response from service is {}", responseSpec.expectBody(String.class).returnResult().getResponseBody());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).startsWith("/oauth2/token");
    }
    private void sendLocation(String pageAddress) throws IOException {
        HtmlPage page = this.webClient.getPage(pageAddress);
        LOG.info("page: {}", page.toString());
        assertLoginPage(page);
        final String redirectUri = "http://127.0.0.1:8080/login/oauth2/code/articles-client-oidc";

    /*    WebResponse approveConsentResponse = signIn(page, "sonam", "sonam").getWebResponse();
        LOG.info("response: {}", approveConsentResponse.getContentAsString());

        assertThat(approveConsentResponse.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY.value());
        String location = approveConsentResponse.getResponseHeaderValue("location");
        assertThat(location).startsWith(redirectUri);
        assertThat(location).contains("code=");
*/
        //HtmlPage consentPage = signIn(page, "sonam", "sonam");

        /*assertThat(consentPage.getTitleText()).isEqualTo("Consent required");

        List<HtmlCheckBoxInput> scopes = new ArrayList<>();
        consentPage.querySelectorAll("input[name='scope']").forEach(scope ->
                scopes.add((HtmlCheckBoxInput) scope));
        for (HtmlCheckBoxInput scope : scopes) {
            scope.click();
        }

        List<String> scopeIds = new ArrayList<>();
        scopes.forEach(scope -> {
            assertThat(scope.isChecked()).isTrue();
            scopeIds.add(scope.getId());
        });
        assertThat(scopeIds).containsExactlyInAnyOrder("articles.read", "articles.write");

        DomElement submitConsentButton = consentPage.querySelector("button[id='submit-consent']");
        this.webClient.getOptions().setRedirectEnabled(false);

        LOG.info("click on consent for all");

        P p = submitConsentButton.click();
        LOG.info("p.value: {}:", p.value());
*/
      /*  WebResponse approveConsentResponse = submitConsentButton.click().getWebResponse();
        assertThat(approveConsentResponse.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY.value());
        String location = approveConsentResponse.getResponseHeaderValue("location");
        assertThat(location).startsWith(redirectUri);
        assertThat(location).contains("code=");*/

        //assertThat(signInResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());	// there is no "default" index page

    }

    public void whenLoginSuccessfulThenDisplayNotFoundError() throws IOException, InterruptedException {
        LOG.info("test whenLoginSuccessfulThenDisplayNotFoundError()");

        assertThat(resource.exists()).isTrue();

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "text/html;charset=UTF-8")
                .setResponseCode(200).setBody(resource.getContentAsString(StandardCharsets.UTF_8)));

        HtmlPage page = this.webClient.getPage("/");

        assertLoginPage(page);

        this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebResponse signInResponse = signIn(page, "user", "password").getWebResponse();

        assertThat(signInResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());	// there is no "default" index page
    }

    private static <P extends Page> P signIn(HtmlPage page, String username, String password) throws IOException {
        HtmlInput usernameInput = page.querySelector("input[name=\"username\"]");
        HtmlInput passwordInput = page.querySelector("input[name=\"password\"]");
        HtmlButton signInButton = page.querySelector("button");

        usernameInput.type(username);
        passwordInput.type(password);
        return signInButton.click();
    }

    private static void assertLoginPage(HtmlPage page) {
        assertThat(page.getUrl().toString()).endsWith("/login");

        HtmlInput usernameInput = page.querySelector("input[name=\"username\"]");
        HtmlInput passwordInput = page.querySelector("input[name=\"password\"]");
        HtmlButton signInButton = page.querySelector("button");

        assertThat(usernameInput).isNotNull();
        assertThat(passwordInput).isNotNull();
        assertThat(signInButton.getTextContent()).isEqualTo("Sign in");
    }
}
