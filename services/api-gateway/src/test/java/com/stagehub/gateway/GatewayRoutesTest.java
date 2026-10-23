package com.stagehub.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La gateway devant un faux service (WireMock, à la place du monolithe) : on vérifie ce qu'elle laisse passer,
 * ce qu'elle bloque elle-même (le service ne reçoit alors rien) et ce qu'elle transmet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "stagehub.jwt.secret=" + GatewayRoutesTest.SECRET,
        "stagehub.cors.allowed-origins=http://front.test",
        "spring.http.clients.read-timeout=2s"
})
class GatewayRoutesTest {

    static final String SECRET = "secret-de-test-de-la-gateway-32-octets-minimum";

    private static final WireMockServer service = new WireMockServer(options().dynamicPort());

    static {
        service.start();
    }

    @DynamicPropertySource
    static void routeVersLeFauxService(DynamicPropertyRegistry registry) {
        registry.add("BACKEND_URI", () -> "http://localhost:" + service.port());
    }

    @AfterAll
    static void arreterLeFauxService() {
        service.stop();
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private static boolean echauffee;

    @BeforeEach
    void reinitialiser() throws Exception {
        if (!echauffee) {
            // Premier appel à froid (chargement des classes du client HTTP et de WireMock) : il peut dépasser
            // le délai de lecture du test ; on le fait une fois, hors assertions
            echauffee = true;
            service.stubFor(get(urlEqualTo("/api/v1/offres/echauffement")).willReturn(aResponse().withStatus(200)));
            envoyer(requete("/api/v1/offres/echauffement").GET());
        }
        service.resetAll();
    }

    @Test
    void offresPubliques_relayeesSansJeton() throws Exception {
        service.stubFor(get(urlPathEqualTo("/api/v1/offres")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"content\":[]}")));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres?page=0").GET());

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(reponse.body()).isEqualTo("{\"content\":[]}");
        service.verify(getRequestedFor(urlEqualTo("/api/v1/offres?page=0")));
    }

