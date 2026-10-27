package com.stagehub.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.stagehub.auth.client.ProfilEntrepriseClient;
import com.stagehub.auth.client.ProfilEtudiantClient;
import com.stagehub.auth.entity.StatutCompte;
import com.stagehub.auth.repository.UtilisateurRepository;
import com.stagehub.auth.service.NettoyageInscriptionsJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * auth-service sur un vrai PostgreSQL (Testcontainers), le service des profils étant simulé par WireMock.
 * On vérifie le contrat de l'API (le même que le monolithe), la saga d'inscription et ses compensations,
 * et les jetons RS256 (vérifiables avec la clé publique publiée, sans rien de secret).
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "stagehub.admin.email=admin@test.local",
        "stagehub.admin.password=admin-test-1234",
        // Délai court pour simuler un service des profils trop lent
        "spring.cloud.openfeign.client.config.default.read-timeout=3000",
        // Nettoyage déclenché à la main par le test (abandon immédiat, pas d'exécution planifiée pendant le test)
        "stagehub.inscription.delai-abandon=PT0S",
        "stagehub.inscription.intervalle-nettoyage=PT1H",
        // Jetons de service (client credentials) : un client valide, et un client au secret trop court (ignoré)
        "stagehub.clients-service.secrets.ai-service=" + AuthServiceIT.SECRET_AI_SERVICE,
        "stagehub.clients-service.secrets.service-faible=court"
})
@AutoConfigureMockMvc
class AuthServiceIT {

