package com.stagehub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base des tests d'intégration (*IT, exécutés par Failsafe).
 * Le conteneur PostgreSQL est démarré une seule fois pour toutes les classes (bloc static) et arrêté
 * par Testcontainers en fin de JVM ; la configuration étant identique, Spring réutilise aussi le même contexte.
 */
@SpringBootTest(properties = {
        "stagehub.jwt.secret=secret-de-test-pour-le-parcours-complet-0123456789",
        "stagehub.admin.email=admin@test.local",
        "stagehub.admin.password=admin-test-1234",
        "stagehub.cors.allowed-origins=http://localhost:4200",
        "stagehub.cv.dossier=target/test-uploads/cv"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    // @ServiceConnection : Spring Boot lit l'URL, l'utilisateur et le mot de passe du conteneur
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    /**
     * Aucun serveur SMTP en test (ni en local, ni en CI) : l'envoi est remplacé par un mock, sur lequel les
     * tests vérifient ce qui aurait été envoyé. Déclaré ici pour garder un seul contexte Spring partagé.
     */
    @MockitoBean
    protected JavaMailSender mailSender;
}
