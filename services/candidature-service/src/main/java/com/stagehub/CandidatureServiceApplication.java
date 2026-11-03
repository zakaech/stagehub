package com.stagehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * candidature-service : profils étudiants, CV et candidatures.
 * Dans le package com.stagehub : il scanne aussi commons et les bibliothèques outbox et audit.
 */
@SpringBootApplication
@EnableFeignClients
@ConfigurationPropertiesScan
public class CandidatureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandidatureServiceApplication.class, args);
    }
}
