package com.stagehub.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Horloge injectable, dans le fuseau de référence (stagehub.fuseau, Africa/Casablanca) : "aujourd'hui" a le même
 * sens pour postuler, rouvrir une offre et la fermeture nocturne, quel que soit le fuseau du serveur.
 * Injectée plutôt que LocalDate.now() : les règles basées sur la date restent testables (horloge figée).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${stagehub.fuseau}") ZoneId fuseau) {
        return Clock.system(fuseau);
    }
}
