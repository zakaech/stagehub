package com.stagehub;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Raccourcis HTTP pour mettre en place les scénarios des tests d'intégration (profils, offre, candidature). Chaque
 * compte reçoit un id et un suffixe propres : les tests restent indépendants.
 * Phase 7 : les comptes sont dans auth-service. Ici, les profils étudiants sont créés par l'API interne (comme le fait
 * auth-service à l'inscription) et les jetons sont signés par {@link JetonsDeTest}. Les entreprises et les offres sont
 * dans offre-service (phase 7 c) : une entreprise n'est plus qu'un jeton, ses offres sont déclarées dans
 * {@link FauxOffreService}.
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
        Compte entreprise = entreprise();
        Long offreId = offreOuverte(entreprise);
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

    /** Entreprise (validée, dans offre-service) : ici, seulement un jeton portant son id. */
    public Compte entreprise() {
        long id = nouvelId();
        String email = "rh-" + suffixe() + "@it.test";
        return new Compte(id, email, JetonsDeTest.utilisateur(id, email, "ENTREPRISE"));
    }

    /** Offre ouverte de cette entreprise, déclarée dans le faux offre-service. */
    public Long offreOuverte(Compte entreprise) {
        long offreId = nouvelId();
        FauxOffreService.offreOuverte(offreId, entreprise.id());
        return offreId;
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
