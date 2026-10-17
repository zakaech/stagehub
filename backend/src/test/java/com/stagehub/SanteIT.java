package com.stagehub;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints Actuator : la santé est publique (healthchecks Docker), le reste n'est pas exposé. */
class SanteIT extends IntegrationTestBase {

    @Test
    void sante_publiqueEtSansDetail() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void autresEndpointsActuator_nonExposes() throws Exception {
        // Non exposés : sans jeton, la sécurité répond 401 avant même de chercher l'endpoint
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
    }
}
