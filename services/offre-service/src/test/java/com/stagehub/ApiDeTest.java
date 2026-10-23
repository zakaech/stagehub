package com.stagehub;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Raccourcis HTTP des tests d'intégration : profil entreprise créé par l'API interne (comme le fait auth-service à
 * l'inscription), validation par l'admin, publication d'offre. Ids et emails uniques : tests indépendants.
 */
public class ApiDeTest {

    public static final String ADMIN_EMAIL = "admin@test.local";
    private static final long ADMIN_ID = 999_999L;
    private static final AtomicLong IDS = new AtomicLong(1_000_000L);

    private final MockMvc mockMvc;

    public ApiDeTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public record Compte(Long id, String email, String token) {
    }

    public static long nouvelId() {
        return IDS.incrementAndGet();
    }

    /** Entreprise EN_ATTENTE de validation, comme après une inscription. */
    public Compte inscrireEntreprise() throws Exception {
        long id = nouvelId();
        String email = "rh-" + UUID.randomUUID().toString().substring(0, 8) + "@it.test";
        appeler(post("/internal/entreprises"), JetonsDeTest.service(), """
                {"id": %d, "email": "%s", "nom": "Acme", "secteur": "IT", "ville": "Rabat"}
                """.formatted(id, email)).andExpect(status().isCreated());
        return new Compte(id, email, JetonsDeTest.utilisateur(id, email, "ENTREPRISE"));
    }

    public Compte entrepriseValidee() throws Exception {
        Compte entreprise = inscrireEntreprise();
        appeler(patch("/api/v1/admin/entreprises/{id}/validation", entreprise.id()), tokenAdmin(),
                "{\"decision\": \"VALIDEE\"}").andExpect(status().isOk());
        return entreprise;
    }

    public Long creerOffre(String tokenEntreprise) throws Exception {
        return idDe(appeler(post("/api/v1/entreprise/offres"), tokenEntreprise, """
                {"titre": "Stage PFE Java", "description": "API Spring Boot", "ville": "Rabat", "domaine": "Dev",
                 "technologies": ["Java"], "dureeMois": 6, "dateLimite": "%s"}
                """.formatted(LocalDate.now().plusDays(30))).andExpect(status().isCreated()));
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

    public static Long idDe(ResultActions resultat) throws Exception {
        String json = resultat.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
