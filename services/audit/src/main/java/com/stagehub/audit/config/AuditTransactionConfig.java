package com.stagehub.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Fixe l'ordre du proxy transactionnel par rapport à AuditAspect (ordre maximal) : plus l'ordre est petit, plus le
 * proxy est "extérieur". L'audit s'exécute donc à l'intérieur de la transaction de l'action qu'il enregistre.
 * Fournie par la bibliothèque : un service qui utilise @Audite a besoin de cet ordre (Spring Boot n'active plus sa
 * propre configuration transactionnelle quand cette annotation est présente).
 */
@Configuration
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 10, proxyTargetClass = true)
public class AuditTransactionConfig {
}
