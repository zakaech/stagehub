package com.stagehub.cv.config;

import com.stagehub.common.storage.FichierStorage;
import com.stagehub.common.storage.LocalFichierStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CvConfig {

    @Bean
    FichierStorage cvStorage(CvProperties properties) {
        return new LocalFichierStorage(properties.dossier());
    }
}
