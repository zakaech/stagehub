package com.stagehub.commons.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Format commun des erreurs, sur un petit contrôleur de test (sans démarrer d'application Spring Boot). */
class ProblemDetailExceptionHandlerTest {

    @RestController
    static class ControleurDeTest {

        @GetMapping("/offres/{offreId}")
        String offre(@PathVariable Long offreId) {
            return "offre " + offreId;
        }

        @GetMapping("/candidatures")
        String candidatures(@RequestParam LocalDate depuis) {
            return "depuis " + depuis;
        }
    }

    @RestControllerAdvice
    static class GestionnaireDeTest extends ProblemDetailExceptionHandler {
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ControleurDeTest())
            .setControllerAdvice(new GestionnaireDeTest())
            .build();

    @AfterEach
    void viderLeMdc() {
        MDC.clear();
    }

    @Test
    void idNonNumerique_400EnFrancaisAvecUnCode() throws Exception {
        mockMvc.perform(get("/offres/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ParametreInvalide"))
                .andExpect(jsonPath("$.detail").value("Le paramètre « offreId » doit être un nombre"));
    }

    @Test
    void parametreDeRequeteDUnAutreType_400AvecUnMessageGenerique() throws Exception {
        mockMvc.perform(get("/candidatures").param("depuis", "hier"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ParametreInvalide"))
                .andExpect(jsonPath("$.detail").value("Valeur invalide pour le paramètre « depuis »"));
    }

    @Test
    void dansUneTrace_lErreurPorteLeTraceId() throws Exception {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

        mockMvc.perform(get("/offres/abc"))
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void horsDeTouteTrace_pasDeTraceId() throws Exception {
        mockMvc.perform(get("/offres/abc"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }
}
