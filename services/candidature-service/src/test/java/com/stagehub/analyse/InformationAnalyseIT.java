package com.stagehub.analyse;

import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/etudiant/analyse-ia : fournisseur de l'analyse, pour le texte d'information de l'étudiant (spec §1). */
class InformationAnalyseIT extends IntegrationTestBase {

    private ApiDeTest api;

    @BeforeEach
    void preparer() {
        api = new ApiDeTest(mockMvc);
    }

    @Test
    void etudiant_fournisseurParDefautOllama() throws Exception {
        api.lire("/api/v1/etudiant/analyse-ia", api.inscrireEtudiant().token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fournisseur").value("OLLAMA"));
    }

    @Test
    void entreprise_refusee() throws Exception {
        api.lire("/api/v1/etudiant/analyse-ia", api.entreprise().token()).andExpect(status().isForbidden());
    }

    @Test
    void sansJeton_nonAuthentifie() throws Exception {
        mockMvc.perform(get("/api/v1/etudiant/analyse-ia")).andExpect(status().isUnauthorized());
    }
}
