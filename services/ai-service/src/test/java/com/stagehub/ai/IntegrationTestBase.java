package com.stagehub.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base des tests d'intégration d'ai-service : vrais PostgreSQL et Kafka (Testcontainers), faux auth-service et faux
 * candidature-service (WireMock). Même configuration pour toutes les classes : un seul contexte Spring, partagé.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        // Nouvelles tentatives rapides en test
        "stagehub.erreurs.tentatives=2",
        "stagehub.erreurs.premier-delai=200ms",
        "spring.security.oauth2.client.registration.auth-service.client-secret=" + IntegrationTestBase.SECRET_AI_SERVICE
})
public abstract class IntegrationTestBase {

    protected static final String SECRET_AI_SERVICE = "secret-de-test-ai-service-0123456789abcdef";

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");
    /** Faux auth-service : délivre les jetons de service (POST /oauth2/token). */
    protected static final WireMockServer AUTH = new WireMockServer(options().dynamicPort());
    /** Faux candidature-service : sert les CV (GET /internal/candidatures/{id}/cv). */
    protected static final WireMockServer CANDIDATURES = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        KAFKA.start();
        AUTH.start();
        CANDIDATURES.start();
    }

    @DynamicPropertySource
    static void fauxServices(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.auth-service.token-uri",
                () -> "http://localhost:" + AUTH.port() + "/oauth2/token");
        // Adresse fixe pour le client Feign : pas d'annuaire Eureka en test
        registry.add("spring.cloud.openfeign.client.config.candidatures.url",
                () -> "http://localhost:" + CANDIDATURES.port());
    }
}
