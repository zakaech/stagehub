package com.stagehub;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet de la phase 1, sur un vrai PostgreSQL lancé par Testcontainers :
 * inscription → validation admin → offre → candidature → changements de statut.
 * Phase 7 : l'inscription et la connexion sont dans auth-service ; ici, les profils sont créés par l'API interne
 * et les jetons signés par {@link JetonsDeTest}.
 * Exécuté par Failsafe ({@code mvn verify}) grâce au suffixe IT.
 */
class ParcoursCompletIT extends IntegrationTestBase {

    @Test
    void inscriptionValidationOffreCandidatureEtChangementsDeStatut() throws Exception {
        // 1. Une entreprise s'inscrit : auth-service crée son compte puis son profil ici (API interne), en attente
        Long entrepriseId = ApiDeTest.nouvelId();
        appeler(post("/internal/entreprises"), JetonsDeTest.service(), """
                {"id": %d, "email": "rh@acme.ma", "nom": "Acme", "secteur": "Informatique", "ville": "Casablanca"}
                """.formatted(entrepriseId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutValidation").value("EN_ATTENTE"));
        String tokenEntreprise = JetonsDeTest.utilisateur(entrepriseId, "rh@acme.ma", "ENTREPRISE");

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
        String tokenAdmin = new ApiDeTest(mockMvc).tokenAdmin();
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
                .andExpect(jsonPath("$.content[*].id", hasItem(offreId.intValue())));

        // 5. Un étudiant s'inscrit et postule ; un second essai est refusé
        Long etudiantId = ApiDeTest.nouvelId();
        appeler(post("/internal/etudiants"), JetonsDeTest.service(), """
                {"id": %d, "email": "sara@etu.ma", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "Génie logiciel", "ville": "Rabat"}
                """.formatted(etudiantId))
                .andExpect(status().isCreated());
        String tokenEtudiant = JetonsDeTest.utilisateur(etudiantId, "sara@etu.ma", "ETUDIANT");

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
                // La base est partagée entre les tests d'intégration : on vérifie des minimums, pas des totaux exacts
                .andExpect(jsonPath("$.nombreOffres", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.candidaturesParStatut.ACCEPTEE", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.topTechnologies[*].technologie", hasItem("Java")));

        // 8. Contrôle d'accès : rôle insuffisant → 403, pas de jeton → 401
        appeler(get("/api/v1/admin/stats"), tokenEtudiant, null).andExpect(status().isForbidden());
        appeler(get("/api/v1/etudiant/candidatures"), null, null).andExpect(status().isUnauthorized());

        // 9. L'entreprise ferme l'offre (elle disparaît du public), puis la rouvre : sa date limite est future
        changerStatutOffre(offreId, tokenEntreprise, "FERMEE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("FERMEE"));
        appeler(get("/api/v1/offres"), null, null)
                .andExpect(jsonPath("$.content[*].id", not(hasItem(offreId.intValue()))));
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

    private static Long idDe(ResultActions resultat) throws Exception {
        String json = resultat.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
