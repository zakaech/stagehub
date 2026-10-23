package com.stagehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * candidature-service : profils étudiants, CV et candidatures (phase 7 d, dernier morceau du monolithe).
 * Dans le package com.stagehub : il scanne aussi commons et les bibliothèques outbox et audit.
 */
@SpringBootApplication
// Client HTTP déclaratif vers offre-service (OffreClient)
@EnableFeignClients
@ConfigurationPropertiesScan
public class CandidatureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandidatureServiceApplication.class, args);
    }
}