    static final String SECRET_AI_SERVICE = "secret-de-test-ai-service-0123456789abcdef";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final WireMockServer profils = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        profils.start();
    }

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("stagehub.jwt.private-key", ClesDeTest::clePriveeBase64);
        // Adresse fixe pour les deux clients Feign : pas d'annuaire Eureka en test
        String url = "http://localhost:" + profils.port();
        registry.add("spring.cloud.openfeign.client.config.profilsEtudiant.url", () -> url);
        registry.add("spring.cloud.openfeign.client.config.profilsEntreprise.url", () -> url);
    }

    @AfterAll
    static void arreterLeFauxService() {
        profils.stop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private NettoyageInscriptionsJob nettoyage;
    @Autowired
    private ProfilEtudiantClient profilEtudiantClient;
    @Autowired
    private ProfilEntrepriseClient profilEntrepriseClient;

    private static boolean echauffe;

    @BeforeEach
    void reinitialiser() {
        profils.resetAll();
        if (!echauffe) {
            // Premier appel de chaque client Feign à froid (création du client, chargement des classes) : il peut
            // dépasser le délai de lecture du test ; on le fait une fois, hors assertions
            echauffe = true;
            profils.stubFor(delete(urlMatching("/internal/.*")).willReturn(aResponse().withStatus(204)));
            for (Runnable appel : new Runnable[]{() -> profilEtudiantClient.supprimer(0L), () -> profilEntrepriseClient.supprimer(0L)}) {
                try {
                    appel.run();
                } catch (RuntimeException e) {
                    // Échauffement seulement : même un délai dépassé a servi à initialiser le client
                }
            }
            profils.resetAll();
        }
    }

    // --- Inscription (saga) ---

    @Test
    void inscriptionEtudiant_compteActifEtProfilCreeAvecUnJetonDeService() throws Exception {
        profils.stubFor(post(urlEqualTo("/internal/etudiants")).willReturn(aResponse().withStatus(201)));
        String email = email();

        inscrireEtudiant(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ETUDIANT"))
                .andExpect(jsonPath("$.etudiant.nom").value("Alaoui"))
                .andExpect(jsonPath("$.etudiant.cvDisponible").value(false));

        // Profil créé avec l'id du compte et l'email, appel signé par un jeton de rôle SERVICE
        Long id = utilisateurRepository.findByEmail(email).orElseThrow().getId();
        profils.verify(postRequestedFor(urlEqualTo("/internal/etudiants"))
                .withRequestBody(matchingJsonPath("$.id", equalTo(id.toString())))
                .withRequestBody(matchingJsonPath("$.email", equalTo(email)))
                .withHeader("Authorization", matching("Bearer .+")));
        String jetonService = profils.findAll(postRequestedFor(urlEqualTo("/internal/etudiants"))).getFirst()
                .getHeader("Authorization").substring("Bearer ".length());
        Jwt service = verifierAvecLaClePublique(jetonService);
        assertThat(service.getClaimAsString("role")).isEqualTo("SERVICE");
        assertThat(service.hasClaim("uid")).isFalse();
        assertThat(Duration.between(Instant.now(), service.getExpiresAt())).isLessThanOrEqualTo(Duration.ofMinutes(5));

        assertThat(utilisateurRepository.findByEmail(email).orElseThrow().getStatut()).isEqualTo(StatutCompte.ACTIF);
        connecter(email, "motdepasse1").andExpect(status().isOk());
    }

    @Test
    void inscriptionEntreprise_profilEnAttenteDeValidation() throws Exception {
        profils.stubFor(post(urlEqualTo("/internal/entreprises")).willReturn(aResponse().withStatus(201)));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register/entreprise").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "motDePasse": "motdepasse1", "nom": "Acme", "secteur": "IT", "ville": "Rabat"}
                        """.formatted(email())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ENTREPRISE"))
                .andExpect(jsonPath("$.entreprise.statutValidation").value("EN_ATTENTE"));
    }

    @Test
    void serviceDesProfilsEnErreur_compensation_compteSupprime_503() throws Exception {
        profils.stubFor(post(urlEqualTo("/internal/etudiants")).willReturn(aResponse().withStatus(500)));
        profils.stubFor(delete(urlMatching("/internal/etudiants/.*")).willReturn(aResponse().withStatus(204)));
        String email = email();

        inscrireEtudiant(email)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ServiceIndisponible"));

        // Compensation : profil éventuel puis compte supprimés ; l'email est de nouveau libre
        profils.verify(deleteRequestedFor(urlMatching("/internal/etudiants/.*")));
        assertThat(utilisateurRepository.findByEmail(email)).isEmpty();
        profils.stubFor(post(urlEqualTo("/internal/etudiants")).willReturn(aResponse().withStatus(201)));
        inscrireEtudiant(email).andExpect(status().isCreated());
    }

    @Test
    void serviceDesProfilsInjoignablePuisDeRetour_compteEnAttenteNettoyePlusTard() throws Exception {
        // Création trop lente (délai dépassé) ET suppression impossible : la compensation ne peut pas aboutir
        profils.stubFor(post(urlEqualTo("/internal/etudiants")).willReturn(aResponse().withStatus(201).withFixedDelay(6000)));
        profils.stubFor(delete(urlMatching("/internal/etudiants/.*"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        String email = email();

        inscrireEtudiant(email).andExpect(status().isServiceUnavailable());

        // Le compte reste EN_ATTENTE : inutilisable (même réponse qu'un mauvais mot de passe)
        assertThat(utilisateurRepository.findByEmail(email).orElseThrow().getStatut()).isEqualTo(StatutCompte.EN_ATTENTE);
        connecter(email, "motdepasse1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IdentifiantsInvalides"));

        // Le service des profils revient : le nettoyage termine la compensation
        profils.resetAll();
        profils.stubFor(delete(urlMatching("/internal/etudiants/.*")).willReturn(aResponse().withStatus(204)));
        nettoyage.nettoyer();
        assertThat(utilisateurRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void emailDejaUtilise_409SansAppelerLeServiceDesProfils() throws Exception {
        profils.stubFor(post(urlEqualTo("/internal/etudiants")).willReturn(aResponse().withStatus(201)));
        String email = email();
        inscrireEtudiant(email).andExpect(status().isCreated());
        profils.resetAll();

        inscrireEtudiant(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EmailDejaUtilise"));
        profils.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void demandeInvalide_400AvecLesChamps() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register/etudiant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"pas-un-email\", \"motDePasse\": \"court\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationEchouee"))
                .andExpect(jsonPath("$.erreurs[?(@.champ == 'motDePasse')]").exists());
    }

    // --- Connexion et jetons ---

    @Test
    void connexion_jetonRs256VerifiableAvecLaClePubliqueSeule() throws Exception {
        profils.stubFor(post(urlEqualTo("/internal/etudiants")).willReturn(aResponse().withStatus(201)));
        String email = email();
        inscrireEtudiant(email).andExpect(status().isCreated());

        String json = connecter(email.toUpperCase(), "motdepasse1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.role").value("ETUDIANT"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Jwt jwt = verifierAvecLaClePublique(JsonPath.read(json, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(email);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ETUDIANT");
        assertThat(((Number) jwt.getClaim("uid")).longValue())
                .isEqualTo(utilisateurRepository.findByEmail(email).orElseThrow().getId());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("stagehub");
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsKey("kid");
    }

    @Test
    void jwks_neContientQueLaClePublique() throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        RSAKey cle = (RSAKey) JWKSet.parse(json).getKeys().getFirst();
        assertThat(cle.isPrivate()).isFalse();
        // Ni l'exposant privé « d » ni les facteurs premiers « p », « q » ne sortent
        assertThat(json).doesNotContain("\"d\"").doesNotContain("\"p\"").doesNotContain("\"q\"");
        assertThat(cle.getKeyID()).isNotBlank();
    }

    @Test
    void mauvaisMotDePasse_401IdentifiantsInvalides() throws Exception {
        connecter("admin@test.local", "pas-le-bon")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IdentifiantsInvalides"));
        connecter("inconnu@test.local", "motdepasse1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IdentifiantsInvalides"));
    }

    // --- Profil (/auth/me) ---

    @Test
    void profil_composeLeCompteEtLeProfilDuServiceQuiLeDetient() throws Exception {
        profils.stubFor(post(urlEqualTo("/internal/etudiants")).willReturn(aResponse().withStatus(201)));
        String email = email();
        inscrireEtudiant(email).andExpect(status().isCreated());
        Long id = utilisateurRepository.findByEmail(email).orElseThrow().getId();
        profils.stubFor(get(urlEqualTo("/internal/etudiants/" + id)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {"id": %d, "nom": "Alaoui", "prenom": "Sara", "ecole": "ENSIAS", "filiere": "GL",
                         "ville": "Rabat", "cvDisponible": true}
                        """.formatted(id))));
        String jeton = jeton(email, "motdepasse1");

        moi(jeton)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.etudiant.cvDisponible").value(true))
                .andExpect(jsonPath("$.entreprise").doesNotExist());

        // Service des profils en panne : 503 explicite plutôt qu'un profil incomplet
        profils.stubFor(get(urlEqualTo("/internal/etudiants/" + id)).willReturn(aResponse().withStatus(500)));
        moi(jeton).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ServiceIndisponible"));
    }

    @Test
    void profilEntreprise_statutDeValidationLuDansLeServiceDesProfils() throws Exception {
        profils.stubFor(post(urlEqualTo("/internal/entreprises")).willReturn(aResponse().withStatus(201)));
        String email = email();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register/entreprise").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "motDePasse": "motdepasse1", "nom": "Acme", "secteur": "IT", "ville": "Rabat"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        Long id = utilisateurRepository.findByEmail(email).orElseThrow().getId();
        profils.stubFor(get(urlEqualTo("/internal/entreprises/" + id)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {"id": %d, "email": "%s", "nom": "Acme", "secteur": "IT", "ville": "Rabat",
                         "statutValidation": "VALIDEE"}
                        """.formatted(id, email))));

        moi(jeton(email, "motdepasse1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ENTREPRISE"))
                .andExpect(jsonPath("$.entreprise.statutValidation").value("VALIDEE"))
                .andExpect(jsonPath("$.etudiant").doesNotExist());
    }

    @Test
    void profilAdmin_sansAppelExterne_etSansJeton401() throws Exception {
        moi(jeton("admin@test.local", "admin-test-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        profils.verify(0, anyRequestedFor(anyUrl()));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NonAuthentifie"));
    }

    // --- Utilitaires ---

    private ResultActions inscrireEtudiant(String email) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register/etudiant").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "motDePasse": "motdepasse1", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "GL", "ville": "Rabat"}
                """.formatted(email)));
    }

    private ResultActions connecter(String email, String motDePasse) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"motDePasse\": \"%s\"}".formatted(email, motDePasse)));
    }

    // --- Jetons de service : flux OAuth2 client credentials (phase 8 c) ---

    @Test
    void clientCredentials_jetonCourtSigneAuNomDuService() throws Exception {
        String json = demanderJetonDeService("ai-service", SECRET_AI_SERVICE, "client_credentials")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Jwt jwt = verifierAvecLaClePublique(JsonPath.read(json, "$.access_token"));
        assertThat(jwt.getSubject()).isEqualTo("ai-service");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("SERVICE");
        assertThat(jwt.hasClaim("uid")).isFalse();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void clientCredentials_mauvaisSecretClientInconnuOuSansIdentifiants_401InvalidClient() throws Exception {
        for (ResultActions refus : new ResultActions[]{
                demanderJetonDeService("ai-service", SECRET_AI_SERVICE + "x", "client_credentials"),
                demanderJetonDeService("offre-service", SECRET_AI_SERVICE, "client_credentials"),
                mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials"))}) {
            refus.andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_client"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("WWW-Authenticate", "Basic realm=\"stagehub\""));
        }
    }

    @Test
    void clientCredentials_secretTropCourtJamaisAccepte() throws Exception {
        // Configuré avec « court » : refusé même avec ce secret exact
        demanderJetonDeService("service-faible", "court", "client_credentials")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCredentials_autreTypeDeDemande_400UnsupportedGrantType() throws Exception {
        demanderJetonDeService("ai-service", SECRET_AI_SERVICE, "password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    /** Comme le client OAuth2 de Spring Security : identifiant et secret encodés, puis en Basic. */
    private ResultActions demanderJetonDeService(String client, String secret, String grantType) throws Exception {
        String identifiants = java.net.URLEncoder.encode(client, StandardCharsets.UTF_8) + ":"
                + java.net.URLEncoder.encode(secret, StandardCharsets.UTF_8);
        return mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder()
                        .encodeToString(identifiants.getBytes(StandardCharsets.UTF_8)))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", grantType));
    }

    private String jeton(String email, String motDePasse) throws Exception {
        String json = connecter(email, motDePasse).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.accessToken");
    }

    private ResultActions moi(String jeton) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + jeton));
    }

    /** Ce que font la gateway et les services : vérifier avec la seule clé publique publiée. */
    private Jwt verifierAvecLaClePublique(String jeton) throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/.well-known/jwks.json"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        RSAKey publique = (RSAKey) JWKSet.parse(json).getKeys().getFirst();
        return NimbusJwtDecoder.withPublicKey(publique.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build()
                .decode(jeton);
    }

    private static String email() {
        return "compte-" + UUID.randomUUID().toString().substring(0, 8) + "@auth.test";
    }
}
