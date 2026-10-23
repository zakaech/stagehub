package com.stagehub.security;

import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import com.stagehub.JetonsDeTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API interne des profils, appelée par auth-service (saga d'inscription) avec un jeton de service. */
class ProfilInterneIT extends IntegrationTestBase {

    @Test
    void reserveeAuxJetonsDeService() throws Exception {
        long id = ApiDeTest.nouvelId();
        String corps = etudiant(id);
        String jetonEtudiant = JetonsDeTest.utilisateur(id, "etu@interne.test", "ETUDIANT");
        String jetonAdmin = JetonsDeTest.utilisateur(id, "admin@interne.test", "ADMIN");

        mockMvc.perform(post("/internal/etudiants").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isUnauthorized());
        for (String jeton : new String[]{jetonEtudiant, jetonAdmin}) {
            mockMvc.perform(post("/internal/etudiants").header("Authorization", "Bearer " + jeton)
                            .contentType(MediaType.APPLICATION_JSON).content(corps))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AccesRefuse"));
        }
    }

    @Test
    void profilEtudiant_creationLectureSuppressionIdempotente() throws Exception {
        long id = ApiDeTest.nouvelId();
        String service = "Bearer " + JetonsDeTest.service();

        mockMvc.perform(post("/internal/etudiants").header("Authorization", service)
                        .contentType(MediaType.APPLICATION_JSON).content(etudiant(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.cvDisponible").value(false));
        // Même id une seconde fois : refusé, le premier profil n'est pas écrasé
        mockMvc.perform(post("/internal/etudiants").header("Authorization", service)
                        .contentType(MediaType.APPLICATION_JSON).content(etudiant(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ProfilDejaExistant"));
        mockMvc.perform(get("/internal/etudiants/{id}", id).header("Authorization", service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Alaoui"));

        // Compensation : supprimer deux fois ne pose pas de problème (elle peut être rejouée par le nettoyage)
        mockMvc.perform(delete("/internal/etudiants/{id}", id).header("Authorization", service))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/internal/etudiants/{id}", id).header("Authorization", service))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/internal/etudiants/{id}", id).header("Authorization", service))
                .andExpect(status().isNotFound());
    }

    @Test
    void profilEntreprise_creeEnAttenteAvecSonEmail() throws Exception {
        long id = ApiDeTest.nouvelId();
        String service = "Bearer " + JetonsDeTest.service();

        mockMvc.perform(post("/internal/entreprises").header("Authorization", service)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id": %d, "email": "rh@interne.test", "nom": "Acme", "secteur": "IT", "ville": "Rabat"}
                                """.formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutValidation").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.email").value("rh@interne.test"));
        mockMvc.perform(delete("/internal/entreprises/{id}", id).header("Authorization", service))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/internal/entreprises/{id}", id).header("Authorization", service))
                .andExpect(status().isNotFound());
    }

    @Test
    void demandeIncomplete_400AvecLesChampsInvalides() throws Exception {
        mockMvc.perform(post("/internal/etudiants").header("Authorization", "Bearer " + JetonsDeTest.service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"pas-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationEchouee"))
                .andExpect(jsonPath("$.erreurs[?(@.champ == 'id')]").exists());
    }

    private static String etudiant(long id) {
        return """
                {"id": %d, "email": "etu-%d@interne.test", "nom": "Alaoui", "prenom": "Sara",
                 "ecole": "ENSIAS", "filiere": "GL", "ville": "Rabat"}
                """.formatted(id, id);
    }
}
