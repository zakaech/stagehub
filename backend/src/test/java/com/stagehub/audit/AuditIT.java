package com.stagehub.audit;

import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import com.stagehub.audit.entity.ActionAudit;
import com.stagehub.audit.entity.AuditLog;
import com.stagehub.audit.entity.EntiteAuditee;
import com.stagehub.audit.repository.AuditLogRepository;
import com.stagehub.audit.service.AuditService;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.candidature.service.CandidatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit de bout en bout : succès, échecs métier, rollback de transaction. */
class AuditIT extends IntegrationTestBase {

    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AuditService auditService;
    @Autowired
    private CandidatureService candidatureService;
    @Autowired
    private CandidatureRepository candidatureRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private ApiDeTest api;

    @BeforeEach
    void setUp() {
        api = new ApiDeTest(mockMvc);
    }

    @Test
    void validationEntreprise_estAuditeeAvecLAdminCommeAuteur() throws Exception {
        ApiDeTest.Compte entreprise = api.inscrireEntreprise();

        api.validerEntreprise(entreprise.id());

        assertThat(historique(EntiteAuditee.ENTREPRISE, entreprise.id()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(ActionAudit.VALIDATION_ENTREPRISE);
                    assertThat(log.getAncienneValeur()).isEqualTo("EN_ATTENTE");
                    assertThat(log.getNouvelleValeur()).isEqualTo("VALIDEE");
                    assertThat(log.getUtilisateur()).isEqualTo(ApiDeTest.ADMIN_EMAIL);
                    assertThat(log.getDate()).isNotNull();
                });
    }

    @Test
    void changementsDeStatut_reussis_sontAudites_etEchecsIgnores() throws Exception {
        ApiDeTest.Scenario s = api.scenarioAvecCandidature();
        ApiDeTest.Compte autreEntreprise = api.inscrireEntreprise();

        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "ENTRETIEN")
                .andExpect(status().isOk());
        // Transition interdite (409) et mauvaise entreprise (403) : aucune ligne d'audit
        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "EN_ATTENTE")
                .andExpect(status().isConflict());
        api.changerStatutCandidature(s.candidatureId(), autreEntreprise.token(), "REFUSEE")
                .andExpect(status().isForbidden());
        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "ACCEPTEE")
                .andExpect(status().isOk());

        assertThat(historique(EntiteAuditee.CANDIDATURE, s.candidatureId()))
                .extracting(AuditLog::getAncienneValeur, AuditLog::getNouvelleValeur, AuditLog::getUtilisateur)
                .containsExactly(
                        tuple("EN_ATTENTE", "ENTRETIEN", s.entreprise().email()),
                        tuple("ENTRETIEN", "ACCEPTEE", s.entreprise().email()));
    }

    @Test
    void rollbackDeLaTransaction_annuleAussiLAudit() throws Exception {
        ApiDeTest.Scenario s = api.scenarioAvecCandidature();

        // L'action réussit dans la méthode, mais la transaction englobante est annulée ensuite
        transactionTemplate.executeWithoutResult(transaction -> {
            candidatureService.changerStatut(s.entreprise().id(), s.candidatureId(), StatutCandidature.ENTRETIEN);
            assertThat(historique(EntiteAuditee.CANDIDATURE, s.candidatureId())).as("visible dans la transaction")
                    .hasSize(1);
            transaction.setRollbackOnly();
        });

        assertThat(historique(EntiteAuditee.CANDIDATURE, s.candidatureId())).isEmpty();
        assertThat(candidatureRepository.findById(s.candidatureId()).orElseThrow().getStatut())
                .isEqualTo(StatutCandidature.EN_ATTENTE);
    }

    @Test
    void journal_consultableParLAdminUniquement() throws Exception {
        ApiDeTest.Compte entreprise = api.inscrireEntreprise();
        api.validerEntreprise(entreprise.id());
        String tokenAdmin = api.tokenAdmin();

        api.lire("/api/v1/admin/audit?entite=ENTREPRISE", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entiteId").value(entreprise.id().intValue()))
                .andExpect(jsonPath("$.content[0].action").value("VALIDATION_ENTREPRISE"))
                .andExpect(jsonPath("$.content[0].nouvelleValeur").value("VALIDEE"))
                .andExpect(jsonPath("$.content[*].entite", everyItem(is("ENTREPRISE"))));
        api.lire("/api/v1/admin/audit", entreprise.token()).andExpect(status().isForbidden());
    }

    @Test
    void enregistrementHorsTransaction_estRefuse() {
        // Garde-fou : l'audit ne peut pas être écrit dans une transaction à part
        assertThatThrownBy(() -> auditService.enregistrer(ActionAudit.VALIDATION_ENTREPRISE, 1L, "A", "B"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private List<AuditLog> historique(EntiteAuditee entite, Long id) {
        return auditLogRepository.findByEntiteAndEntiteIdOrderByIdAsc(entite, id);
    }
}
