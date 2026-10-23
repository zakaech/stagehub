package com.stagehub.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Horloge injectable, dans le fuseau de référence (stagehub.fuseau, Africa/Casablanca), quel que soit le fuseau du
 * serveur. Injectée plutôt que Instant.now() : les dates enregistrées (audit, outbox) restent testables (horloge
 * figée). Les règles de date des offres sont dans offre-service depuis la phase 7 (c).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${stagehub.fuseau}") ZoneId fuseau) {
        return Clock.system(fuseau);
    }
}
