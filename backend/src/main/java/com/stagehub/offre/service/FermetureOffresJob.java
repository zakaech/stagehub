package com.stagehub.offre.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâche planifiée : chaque nuit, ferme les offres dont la date limite est passée.
 *
 * <p>Cron "0 5 0 * * *" = à 00:05:00 chaque jour (secondes, minutes, heures, jour, mois, jour de semaine),
 * évalué dans le fuseau stagehub.fuseau (Africa/Casablanca) et non celui du serveur.
 * La valeur "-" désactive la tâche (utilisé dans les tests).
 *
 * <p>Avec plusieurs instances (phase 7), chacune exécuterait la tâche : il faudra un verrou partagé (ShedLock).
 */
@Component
public class FermetureOffresJob {

    private final OffreService offreService;

    public FermetureOffresJob(OffreService offreService) {
        this.offreService = offreService;
    }

    @Scheduled(cron = "${stagehub.offres.cron-fermeture}", zone = "${stagehub.fuseau}")
    public void fermerOffresExpirees() {
        offreService.fermerOffresExpirees();
    }
}
