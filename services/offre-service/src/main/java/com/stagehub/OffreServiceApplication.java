package com.stagehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * offre-service : offres de stage et entreprises, extraites du monolithe (phase 7 c).
 * Classe placée dans le package racine {@code com.stagehub} : les packages repris tels quels du monolithe
 * ({@code offre}, {@code entreprise}...) et ceux des bibliothèques ({@code commons}, {@code outbox}, {@code audit})
 * sont tous scannés (composants, entités JPA, repositories), sans configuration supplémentaire.
 */
@SpringBootApplication
public class OffreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OffreServiceApplication.class, args);
    }
}
