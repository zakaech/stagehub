package com.stagehub.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ShedLock : avec plusieurs instances d'offre-service, chacune déclencherait la tâche nocturne. Avant de l'exécuter,
 * une instance prend un verrou dans la table {@code shedlock} (base partagée) ; les autres trouvent le verrou pris et
 * sautent ce créneau. Une seule exécution par nuit, quel que soit le nombre d'instances.
 * {@code defaultLockAtMostFor} : si l'instance qui tient le verrou meurt pendant la tâche, le verrou expire seul.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class ShedLockConfig {

    /**
     * usingDbTime() : les dates du verrou viennent de l'horloge de PostgreSQL, la même pour toutes les instances (les
     * horloges des conteneurs peuvent légèrement différer).
     */
    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
