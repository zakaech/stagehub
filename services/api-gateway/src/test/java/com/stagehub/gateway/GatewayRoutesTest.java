package com.stagehub.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La gateway devant trois faux services (WireMock) : candidature-service, offre-service, et auth-service qui publie
 * aussi la clé publique (JWKS). On vérifie ce qu'elle laisse passer, ce qu'elle bloque elle-même (le service ne reçoit alors rien), où elle
 * envoie chaque requête et ce qu'elle transmet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "stagehub.cors.allowed-origins=http://front.test",
        "spring.http.clients.read-timeout=2s"
})
class GatewayRoutesTest {

    private static final WireMockServer candidatures = new WireMockServer(options().dynamicPort());
    private static final WireMockServer auth = new WireMockServer(options().dynamicPort());
    private static final WireMockServer offres = new WireMockServer(options().dynamicPort());

    /** Clé d'auth-service (sa partie publique est servie en JWKS) et clé d'un faussaire (inconnue de la gateway). */
    private static final RSAKey CLE_AUTH = cleRsa("cle-auth");
    private static final RSAKey CLE_FAUSSAIRE = cleRsa("cle-auth");

    /** Requêtes d'API (hors téléchargement du JWKS, qui passe par le même faux auth-service). */
    private static final RequestPatternBuilder APPEL_D_API = anyRequestedFor(urlMatching("/(api|internal).*"));

    static {
        candidatures.start();
        auth.start();
        offres.start();
    }

    @DynamicPropertySource
    static void routesVersLesFauxServices(DynamicPropertyRegistry registry) {
        registry.add("CANDIDATURE_SERVICE_URI", () -> "http://localhost:" + candidatures.port());
        registry.add("AUTH_SERVICE_URI", () -> "http://localhost:" + auth.port());
        registry.add("OFFRE_SERVICE_URI", () -> "http://localhost:" + offres.port());
        registry.add("stagehub.jwt.jwk-set-uri", () -> "http://localhost:" + auth.port() + "/.well-known/jwks.json");
    }

