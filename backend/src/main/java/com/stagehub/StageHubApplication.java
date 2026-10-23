package com.stagehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// Phase 7 (c) : clients HTTP déclaratifs vers les autres services (OffreClient)
@EnableFeignClients
@ConfigurationPropertiesScan
public class StageHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StageHubApplication.class, args);
    }
}
