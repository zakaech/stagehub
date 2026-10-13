package com.stagehub.security;

import com.stagehub.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Vérifie la configuration CORS ({@code stagehub.cors.allowed-origins=http://localhost:4200} dans la base). */
class CorsIT extends IntegrationTestBase {

    private static final String ORIGINE_AUTORISEE = "http://localhost:4200";

    @Test
    void preflightDepuisLOrigineAutorisee_estAccepte() throws Exception {
        mockMvc.perform(options("/api/v1/entreprise/offres")
                        .header("Origin", ORIGINE_AUTORISEE)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGINE_AUTORISEE))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")));
    }

    @Test
    void preflightDepuisUneAutreOrigine_estRefuse() throws Exception {
        mockMvc.perform(options("/api/v1/entreprise/offres")
                        .header("Origin", "http://site-malveillant.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void requeteDepuisLOrigineAutorisee_exposeLEnTeteLocation() throws Exception {
        mockMvc.perform(get("/api/v1/offres").header("Origin", ORIGINE_AUTORISEE))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGINE_AUTORISEE))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Location")));
    }
}
