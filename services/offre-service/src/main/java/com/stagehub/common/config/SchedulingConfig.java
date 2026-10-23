package com.stagehub.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les méthodes @Scheduled (exécutées par le planificateur de Spring, dans un thread dédié). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
