package com.stagehub.offre.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FermetureOffresJobTest {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    /** Valeur de stagehub.offres.cron-fermeture dans application.yml. */
    private static final String CRON = "0 5 0 * * *";

    @Test
    void cron_declencheChaqueJourA0h05HeureDeCasablanca() {
        ZonedDateTime veilleAuSoir = ZonedDateTime.of(2026, 10, 17, 23, 30, 0, 0, CASABLANCA);

        ZonedDateTime prochaine = CronExpression.parse(CRON).next(veilleAuSoir);

        assertThat(prochaine).isEqualTo(ZonedDateTime.of(2026, 10, 18, 0, 5, 0, 0, CASABLANCA));
    }

    @Test
    void tache_estPlanifieeDansLeFuseauConfigure() throws NoSuchMethodException {
        Scheduled planification = FermetureOffresJob.class.getMethod("fermerOffresExpirees")
                .getAnnotation(Scheduled.class);

        assertThat(planification.cron()).isEqualTo("${stagehub.offres.cron-fermeture}");
        assertThat(planification.zone()).isEqualTo("${stagehub.fuseau}");
    }

    @Test
    void tache_delegueAuService() {
        OffreService service = mock(OffreService.class);

        new FermetureOffresJob(service).fermerOffresExpirees();

        verify(service).fermerOffresExpirees();
    }
}
