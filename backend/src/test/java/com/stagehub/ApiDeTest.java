package com.stagehub;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Raccourcis HTTP pour mettre en place les scénarios des tests d'intégration (profils, validation, offre,
 * candidature). Chaque compte reçoit un id et un suffixe propres : les tests restent indépendants.
 * Phase 7 : les comptes sont dans auth-service. Ici, les profils sont créés par l'API interne (comme le fait
 * auth-service à l'inscription) et les jetons sont signés par {@link JetonsDeTest}.
 */
public class ApiDeTest {

    public static final String ADMIN_EMAIL = "admin@test.local";
    private static final long ADMIN_ID = 999_999L;

    /** Ids de comptes « attribués par auth-service » : uniques pour toute l'exécution des tests. */
    private static final AtomicLong IDS = new AtomicLong(1_000_000L);

    private final MockMvc mockMvc;

    public ApiDeTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public record Compte(Long id, String email, String token) {
    }

    /** Entreprise validée (avec une offre ouverte) et étudiant ayant postulé à cette offre. */
    public record Scenario(Compte entreprise, Compte etudiant, Long offreId, Long candidatureId) {
    }

    public Scenario scenarioAvecCandidature() throws Exception {
        Compte entreprise = inscrireEntreprise();
        validerEntreprise(entreprise.id());
        Long offreId = creerOffre(entreprise.token());
        Compte etudiant = inscrireEtudiant();
        Long candidatureId = idDe(appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId),
                etudiant.token(), null).andExpect(status().isCreated()));
        return new Scenario(entreprise, etudiant, offreId, candidatureId);
    }

    public static long nouvelId() {
        return IDS.incrementAndGet();
    }

    public Compte inscrireEtudiant() throws Exception {
        long id = nouvelId();
        String email = "etu-" + suffixe() + "@it.test";
        appeler(post("/internal/etudiants"), JetonsDeTest.service(), """
                {"id": %d, "email": "%s", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "GL", "ville": "Rabat"}
                """.formatted(id, email)).andExpect(status().isCreated());
        return new Compte(id, email, JetonsDeTest.utilisateur(id, email, "ETUDIANT"));
    }

    /** Entreprise EN_ATTENTE de validation, comme après une inscription. */
    public Compte inscrireEntreprise() throws Exception {
        long id = nouvelId();
        String email = "rh-" + suffixe() + "@it.test";
        appeler(post("/internal/entreprises"), JetonsDeTest.service(), """
                {"id": %d, "email": "%s", "nom": "Acme", "secteur": "IT", "ville": "Rabat"}
                """.formatted(id, email)).andExpect(status().isCreated());
        return new Compte(id, email, JetonsDeTest.utilisateur(id, email, "ENTREPRISE"));
    }

    public ResultActions validerEntreprise(Long entrepriseId) throws Exception {
        return appeler(patch("/api/v1/admin/entreprises/{id}/validation", entrepriseId), tokenAdmin(),
                "{\"decision\": \"VALIDEE\"}").andExpect(status().isOk());
    }

    public Long creerOffre(String tokenEntreprise) throws Exception {
        return idDe(appeler(post("/api/v1/entreprise/offres"), tokenEntreprise, """
                {"titre": "Stage PFE Java", "description": "API Spring Boot", "ville": "Rabat", "domaine": "Dev",
                 "technologies": ["Java"], "dureeMois": 6, "dateLimite": "%s"}
                """.formatted(LocalDate.now().plusDays(30))).andExpect(status().isCreated()));
    }

    public ResultActions changerStatutCandidature(Long candidatureId, String token, String statut) throws Exception {
        return appeler(patch("/api/v1/entreprise/candidatures/{id}/statut", candidatureId), token,
                "{\"statut\": \"%s\"}".formatted(statut));
    }

    public String tokenAdmin() {
        return JetonsDeTest.utilisateur(ADMIN_ID, ADMIN_EMAIL, "ADMIN");
    }

    public ResultActions appeler(MockHttpServletRequestBuilder requete, String token, String corps) throws Exception {
        if (token != null) {
            requete.header("Authorization", "Bearer " + token);
        }
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mockMvc.perform(requete);
    }

    public ResultActions lire(String url, String token, Object... variables) throws Exception {
        return appeler(get(url, variables), token, null);
    }

    public static Long idDe(ResultActions resultat) throws Exception {
        String json = resultat.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String suffixe() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
