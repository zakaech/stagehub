package com.stagehub;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet de la phase 1, sur un vrai PostgreSQL lancé par Testcontainers :
 * inscription → validation admin → offre → candidature → changements de statut.
 * Exécuté par Failsafe ({@code mvn verify}) grâce au suffixe IT.
 */
@SpringBootTest(properties = {
        "stagehub.jwt.secret=secret-de-test-pour-le-parcours-complet-0123456789",
        "stagehub.admin.email=admin@test.local",
        "stagehub.admin.password=admin-test-1234"
})
@AutoConfigureMockMvc
@Testcontainers
class ParcoursCompletIT {

    // @ServiceConnection : Spring Boot lit l'URL, l'utilisateur et le mot de passe du conteneur
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void inscriptionValidationOffreCandidatureEtChangementsDeStatut() throws Exception {
        // 1. Une entreprise s'inscrit : son compte est en attente
        Long entrepriseId = idDe(appeler(post("/api/v1/auth/register/entreprise"), null, """
                {"email": "rh@acme.ma", "motDePasse": "motdepasse1", "nom": "Acme",
                 "secteur": "Informatique", "ville": "Casablanca"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entreprise.statutValidation").value("EN_ATTENTE")));
        String tokenEntreprise = connecter("rh@acme.ma", "motdepasse1");

        // 2. Tant qu'elle n'est pas validée, elle ne peut pas publier
        String offre = """
                {"titre": "Stage PFE Java", "description": "API Spring Boot", "ville": "Casablanca",
                 "domaine": "Développement", "technologies": ["Java", "Spring Boot"], "dureeMois": 6,
                 "dateLimite": "%s"}
                """.formatted(LocalDate.now().plusDays(30));
        appeler(post("/api/v1/entreprise/offres"), tokenEntreprise, offre)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EntrepriseNonValidee"));

        // 3. L'admin la voit dans les comptes en attente et la valide
        String tokenAdmin = connecter("admin@test.local", "admin-test-1234");
        appeler(get("/api/v1/admin/entreprises").param("statut", "EN_ATTENTE"), tokenAdmin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(entrepriseId.intValue())));
        appeler(patch("/api/v1/admin/entreprises/{id}/validation", entrepriseId), tokenAdmin, """
                {"decision": "VALIDEE"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutValidation").value("VALIDEE"));

        // 4. L'entreprise publie son offre, visible publiquement sans authentification
        Long offreId = idDe(appeler(post("/api/v1/entreprise/offres"), tokenEntreprise, offre)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.statut").value("OUVERTE")));
        appeler(get("/api/v1/offres"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(offreId.intValue())))
                .andExpect(jsonPath("$.totalElements").value(1));

        // 5. Un étudiant s'inscrit et postule ; un second essai est refusé
        appeler(post("/api/v1/auth/register/etudiant"), null, """
                {"email": "sara@etu.ma", "motDePasse": "motdepasse1", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "Génie logiciel", "ville": "Rabat"}
                """)
                .andExpect(status().isCreated());
        String tokenEtudiant = connecter("sara@etu.ma", "motdepasse1");

        String lettre = """
                {"lettreMotivation": "Très motivée par ce stage"}
                """;
        Long candidatureId = idDe(appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), tokenEtudiant, lettre)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE")));
        appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), tokenEtudiant, lettre)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CandidatureDejaExistante"));

        // 6. L'entreprise consulte la candidature et la fait avancer selon la machine à états
        appeler(get("/api/v1/entreprise/offres/{id}/candidatures", offreId), tokenEntreprise, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].etudiantEmail").value("sara@etu.ma"))
                .andExpect(jsonPath("$.content[0].lettreMotivation").value("Très motivée par ce stage"));
        changerStatut(candidatureId, tokenEntreprise, "ENTRETIEN").andExpect(status().isOk());
        changerStatut(candidatureId, tokenEntreprise, "EN_ATTENTE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TransitionStatutInvalide"));
        changerStatut(candidatureId, tokenEntreprise, "ACCEPTEE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"));

        // 7. L'étudiant voit le nouveau statut ; l'admin le retrouve dans les statistiques
        appeler(get("/api/v1/etudiant/candidatures"), tokenEtudiant, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.content[0].offre.titre").value("Stage PFE Java"))
                .andExpect(jsonPath("$.content[0].offre.entrepriseNom").value("Acme"))
                .andExpect(jsonPath("$.content[0].offre.ville").value("Casablanca"))
                .andExpect(jsonPath("$.content[0].offre.statut").value("OUVERTE"));
        appeler(get("/api/v1/admin/stats"), tokenAdmin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreOffres").value(1))
                .andExpect(jsonPath("$.candidaturesParStatut.ACCEPTEE").value(1))
                .andExpect(jsonPath("$.topTechnologies[*].technologie", hasItem("Java")));

        // 8. Contrôle d'accès : rôle insuffisant → 403, pas de jeton → 401
        appeler(get("/api/v1/admin/stats"), tokenEtudiant, null).andExpect(status().isForbidden());
        appeler(get("/api/v1/auth/me"), null, null).andExpect(status().isUnauthorized());

        // 9. L'entreprise ferme l'offre (elle disparaît du public), puis la rouvre : sa date limite est future
        changerStatutOffre(offreId, tokenEntreprise, "FERMEE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("FERMEE"));
        appeler(get("/api/v1/offres"), null, null)
                .andExpect(jsonPath("$.totalElements").value(0));
        // L'étudiant garde sa candidature, avec le résumé de l'offre fermée
        appeler(get("/api/v1/etudiant/candidatures"), tokenEtudiant, null)
                .andExpect(jsonPath("$.content[0].id").value(candidatureId.intValue()))
                .andExpect(jsonPath("$.content[0].offre.statut").value("FERMEE"));
        changerStatutOffre(offreId, tokenEntreprise, "OUVERTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("OUVERTE"));
    }

    // --- Utilitaires ---

    private ResultActions appeler(MockHttpServletRequestBuilder requete, String token, String corps) throws Exception {
        if (token != null) {
            requete.header("Authorization", "Bearer " + token);
        }
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mockMvc.perform(requete);
    }

    private ResultActions changerStatut(Long candidatureId, String token, String statut) throws Exception {
        return appeler(patch("/api/v1/entreprise/candidatures/{id}/statut", candidatureId), token,
                "{\"statut\": \"%s\"}".formatted(statut));
    }

    private ResultActions changerStatutOffre(Long offreId, String token, String statut) throws Exception {
        return appeler(patch("/api/v1/entreprise/offres/{id}/statut", offreId), token,
                "{\"statut\": \"%s\"}".formatted(statut));
    }

    private String connecter(String email, String motDePasse) throws Exception {
        MvcResult resultat = appeler(post("/api/v1/auth/login"), null,
                "{\"email\": \"%s\", \"motDePasse\": \"%s\"}".formatted(email, motDePasse))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultat.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }

    private static Long idDe(ResultActions resultat) throws Exception {
        String json = resultat.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
