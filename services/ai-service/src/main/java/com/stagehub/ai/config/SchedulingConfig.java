package com.stagehub.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tâches planifiées : relais de l'outbox (bibliothèque) et analyse des demandes. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
