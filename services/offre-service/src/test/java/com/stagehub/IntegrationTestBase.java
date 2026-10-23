package com.stagehub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base des tests d'intégration (*IT) d'offre-service : un PostgreSQL (Testcontainers) partagé par toutes les classes,
 * pas d'annuaire, pas de Kafka (les événements restent dans l'outbox), jetons signés par une clé de test.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "stagehub.offres.cron-fermeture=-",
        "stagehub.outbox.relais.actif=false"
})
@AutoConfigureMockMvc
@Import(JetonsDeTest.Config.class)
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;
}
