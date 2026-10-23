package com.stagehub.offre;

import com.stagehub.IntegrationTestBase;
import com.stagehub.offre.service.FermetureOffresJob;
import com.stagehub.offre.service.OffreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ShedLock : deux déclenchements de la tâche nocturne au même moment (comme deux instances d'offre-service) ne
 * donnent qu'une exécution. Le verrou est une ligne de la table shedlock, dans la base partagée.
 */
class ShedLockIT extends IntegrationTestBase {

    @Autowired
    private FermetureOffresJob job;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private OffreService offreService;

    @Test
    void deuxDeclenchementsSimultanes_uneSeuleExecution() throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        Runnable instance = () -> {
            try {
                depart.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            job.fermerOffresExpirees();
        };
        CompletableFuture<Void> premiere = CompletableFuture.runAsync(instance);
        CompletableFuture<Void> seconde = CompletableFuture.runAsync(instance);
        depart.countDown();
        CompletableFuture.allOf(premiere, seconde).get();

        // Et un troisième déclenchement juste après : lockAtLeastFor (30 s) garde encore le verrou
        job.fermerOffresExpirees();

        verify(offreService, times(1)).fermerOffresExpirees();
        assertThat(jdbcTemplate.queryForObject("select count(*) from shedlock where name = 'fermeture-offres'", Long.class))
                .isEqualTo(1L);
    }
}
