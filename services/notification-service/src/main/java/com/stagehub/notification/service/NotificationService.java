package com.stagehub.notification.service;

import com.stagehub.notification.event.CandidatureStatutChange;
import com.stagehub.notification.event.EntrepriseValidation;
import com.stagehub.notification.repository.EvenementTraiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Transforme un événement en email, <b>une seule fois</b> par événement (consommateur idempotent) :
 * <ol>
 *   <li>inscription de l'id de l'événement (0 ligne ajoutée = déjà traité : on s'arrête) ;</li>
 *   <li>envoi de l'email ; s'il échoue, la transaction est annulée (l'inscription aussi) et l'exception fait
 *       rejouer le message par Kafka.</li>
 * </ol>
 * Seul cas de doublon restant : email parti puis panne avant le commit (rare). Avec un serveur SMTP, « exactement
 * une fois » est impossible : on choisit « au moins une fois, et presque toujours une seule ».
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EvenementTraiteRepository evenementTraiteRepository;
    private final EmailService emailService;
    private final String urlFrontend;
    private final Clock clock;

    public NotificationService(EvenementTraiteRepository evenementTraiteRepository, EmailService emailService,
                               @Value("${stagehub.mail.url-frontend}") String urlFrontend, Clock clock) {
        this.evenementTraiteRepository = evenementTraiteRepository;
        this.emailService = emailService;
        this.urlFrontend = urlFrontend;
        this.clock = clock;
    }

    @Transactional
    public void surChangementStatutCandidature(UUID evenementId, CandidatureStatutChange evenement) {
        if (dejaTraite(evenementId, "CandidatureStatutChange")) {
            return;
        }
        // Modèles : candidature-entretien, candidature-acceptee, candidature-refusee
        emailService.envoyer(evenement.etudiantEmail(), "candidature-" + evenement.nouveauStatut().toLowerCase(Locale.ROOT),
                Map.of("prenom", evenement.etudiantPrenom(),
                        "offreTitre", evenement.offreTitre(),
                        "entrepriseNom", evenement.entrepriseNom(),
                        "lienCandidatures", urlFrontend + "/etudiant/candidatures",
                        "lienOffres", urlFrontend + "/offres"));
    }

    @Transactional
    public void surValidationEntreprise(UUID evenementId, EntrepriseValidation evenement) {
        if (dejaTraite(evenementId, "EntrepriseValidation")) {
            return;
        }
        emailService.envoyer(evenement.email(), "entreprise-" + evenement.decision().toLowerCase(Locale.ROOT),
                Map.of("nom", evenement.nom(), "lienEspace", urlFrontend + "/entreprise/offres"));
    }

    private boolean dejaTraite(UUID evenementId, String type) {
        if (evenementTraiteRepository.inscrire(evenementId, type, clock.instant()) == 0) {
            log.info("Événement {} ({}) déjà traité : doublon ignoré", evenementId, type);
            return true;
        }
        return false;
    }
}
