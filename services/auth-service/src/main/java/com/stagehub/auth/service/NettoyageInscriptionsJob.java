package com.stagehub.auth.service;

import com.stagehub.auth.client.ProfilEntrepriseClient;
import com.stagehub.auth.client.ProfilEtudiantClient;
import com.stagehub.auth.entity.StatutCompte;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Termine les sagas d'inscription interrompues (auth-service arrêté entre la création du profil et l'activation du
 * compte, ou compensation qui n'a pas pu joindre le service des profils) : un compte resté EN_ATTENTE plus de
 * {@code stagehub.inscription.delai-abandon} voit son profil puis lui-même supprimés.
 * Idempotent (supprimer un profil absent ne fait rien) : sans risque si plusieurs instances l'exécutent.
 */
@Component
public class NettoyageInscriptionsJob {

    private static final Logger log = LoggerFactory.getLogger(NettoyageInscriptionsJob.class);

    private final UtilisateurRepository utilisateurRepository;
    private final CompteService compteService;
    private final ProfilEtudiantClient profilEtudiantClient;
    private final ProfilEntrepriseClient profilEntrepriseClient;
    private final Duration delaiAbandon;
    private final Clock clock;

    public NettoyageInscriptionsJob(UtilisateurRepository utilisateurRepository, CompteService compteService,
                                    ProfilEtudiantClient profilEtudiantClient,
                                    ProfilEntrepriseClient profilEntrepriseClient,
                                    @Value("${stagehub.inscription.delai-abandon}") Duration delaiAbandon, Clock clock) {
        this.utilisateurRepository = utilisateurRepository;
        this.compteService = compteService;
        this.profilEtudiantClient = profilEtudiantClient;
        this.profilEntrepriseClient = profilEntrepriseClient;
        this.delaiAbandon = delaiAbandon;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${stagehub.inscription.intervalle-nettoyage}",
               initialDelayString = "${stagehub.inscription.intervalle-nettoyage}")
    public void nettoyer() {
        List<Utilisateur> abandonnes = utilisateurRepository
                .findByStatutAndDateCreationBefore(StatutCompte.EN_ATTENTE, clock.instant().minus(delaiAbandon));
        for (Utilisateur compte : abandonnes) {
            try {
                switch (compte.getRole()) {
                    case ETUDIANT -> profilEtudiantClient.supprimer(compte.getId());
                    case ENTREPRISE -> profilEntrepriseClient.supprimer(compte.getId());
                    case ADMIN -> {
                        // Jamais EN_ATTENTE (créé actif, sans profil) : rien à compenser ailleurs
                    }
                }
                compteService.supprimer(compte.getId());
                log.info("Inscription abandonnée nettoyée : compte {} ({})", compte.getId(), compte.getEmail());
            } catch (RuntimeException e) {
                // Le service des profils est peut-être arrêté : on réessaiera au prochain passage
                log.warn("Nettoyage du compte {} reporté : {}", compte.getId(), e.getMessage());
            }
        }
    }
}