    @AfterAll
    static void arreterLesFauxServices() {
        candidatures.stop();
        auth.stop();
        offres.stop();
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private static boolean echauffee;

    @BeforeEach
    void reinitialiser() throws Exception {
        candidatures.resetAll();
        auth.resetAll();
        offres.resetAll();
        auth.stubFor(get(urlEqualTo("/.well-known/jwks.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JWKSet(CLE_AUTH.toPublicJWK()).toString())));
        if (!echauffee) {
            // Premier appel à froid (chargement des classes du client HTTP et de WireMock) : il peut dépasser
            // le délai de lecture du test ; on le fait une fois, hors assertions
            echauffee = true;
            offres.stubFor(get(urlEqualTo("/api/v1/offres/echauffement")).willReturn(aResponse().withStatus(200)));
            envoyer(requete("/api/v1/offres/echauffement").GET());
            offres.resetAll();
        }
    }

    @Test
    void offresPubliques_relayeesAOffreServiceSansJeton() throws Exception {
        offres.stubFor(get(urlPathEqualTo("/api/v1/offres")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"content\":[]}")));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres?page=0").GET());

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(reponse.body()).isEqualTo("{\"content\":[]}");
        offres.verify(getRequestedFor(urlEqualTo("/api/v1/offres?page=0")));
        candidatures.verify(0, APPEL_D_API);
    }

    /**
     * Les routes d'offres et d'entreprises vont à offre-service, sauf les candidatures d'une offre (candidature-service) ;
     * le journal d'audit va au service qui tient l'entité demandée (candidature-service par défaut, qui répond 400).
     */
    @Test
    void routes_reparties_entreOffreServiceEtCandidatureService() throws Exception {
        offres.stubFor(get(urlMatching("/api/.*")).willReturn(aResponse().withStatus(200)));
        candidatures.stubFor(get(urlMatching("/api/.*")).willReturn(aResponse().withStatus(200)));
        String entreprise = "Bearer " + jetonRs256("ENTREPRISE", Instant.now().plusSeconds(3600), CLE_AUTH);
        String admin = "Bearer " + jetonRs256("ADMIN", Instant.now().plusSeconds(3600), CLE_AUTH);

        String[][] versOffreService = {
                {"/api/v1/offres/filtres", null},
                {"/api/v1/entreprise/offres", entreprise},
                {"/api/v1/entreprise/offres/5", entreprise},
                {"/api/v1/admin/entreprises?statut=EN_ATTENTE", admin},
                {"/api/v1/admin/stats/offres", admin},
                {"/api/v1/admin/audit?entite=ENTREPRISE&page=0", admin}};
        String etudiant = "Bearer " + jetonRs256("ETUDIANT", Instant.now().plusSeconds(3600), CLE_AUTH);
        String[][] versCandidatureService = {
                {"/api/v1/etudiant/candidatures?page=0", etudiant},
                {"/api/v1/etudiant/cv", etudiant},
                {"/api/v1/entreprise/offres/5/candidatures", entreprise},
                {"/api/v1/entreprise/candidatures/9/cv", entreprise},
                {"/api/v1/admin/stats/candidatures", admin},
                {"/api/v1/admin/audit?entite=CANDIDATURE", admin},
                {"/api/v1/admin/audit", admin}};

        for (String[] appel : versOffreService) {
            assertThat(envoyer(avecJeton(requete(appel[0]), appel[1]).GET()).statusCode()).as(appel[0]).isEqualTo(200);
            offres.verify(getRequestedFor(urlEqualTo(appel[0])));
        }
        for (String[] appel : versCandidatureService) {
            assertThat(envoyer(avecJeton(requete(appel[0]), appel[1]).GET()).statusCode()).as(appel[0]).isEqualTo(200);
            candidatures.verify(getRequestedFor(urlEqualTo(appel[0])));
        }
        offres.verify(versOffreService.length, APPEL_D_API);
        candidatures.verify(versCandidatureService.length, APPEL_D_API);
    }

    @Test
    void authentification_routeeVersAuthService() throws Exception {
        auth.stubFor(post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"accessToken\":\"x\"}")));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/auth/login")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(reponse.statusCode()).isEqualTo(200);
        auth.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login")));
        candidatures.verify(0, APPEL_D_API);
    }

    @Test
    void routeProtegeeSansJeton_401SansAppelerLeService() throws Exception {
        HttpResponse<String> reponse = envoyer(requete("/api/v1/etudiant/candidatures").GET());

        assertThat(reponse.statusCode()).isEqualTo(401);
        assertThat(reponse.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(reponse.headers().firstValue("WWW-Authenticate")).get().asString().startsWith("Bearer");
        assertThat(reponse.body()).contains("\"code\":\"NonAuthentifie\"");
        candidatures.verify(0, APPEL_D_API);
    }

    @Test
    void jetonSigneParAuthService_requeteRelayeeAvecSonEnTeteAuthorization() throws Exception {
        candidatures.stubFor(get(urlEqualTo("/api/v1/etudiant/candidatures")).willReturn(aResponse().withStatus(200)));
        String jeton = jetonRs256("ETUDIANT", Instant.now().plusSeconds(3600), CLE_AUTH);

        HttpResponse<String> reponse = envoyer(requete("/api/v1/etudiant/candidatures")
                .header("Authorization", "Bearer " + jeton).GET());

        assertThat(reponse.statusCode()).isEqualTo(200);
        candidatures.verify(getRequestedFor(urlEqualTo("/api/v1/etudiant/candidatures"))
                .withHeader("Authorization", equalTo("Bearer " + jeton)));
    }

    @Test
    void mauvaisRole_403SansAppelerLeService() throws Exception {
        String jeton = jetonRs256("ENTREPRISE", Instant.now().plusSeconds(3600), CLE_AUTH);

        HttpResponse<String> reponse = envoyer(requete("/api/v1/etudiant/candidatures")
                .header("Authorization", "Bearer " + jeton).GET());

        assertThat(reponse.statusCode()).isEqualTo(403);
        assertThat(reponse.body()).contains("\"code\":\"AccesRefuse\"");
        candidatures.verify(0, APPEL_D_API);
    }

    @Test
    void jetonsRefuses_expireFaussaireOuHs256_401() throws Exception {
        String expire = jetonRs256("ADMIN", Instant.now().minusSeconds(120), CLE_AUTH);
        // Même kid que la vraie clé, mais une autre clé privée : la signature ne correspond pas à la clé publique
        String faussaire = jetonRs256("ADMIN", Instant.now().plusSeconds(3600), CLE_FAUSSAIRE);
        // « Confusion d'algorithme » : un jeton HS256 n'est jamais accepté, quel que soit le secret utilisé
        String hs256 = jetonHs256("ADMIN", "un-secret-quelconque-mais-assez-long-pour-hs256!");

        for (String jeton : new String[]{expire, faussaire, hs256}) {
            HttpResponse<String> reponse = envoyer(requete("/api/v1/admin/stats/candidatures")
                    .header("Authorization", "Bearer " + jeton).GET());
            assertThat(reponse.statusCode()).isEqualTo(401);
        }
        candidatures.verify(0, APPEL_D_API);
    }

    @Test
    void endpointsInternes_jamaisAccessiblesDeLExterieur() throws Exception {
        String admin = jetonRs256("ADMIN", Instant.now().plusSeconds(3600), CLE_AUTH);
        String service = jetonRs256("SERVICE", Instant.now().plusSeconds(300), CLE_AUTH);

        HttpResponse<String> avecJetonAdmin = envoyer(requete("/api/v1/internal/offres/1")
                .header("Authorization", "Bearer " + admin).GET());
        HttpResponse<String> avecJetonService = envoyer(requete("/internal/etudiants/1")
                .header("Authorization", "Bearer " + service).GET());
        HttpResponse<String> sansJeton = envoyer(requete("/internal/offres/1").GET());

        assertThat(avecJetonAdmin.statusCode()).isEqualTo(403);
        assertThat(avecJetonService.statusCode()).isEqualTo(403);
        assertThat(sansJeton.statusCode()).isEqualTo(401);
        candidatures.verify(0, APPEL_D_API);
        auth.verify(0, APPEL_D_API);
        offres.verify(0, APPEL_D_API);
    }

    @Test
    void cheminDApiInconnu_404SansAppelerAucunService() throws Exception {
        String jeton = jetonRs256("ADMIN", Instant.now().plusSeconds(3600), CLE_AUTH);

        HttpResponse<String> reponse = envoyer(requete("/api/v1/inconnu").header("Authorization", "Bearer " + jeton).GET());

        assertThat(reponse.statusCode()).isEqualTo(404);
        assertThat(reponse.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        candidatures.verify(0, APPEL_D_API);
        offres.verify(0, APPEL_D_API);
        auth.verify(0, APPEL_D_API);
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
        auth.verify(0, APPEL_D_API);
    }

    @Test
    void cors_origineInconnueRefusee_origineAutoriseeRelayeeSansEnTeteOrigin() throws Exception {
        auth.stubFor(post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse().withStatus(200)));

        HttpResponse<String> pirate = envoyer(requete("/api/v1/auth/login")
                .header("Origin", "http://pirate.test").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(pirate.statusCode()).isEqualTo(403);
        auth.verify(0, APPEL_D_API);

        HttpResponse<String> autorisee = envoyer(requete("/api/v1/auth/login")
                .header("Origin", "http://front.test").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(autorisee.statusCode()).isEqualTo(200);
        assertThat(autorisee.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://front.test");
        // Le service ne refait pas la vérification CORS : il ne voit plus l'origine du navigateur
        auth.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login")).withHeader("Origin", absent()));
    }

    @Test
    void enTetesXForwarded_dOrigineTransmisAuService() throws Exception {
        offres.stubFor(get(urlPathEqualTo("/api/v1/offres")).willReturn(aResponse().withStatus(200)));

        // Ce que Nginx ajoute : l'hôte vu par le navigateur et le protocole d'origine (https derrière Caddy)
        envoyer(requete("/api/v1/offres")
                .header("X-Forwarded-Host", "localhost:8090")
                .header("X-Forwarded-Proto", "https")
                .GET());

        // Le service (forward-headers-strategy=framework) retient la première valeur de chaque en-tête :
        // il construit donc ses URL (en-tête Location) avec l'adresse publique
        offres.verify(getRequestedFor(urlPathEqualTo("/api/v1/offres"))
                .withHeader("X-Forwarded-Host", matching("^localhost:8090.*"))
                .withHeader("X-Forwarded-Proto", matching("^https.*")));
    }

    @Test
    void documentationDeChaqueService_relayeeAvecLeBonChemin() throws Exception {
        auth.stubFor(get(urlEqualTo("/v3/api-docs")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"openapi\":\"3.1.0\"}")));

        HttpResponse<String> doc = envoyer(requete("/v3/api-docs/auth-service").GET());
        HttpResponse<String> interfaceSwagger = envoyer(requete("/swagger-ui/index.html").GET());

        assertThat(doc.statusCode()).isEqualTo(200);
        assertThat(doc.body()).contains("openapi");
        auth.verify(getRequestedFor(urlEqualTo("/v3/api-docs")));
        assertThat(interfaceSwagger.statusCode()).isEqualTo(200);
    }

    @Test
    void erreurDuService_relayeeTelleQuelle() throws Exception {
        String probleme = "{\"status\":409,\"code\":\"CandidatureDejaExistante\"}";
        offres.stubFor(get(urlEqualTo("/api/v1/offres/7")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/problem+json").withBody(probleme)));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres/7").GET());

        assertThat(reponse.statusCode()).isEqualTo(409);
        assertThat(reponse.body()).isEqualTo(probleme);
    }

    @Test
    void serviceIndisponible503_relayeSansRejouerLaRequete() throws Exception {
        String probleme = "{\"status\":503,\"code\":\"ServiceIndisponible\"}";
        candidatures.stubFor(post(urlEqualTo("/api/v1/etudiant/offres/3/candidatures")).willReturn(aResponse()
                .withStatus(503).withHeader("Content-Type", "application/problem+json").withBody(probleme)));
        String jeton = jetonRs256("ETUDIANT", Instant.now().plusSeconds(3600), CLE_AUTH);

        HttpResponse<String> reponse = envoyer(requete("/api/v1/etudiant/offres/3/candidatures")
                .header("Authorization", "Bearer " + jeton).POST(HttpRequest.BodyPublishers.noBody()));

        assertThat(reponse.statusCode()).isEqualTo(503);
        assertThat(reponse.body()).isEqualTo(probleme);
        // Une écriture n'est jamais rejouée par la gateway (HttpClient le ferait par défaut sur un 503)
        candidatures.verify(1, postRequestedFor(urlEqualTo("/api/v1/etudiant/offres/3/candidatures")));
    }

    @Test
    void serviceQuiCoupeLaConnexion_502() throws Exception {
        offres.stubFor(get(urlEqualTo("/api/v1/offres/1"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres/1").GET());

        assertThat(reponse.statusCode()).isEqualTo(502);
        assertThat(reponse.body()).contains("\"code\":\"ServiceIndisponible\"");
        // GET (idempotent) coupé avant toute réponse : une seule nouvelle tentative
        offres.verify(2, getRequestedFor(urlEqualTo("/api/v1/offres/1")));
    }

    @Test
    void serviceTropLent_504() throws Exception {
        offres.stubFor(get(urlEqualTo("/api/v1/offres/2"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(5000)));

        HttpResponse<String> reponse = envoyer(requete("/api/v1/offres/2").GET());

        assertThat(reponse.statusCode()).isEqualTo(504);
        assertThat(reponse.body()).contains("\"code\":\"DelaiDepasse\"");
    }

    private HttpRequest.Builder requete(String chemin) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin));
    }

    private static HttpRequest.Builder avecJeton(HttpRequest.Builder requete, String authorization) {
        return authorization == null ? requete : requete.header("Authorization", authorization);
    }

    private HttpResponse<String> envoyer(HttpRequest.Builder requete) throws Exception {
        return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Jeton au format d'auth-service : RS256 avec kid, sub = email, claims uid et role, émetteur « stagehub ». */
    private static String jetonRs256(String role, Instant expiration, RSAKey cle) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(cle)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(cle.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims(role, expiration))).getTokenValue();
    }

    private static String jetonHs256(String role, String secret) {
        NimbusJwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims(role, Instant.now().plusSeconds(3600))))
                .getTokenValue();
    }

    private static JwtClaimsSet claims(String role, Instant expiration) {
        return JwtClaimsSet.builder()
                .issuer("stagehub")
                .subject("test@stagehub.test")
                .issuedAt(expiration.minusSeconds(3600))
                .expiresAt(expiration)
                .claim("uid", 42)
                .claim("role", role)
                .build();
    }

    private static RSAKey cleRsa(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
