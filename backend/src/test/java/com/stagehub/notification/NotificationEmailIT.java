package com.stagehub.notification;

import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.candidature.service.CandidatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Emails : envoyés après le commit, dans le pool "mail-", jamais pour une action annulée, et un échec SMTP
 * n'affecte pas la réponse. L'envoi est asynchrone : verify(..., timeout(...)) attend qu'il ait lieu.
 */
class NotificationEmailIT extends IntegrationTestBase {

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
    void changementDeStatut_envoieLEmailALEtudiantDepuisLePoolMail() throws Exception {
        ApiDeTest.Scenario s = scenario();
        AtomicReference<String> threadEnvoi = new AtomicReference<>();
        doAnswer(invocation -> {
            threadEnvoi.set(Thread.currentThread().getName());
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "ENTRETIEN").andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(s.etudiant().email());
        assertThat(message.getValue().getSubject()).isEqualTo("Entretien proposé pour « Stage PFE Java »");
        assertThat(threadEnvoi.get()).as("envoyé hors du thread de la requête").startsWith("mail-");
    }

    @Test
    void transitionRefusee_aucunEmail() throws Exception {
        ApiDeTest.Scenario s = scenario();

        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "ACCEPTEE")
                .andExpect(status().isConflict());

        verify(mailSender, after(1000).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void transactionAnnulee_aucunEmail() throws Exception {
        ApiDeTest.Scenario s = scenario();

        transactionTemplate.executeWithoutResult(transaction -> {
            candidatureService.changerStatut(s.entreprise().id(), s.candidatureId(), StatutCandidature.ENTRETIEN);
            transaction.setRollbackOnly();
        });

        verify(mailSender, after(1000).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void echecSmtp_laRequeteReussitQuandMeme() throws Exception {
        ApiDeTest.Scenario s = scenario();
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(SimpleMailMessage.class));

        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "REFUSEE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE"));

        verify(mailSender, timeout(5000)).send(any(SimpleMailMessage.class));
        assertThat(candidatureRepository.findById(s.candidatureId()).orElseThrow().getStatut())
                .isEqualTo(StatutCandidature.REFUSEE);
    }

    @Test
    void validationEntreprise_envoieLEmailALEntreprise() throws Exception {
        ApiDeTest.Compte entreprise = api.inscrireEntreprise();

        api.validerEntreprise(entreprise.id());

        verify(mailSender, timeout(5000)).send(argThat((SimpleMailMessage m) ->
                m.getTo()[0].equals(entreprise.email()) && "Votre compte StageHub est validé".equals(m.getSubject())));
    }

    /**
     * La mise en place valide l'entreprise, ce qui envoie déjà un email (de validation) : on l'attend,
     * puis on remet le mock à zéro pour ne compter que les emails du test.
     */
    private ApiDeTest.Scenario scenario() throws Exception {
        ApiDeTest.Scenario s = api.scenarioAvecCandidature();
        verify(mailSender, timeout(5000)).send(any(SimpleMailMessage.class));
        clearInvocations(mailSender);
        return s;
    }
}
