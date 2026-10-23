package com.stagehub.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Lancé une fois par docker compose (profil « demo ») après le démarrage des services, puis s'arrête.
 * Code de sortie 0 si les données sont créées (ou déjà présentes), 1 sinon.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoSeederApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(DemoSeederApplication.class, args)));
    }
}
