package com.stagehub.offre.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâche planifiée : chaque nuit, ferme les offres dont la date limite est passée.
 *
 * <p>Cron "0 5 0 * * *" = à 00:05:00 chaque jour (secondes, minutes, heures, jour, mois, jour de semaine),
 * évalué dans le fuseau stagehub.fuseau (Africa/Casablanca) et non celui du serveur.
 * La valeur "-" désactive la tâche (utilisé dans les tests).
 *
 * <p>Phase 7 : avec plusieurs instances d'offre-service, chacune déclencherait la tâche. {@code @SchedulerLock} (ShedLock)
 * ne laisse passer que celle qui obtient le verrou « fermeture-offres » ; {@code lockAtLeastFor} garde ce verrou au
 * moins 30 s, pour qu'une instance dont le planificateur se déclenche un peu plus tard ne relance pas la tâche juste
 * après. Cette durée doit rester plus courte que l'intervalle entre deux créneaux : à 1 min, un cron à la minute
 * (démonstration) ne s'exécutait qu'une minute sur deux, le verrou couvrant encore le créneau suivant.
 */
@Component
public class FermetureOffresJob {

    private final OffreService offreService;

    public FermetureOffresJob(OffreService offreService) {
        this.offreService = offreService;
    }

    @Scheduled(cron = "${stagehub.offres.cron-fermeture}", zone = "${stagehub.fuseau}")
    @SchedulerLock(name = "fermeture-offres", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void fermerOffresExpirees() {
        offreService.fermerOffresExpirees();
    }
}
