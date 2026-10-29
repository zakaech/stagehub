package com.stagehub;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mise à jour d'une base existante (phase 8 e) : la base de la démo et celle de la recette contenaient déjà V1 et les
 * migrations des bibliothèques (V1000 à V1002) quand V2 est arrivée. Par défaut, Flyway refuse une version plus petite
 * que la dernière appliquée : candidature-service ne démarrait plus. Ce test rejoue ce cas dans un schéma neuf, avec
 * la configuration réelle du service (emplacements et {@code spring.flyway.out-of-order}).
 */
class MigrationBaseExistanteIT extends IntegrationTestBase {

    private static final String SCHEMA = "base_existante";

    @Autowired
    private Environment environnement;

    @Test
    void nouvelleMigrationDuService_baseDejaAuxVersionsDesBibliotheques_appliquee(@TempDir Path anciennes)
            throws IOException {
        // 1. Base « d'avant » : V1 du service + V1000, V1001, V1002 des bibliothèques, sans V2
        copier("classpath:db/migration/V1__*.sql", anciennes);
        copier("classpath:db/outbox/*.sql", anciennes);
        copier("classpath:db/audit/*.sql", anciennes);
        flyway("filesystem:" + anciennes).load().migrate();

        String[] emplacements = environnement.getRequiredProperty("spring.flyway.locations", String[].class);
        // 2. Sans le réglage : le refus constaté en réel
        assertThatThrownBy(() -> flyway(emplacements).outOfOrder(false).load().migrate())
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("Migrations have failed validation");

        // 3. Avec la configuration du service : V2 appliquée
        boolean horsOrdre = environnement.getProperty("spring.flyway.out-of-order", Boolean.class, false);
        Flyway service = flyway(emplacements).outOfOrder(horsOrdre).load();
        service.migrate();
        // (la ligne « création du schéma » de l'historique n'a pas de version)
        assertThat(Arrays.stream(service.info().applied()).filter(m -> m.getVersion() != null)
                .map(m -> m.getVersion().getVersion()))
                .containsExactlyInAnyOrder("1", "1000", "1001", "1002", "2");
        assertThat(Arrays.stream(service.info().pending()).map(MigrationInfo::getScript)).isEmpty();
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(String... emplacements) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(SCHEMA)
                .locations(emplacements);
    }

    private static void copier(String motif, Path dossier) throws IOException {
        for (Resource ressource : new PathMatchingResourcePatternResolver().getResources(motif)) {
            try (InputStream contenu = ressource.getInputStream()) {
                Files.copy(contenu, dossier.resolve(ressource.getFilename()));
            }
        }
    }
}
