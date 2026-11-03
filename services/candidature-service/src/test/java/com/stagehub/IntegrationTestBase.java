package com.stagehub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base des tests d'intégration (*IT, exécutés par Failsafe).
 * Le conteneur PostgreSQL est démarré une seule fois pour toutes les classes (bloc static) et arrêté
 * par Testcontainers en fin de JVM ; la configuration étant identique, Spring réutilise aussi le même contexte.
 */
@SpringBootTest(properties = {
        "stagehub.cv.dossier=target/test-uploads/cv",
        // Pas d'annuaire : le client Feign appelle directement le faux offre-service (FauxOffreService)
        "eureka.client.enabled=false",
        // Pas de Kafka pour la plupart des tests : les événements restent dans l'outbox (OutboxIT teste le relais),
        // et le consommateur des analyses ne démarre pas (AnalyseResultatIT le teste)
        "stagehub.outbox.relais.actif=false",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
// Jetons signés par une clé de test à la place d'auth-service
@Import(JetonsDeTest.Config.class)
public abstract class IntegrationTestBase {

    // @ServiceConnection : Spring Boot lit l'URL, l'utilisateur et le mot de passe du conteneur
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    /** Le client Feign d'offre-service (contextId « offres ») appelle le faux service au lieu de passer par Eureka. */
    @DynamicPropertySource
    static void fauxOffreService(DynamicPropertyRegistry proprietes) {
        proprietes.add("spring.cloud.openfeign.client.config.offres.url", FauxOffreService::url);
    }
}