    @Test
    void routeProtegeeSansJeton_401SansAppelerLeService() throws Exception {
        HttpResponse<String> reponse = envoyer(requete("/api/v1/etudiant/candidatures").GET());

        assertThat(reponse.statusCode()).isEqualTo(401);
        assertThat(reponse.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(reponse.headers().firstValue("WWW-Authenticate")).get().asString().startsWith("Bearer");
        assertThat(reponse.body()).contains("\"code\":\"NonAuthentifie\"");
        service.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void jetonValide_requeteRelayeeAvecSonEnTeteAuthorization() throws Exception {
        service.stubFor(get(urlEqualTo("/api/v1/etudiant/candidatures")).willReturn(aResponse().withStatus(200)));
        String jeton = jeton("ETUDIANT", Instant.now().plusSeconds(3600), SECRET);

        HttpResponse<String> reponse = envoyer(requete("/api/v1/etudiant/candidatures")
                .header("Authorization", "Bearer " + jeton).GET());

        assertThat(reponse.statusCode()).isEqualTo(200);
        service.verify(getRequestedFor(urlEqualTo("/api/v1/etudiant/candidatures"))
                .withHeader("Authorization", equalTo("Bearer " + jeton)));
    }

    @Test
    void mauvaisRole_403SansAppelerLeService() throws Exception {
        String jeton = jeton("ENTREPRISE", Instant.now().plusSeconds(3600), SECRET);

        HttpResponse<String> reponse = envoyer(requete("/api/v1/etudiant/candidatures")
                .header("Authorization", "Bearer " + jeton).GET());

        assertThat(reponse.statusCode()).isEqualTo(403);
        assertThat(reponse.body()).contains("\"code\":\"AccesRefuse\"");
        service.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void jetonExpireOuSigneAvecUnAutreSecret_401() throws Exception {
        String expire = jeton("ADMIN", Instant.now().minusSeconds(120), SECRET);
        String faux = jeton("ADMIN", Instant.now().plusSeconds(3600), "un-autre-secret-tout-aussi-long-que-le-vrai!");

        for (String jeton : new String[]{expire, faux}) {
            HttpResponse<String> reponse = envoyer(requete("/api/v1/admin/stats")
                    .header("Authorization", "Bearer " + jeton).GET());
            assertThat(reponse.statusCode()).isEqualTo(401);
        }
        service.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void endpointsInternes_jamaisAccessiblesDeLExterieur() throws Exception {
        String admin = jeton("ADMIN", Instant.now().plusSeconds(3600), SECRET);

        HttpResponse<String> avecJeton = envoyer(requete("/api/v1/internal/offres/1")
                .header("Authorization", "Bearer " + admin).GET());
        HttpResponse<String> sansJeton = envoyer(requete("/internal/offres/1").GET());

        assertThat(avecJeton.statusCode()).isEqualTo(403);
        assertThat(sansJeton.statusCode()).isEqualTo(401);
        service.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void cors_preflightTraiteParLaGateway() throws Exception {
        HttpResponse<String> reponse = envoyer(requete("/api/v1/auth/login")
                .header("Origin", "http://front.test")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(reponse.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://front.test");
        service.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void cors_origineInconnueRefusee_origineAutoriseeRelayeeSansEnTeteOrigin() throws Exception {
        service.stubFor(post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse().withStatus(200)));

        HttpResponse<String> pirate = envoyer(requete("/api/v1/auth/login")
                .header("Origin", "http://pirate.test").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(pirate.statusCode()).isEqualTo(403);
        service.verify(0, anyRequestedFor(anyUrl()));

        HttpResponse<String> autorisee = envoyer(requete("/api/v1/auth/login")
                .header("Origin", "http://front.test").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(autorisee.statusCode()).isEqualTo(200);
        assertThat(autorisee.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://front.test");
        // Le service ne refait pas la vérification CORS : il ne voit plus l'origine du navigateur
        service.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login")).withHeader("Origin", absent()));
    }

    @Test
    void enTetesXForwarded_dOrigineTransmisAuService() throws Exception {
        service.stubFor(get(urlPathEqualTo("/api/v1/offres")).willReturn(aResponse().withStatus(200)));

        // Ce que Nginx ajoute : l'hôte vu par le navigateur et le protocole d'origine (https derrière Caddy)
        envoyer(requete("/api/v1/offres")
                .header("X-Forwarded-Host", "localhost:8090")
                .header("X-Forwarded-Proto", "https")
                .GET());

        // Le service (forward-headers-strategy=framework) retient la première valeur de chaque en-tête :
        // il construit donc ses URL (en-tête Location) avec l'adresse publique
        service.verify(getRequestedFor(urlPathEqualTo("/api/v1/offres"))
                .withHeader("X-Forwarded-Host", matching("^localhost:8090.*"))
                .withHeader("X-Forwarded-Proto", matching("^https.*")));
    }

    @Test
    void erreurDuService_relayeeTelleQuelle() throws Exception {
        String probleme = "{\"status\":409,\"code\":\"CandidatureDejaExistante\"}";
        service.stubFor(get(urlEqualTo("/api/v1/offres/7")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/problem+json").withBody(probleme)));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres/7").GET());

        assertThat(reponse.statusCode()).isEqualTo(409);
        assertThat(reponse.body()).isEqualTo(probleme);
    }

    @Test
    void serviceQuiCoupeLaConnexion_502() throws Exception {
        service.stubFor(get(urlEqualTo("/api/v1/offres/1"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres/1").GET());

        assertThat(reponse.statusCode()).isEqualTo(502);
        assertThat(reponse.body()).contains("\"code\":\"ServiceIndisponible\"");
    }

    @Test
    void serviceTropLent_504() throws Exception {
        service.stubFor(get(urlEqualTo("/api/v1/offres/2"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(5000)));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres/2").GET());

        assertThat(reponse.statusCode()).isEqualTo(504);
        assertThat(reponse.body()).contains("\"code\":\"DelaiDepasse\"");
    }

    private HttpRequest.Builder requete(String chemin) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin));
    }

    private HttpResponse<String> envoyer(HttpRequest.Builder requete) throws Exception {
        return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Jeton au format du monolithe : sub = email, claims uid et role, émetteur « stagehub ». */
    private static String jeton(String role, Instant expiration, String secret) {
        NimbusJwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("stagehub")
                .subject("test@stagehub.test")
                .issuedAt(expiration.minusSeconds(3600))
                .expiresAt(expiration)
                .claim("uid", 42)
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
