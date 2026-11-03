package com.stagehub.offre;

import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import com.stagehub.JetonsDeTest;
import com.stagehub.audit.repository.AuditLogRepository;
import com.stagehub.outbox.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parcours d'offre-service sur un vrai PostgreSQL : entreprise, validation (audit + outbox), offres, API interne. */
class OffreServiceIT extends IntegrationTestBase {

    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void entrepriseEnAttente_nePeutPasPublier_puisValidee_publie() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Compte entreprise = api.inscrireEntreprise();
        String offre = """
                {"titre": "Stage", "description": "API", "ville": "Rabat", "domaine": "Dev",
                 "technologies": ["Java"], "dureeMois": 6, "dateLimite": "%s"}
                """.formatted(LocalDate.now().plusDays(10));

        api.appeler(post("/api/v1/entreprise/offres"), entreprise.token(), offre)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EntrepriseNonValidee"));

        api.appeler(patch("/api/v1/admin/entreprises/{id}/validation", entreprise.id()), api.tokenAdmin(),
                "{\"decision\": \"VALIDEE\"}").andExpect(status().isOk());
        api.appeler(post("/api/v1/entreprise/offres"), entreprise.token(), offre)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.entrepriseNom").value("Acme"));
    }

    @Test
    void validation_auditeeEtEvenementDansLOutbox_memeTransaction() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Compte entreprise = api.entrepriseValidee();

        assertThat(auditLogRepository.findByEntiteAndEntiteIdOrderByIdAsc("ENTREPRISE", entreprise.id()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo("VALIDATION_ENTREPRISE");
                    assertThat(log.getAncienneValeur()).isEqualTo("EN_ATTENTE");
                    assertThat(log.getNouvelleValeur()).isEqualTo("VALIDEE");
                    assertThat(log.getUtilisateur()).isEqualTo(ApiDeTest.ADMIN_EMAIL);
                });
        assertThat(outboxRepository.findAll()).anySatisfy(e -> {
            assertThat(e.getTopic()).isEqualTo("stagehub.entreprise.validation");
            assertThat(e.getCle()).isEqualTo(entreprise.id().toString());
            assertThat(e.getContenu()).contains("\"decision\":\"VALIDEE\"", entreprise.email());
        });

        // Décision impossible (déjà validée) : 409, ni audit ni événement en plus (rollback)
        long evenements = outboxRepository.count();
        api.appeler(patch("/api/v1/admin/entreprises/{id}/validation", entreprise.id()), api.tokenAdmin(),
                "{\"decision\": \"REFUSEE\"}").andExpect(status().isConflict());
        assertThat(outboxRepository.count()).isEqualTo(evenements);
        assertThat(auditLogRepository.findByEntiteAndEntiteIdOrderByIdAsc("ENTREPRISE", entreprise.id())).hasSize(1);
    }

    @Test
    void journalDAudit_filtreParEntiteObligatoire() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        api.entrepriseValidee();

        api.appeler(get("/api/v1/admin/audit").param("entite", "ENTREPRISE"), api.tokenAdmin(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entite").value("ENTREPRISE"));
        // Les candidatures ne sont pas auditées ici : la gateway envoie ce filtre à leur service
        api.appeler(get("/api/v1/admin/audit").param("entite", "CANDIDATURE"), api.tokenAdmin(), null)
                .andExpect(status().isBadRequest());
        api.appeler(get("/api/v1/admin/audit"), api.tokenAdmin(), null).andExpect(status().isBadRequest());
    }

    @Test
    void apiInterne_offreDeToutStatutEtResumesGroupes() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Compte entreprise = api.entrepriseValidee();
        Long ouverte = api.creerOffre(entreprise.token());
        Long fermee = api.creerOffre(entreprise.token());
        api.appeler(patch("/api/v1/entreprise/offres/{id}/statut", fermee), entreprise.token(), "{\"statut\": \"FERMEE\"}")
                .andExpect(status().isOk());
        String etudiant = JetonsDeTest.utilisateur(ApiDeTest.nouvelId(), "etu@it.test", "ETUDIANT");

        api.appeler(get("/internal/offres/{id}", ouverte), etudiant, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entrepriseId").value(entreprise.id()))
                .andExpect(jsonPath("$.accepteCandidatures").value(true));
        // Contenu de l'offre, lu par ai-service avec son jeton de service
        api.appeler(get("/internal/offres/{id}", ouverte), JetonsDeTest.service("ai-service"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.domaine").isNotEmpty())
                .andExpect(jsonPath("$.technologies").isArray())
                .andExpect(jsonPath("$.technologies").isNotEmpty());
        api.appeler(get("/internal/offres/{id}", fermee), etudiant, null)
                .andExpect(jsonPath("$.statut").value("FERMEE"))
                .andExpect(jsonPath("$.accepteCandidatures").value(false));
        api.appeler(get("/internal/offres/{id}", 987_654L), etudiant, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OffreNotFound"));
        api.appeler(get("/internal/offres").param("ids", ouverte + "," + fermee + ",987654"), etudiant, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(ouverte.intValue(), fermee.intValue())));
        api.appeler(get("/internal/offres/{id}", ouverte), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void creationDeProfil_reserveeAuxJetonsDeService() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        long id = ApiDeTest.nouvelId();
        String corps = "{\"id\": %d, \"email\": \"x@it.test\", \"nom\": \"X\", \"secteur\": \"IT\", \"ville\": \"Rabat\"}".formatted(id);

        api.appeler(post("/internal/entreprises"), JetonsDeTest.utilisateur(id, "x@it.test", "ADMIN"), corps)
                .andExpect(status().isForbidden());
        api.appeler(post("/internal/entreprises"), JetonsDeTest.service(), corps).andExpect(status().isCreated());
        api.appeler(post("/internal/entreprises"), JetonsDeTest.service(), corps)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ProfilDejaExistant"));
    }

    @Test
    void statistiquesDesOffres_reserveesALAdmin() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Compte entreprise = api.entrepriseValidee();
        api.creerOffre(entreprise.token());

        api.appeler(get("/api/v1/admin/stats/offres"), api.tokenAdmin(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreOffres", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.topTechnologies[*].technologie", hasItem("Java")));
        api.appeler(get("/api/v1/admin/stats/offres"), entreprise.token(), null).andExpect(status().isForbidden());
    }
}
