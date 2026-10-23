package com.stagehub;

import com.jayway.jsonpath.JsonPath;
import com.stagehub.candidature.client.OffreInterne;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet du monolithe, sur un vrai PostgreSQL lancé par Testcontainers : inscription de l'étudiant →
 * candidature → traitement par l'entreprise → suivi par l'étudiant → statistiques.
 * Phase 7 : l'inscription et la connexion sont dans auth-service (profils créés ici par l'API interne, jetons signés
 * par {@link JetonsDeTest}) ; les entreprises et les offres sont dans offre-service, remplacé ici par
 * {@link FauxOffreService} (leur parcours est testé dans offre-service).
 * Exécuté par Failsafe ({@code mvn verify}) grâce au suffixe IT.
 */
class ParcoursCompletIT extends IntegrationTestBase {

    @Test
    void candidatureTraitementSuiviEtStatistiques() throws Exception {
        // 1. Une entreprise validée a publié une offre ouverte (dans offre-service)
        Long entrepriseId = ApiDeTest.nouvelId();
        String tokenEntreprise = JetonsDeTest.utilisateur(entrepriseId, "rh@acme.ma", "ENTREPRISE");
        Long offreId = ApiDeTest.nouvelId();
        OffreInterne offre = new OffreInterne(offreId, "Stage PFE Java", "Casablanca", "OUVERTE",
                LocalDate.now().plusDays(30), entrepriseId, "Acme", true);
        FauxOffreService.declarer(offre);

        // 2. Un étudiant s'inscrit : auth-service crée son compte puis son profil ici (API interne)
        Long etudiantId = ApiDeTest.nouvelId();
        appeler(post("/internal/etudiants"), JetonsDeTest.service(), """
                {"id": %d, "email": "sara@etu.ma", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "Génie logiciel", "ville": "Rabat"}
                """.formatted(etudiantId))
                .andExpect(status().isCreated());
        String tokenEtudiant = JetonsDeTest.utilisateur(etudiantId, "sara@etu.ma", "ETUDIANT");

        // 3. Il postule ; un second essai est refusé. L'offre a été lue dans offre-service, au nom de l'étudiant
        String lettre = """
                {"lettreMotivation": "Très motivée par ce stage"}
                """;
        Long candidatureId = idDe(appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), tokenEtudiant, lettre)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.offre.titre").value("Stage PFE Java")));
        FauxOffreService.SERVEUR.verify(getRequestedFor(urlPathEqualTo("/internal/offres/" + offreId))
                .withHeader("Authorization", equalTo("Bearer " + tokenEtudiant)));
        appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), tokenEtudiant, lettre)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CandidatureDejaExistante"));

        // 4. L'entreprise consulte la candidature et la fait avancer selon la machine à états
        appeler(get("/api/v1/entreprise/offres/{id}/candidatures", offreId), tokenEntreprise, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].etudiantEmail").value("sara@etu.ma"))
                .andExpect(jsonPath("$.content[0].offreTitre").value("Stage PFE Java"))
                .andExpect(jsonPath("$.content[0].lettreMotivation").value("Très motivée par ce stage"));
        changerStatut(candidatureId, tokenEntreprise, "ENTRETIEN").andExpect(status().isOk());
        changerStatut(candidatureId, tokenEntreprise, "EN_ATTENTE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TransitionStatutInvalide"));
        changerStatut(candidatureId, tokenEntreprise, "ACCEPTEE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"));

        // 5. L'étudiant voit le nouveau statut ; l'admin le retrouve dans les statistiques
        appeler(get("/api/v1/etudiant/candidatures"), tokenEtudiant, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.content[0].offre.titre").value("Stage PFE Java"))
                .andExpect(jsonPath("$.content[0].offre.entrepriseNom").value("Acme"))
                .andExpect(jsonPath("$.content[0].offre.ville").value("Casablanca"))
                .andExpect(jsonPath("$.content[0].offre.statut").value("OUVERTE"));
        String tokenAdmin = new ApiDeTest(mockMvc).tokenAdmin();
        appeler(get("/api/v1/admin/stats/candidatures"), tokenAdmin, null)
                .andExpect(status().isOk())
                // La base est partagée entre les tests d'intégration : on vérifie des minimums, pas des totaux exacts
                .andExpect(jsonPath("$.nombreCandidatures", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.candidaturesParStatut.ACCEPTEE", greaterThanOrEqualTo(1)));

        // 6. Contrôle d'accès : rôle insuffisant → 403, pas de jeton → 401
        appeler(get("/api/v1/admin/stats/candidatures"), tokenEtudiant, null).andExpect(status().isForbidden());
        appeler(get("/api/v1/etudiant/candidatures"), null, null).andExpect(status().isUnauthorized());

        // 7. L'entreprise ferme l'offre (dans offre-service) : l'étudiant garde sa candidature, avec le statut à jour
        // de l'offre, et plus personne ne peut postuler
        FauxOffreService.declarer(new OffreInterne(offreId, "Stage PFE Java", "Casablanca", "FERMEE",
                offre.dateLimite(), entrepriseId, "Acme", false));
        appeler(get("/api/v1/etudiant/candidatures"), tokenEtudiant, null)
                .andExpect(jsonPath("$.content[0].id").value(candidatureId.intValue()))
                .andExpect(jsonPath("$.content[0].offre.statut").value("FERMEE"));
        String autreEtudiant = new ApiDeTest(mockMvc).inscrireEtudiant().token();
        appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId), autreEtudiant, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OffreNonDisponible"));
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

    private static Long idDe(ResultActions resultat) throws Exception {
        String json = resultat.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
