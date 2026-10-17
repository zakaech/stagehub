package com.stagehub;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Raccourcis HTTP pour mettre en place les scénarios des tests d'intégration (inscription, validation,
 * offre, candidature). Chaque compte reçoit un suffixe aléatoire : les tests restent indépendants.
 */
public class ApiDeTest {

    public static final String ADMIN_EMAIL = "admin@test.local";
    public static final String ADMIN_MOT_DE_PASSE = "admin-test-1234";

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

    public Compte inscrireEtudiant() throws Exception {
        String email = "etu-" + suffixe() + "@it.test";
        Long id = idDe(appeler(post("/api/v1/auth/register/etudiant"), null, """
                {"email": "%s", "motDePasse": "motdepasse1", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "GL", "ville": "Rabat"}
                """.formatted(email)).andExpect(status().isCreated()));
        return new Compte(id, email, connecter(email, "motdepasse1"));
    }

    public Compte inscrireEntreprise() throws Exception {
        String email = "rh-" + suffixe() + "@it.test";
        Long id = idDe(appeler(post("/api/v1/auth/register/entreprise"), null, """
                {"email": "%s", "motDePasse": "motdepasse1", "nom": "Acme", "secteur": "IT", "ville": "Rabat"}
                """.formatted(email)).andExpect(status().isCreated()));
        return new Compte(id, email, connecter(email, "motdepasse1"));
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

    public String tokenAdmin() throws Exception {
        return connecter(ADMIN_EMAIL, ADMIN_MOT_DE_PASSE);
    }

    public String connecter(String email, String motDePasse) throws Exception {
        String json = appeler(post("/api/v1/auth/login"), null,
                "{\"email\": \"%s\", \"motDePasse\": \"%s\"}".formatted(email, motDePasse))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.accessToken");
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
