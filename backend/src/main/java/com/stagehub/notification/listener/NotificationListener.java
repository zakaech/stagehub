package com.stagehub.notification.listener;

import com.stagehub.candidature.event.CandidatureStatutChangeEvent;
import com.stagehub.entreprise.event.EntrepriseValidationEvent;
import com.stagehub.notification.config.AsyncConfig;
import com.stagehub.notification.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Locale;
import java.util.Map;

/**
 * Transforme les événements métier en emails.
 *
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)} : l'événement est retenu jusqu'au commit de la
 *       transaction qui l'a publié, et abandonné en cas de rollback. Pas d'email pour un changement annulé.</li>
 *   <li>{@code @Async} : l'envoi se fait dans le pool "mailExecutor" ; la réponse HTTP n'attend pas le SMTP.</li>
 * </ul>
 */
@Component
public class NotificationListener {

    private final EmailService emailService;
    private final String urlFrontend;

    public NotificationListener(EmailService emailService, @Value("${stagehub.mail.url-frontend}") String urlFrontend) {
        this.emailService = emailService;
        this.urlFrontend = urlFrontend;
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surChangementStatutCandidature(CandidatureStatutChangeEvent evenement) {
        // Modèles : candidature-entretien, candidature-acceptee, candidature-refusee
        String modele = "candidature-" + evenement.nouveauStatut().name().toLowerCase(Locale.ROOT);
        emailService.envoyer(evenement.etudiantEmail(), modele, Map.of(
                "prenom", evenement.etudiantPrenom(),
                "offreTitre", evenement.offreTitre(),
                "entrepriseNom", evenement.entrepriseNom(),
                "lienCandidatures", urlFrontend + "/etudiant/candidatures",
                "lienOffres", urlFrontend + "/offres"));
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surValidationEntreprise(EntrepriseValidationEvent evenement) {
        String modele = "entreprise-" + evenement.decision().name().toLowerCase(Locale.ROOT);
        emailService.envoyer(evenement.email(), modele, Map.of(
                "nom", evenement.nom(),
                "lienEspace", urlFrontend + "/entreprise/offres"));
    }
}
