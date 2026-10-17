package com.stagehub.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Fixe l'ordre du proxy transactionnel par rapport à nos aspects. Plus l'ordre est petit, plus le proxy est
 * "extérieur" : MesureTempsAspect (ordre minimal) → transaction (ici) → AuditAspect (ordre maximal).
 * L'audit s'exécute donc à l'intérieur de la transaction de l'action qu'il enregistre.
 * Spring Boot n'active plus sa propre configuration quand cette annotation est présente.
 */
@Configuration
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 10, proxyTargetClass = true)
public class TransactionConfig {
}
