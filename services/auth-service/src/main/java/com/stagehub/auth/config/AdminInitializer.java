package com.stagehub.auth.config;

import com.stagehub.auth.entity.Role;
import com.stagehub.auth.service.CompteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Pas d'inscription publique pour l'admin (spec §4.5) : le compte est créé au démarrage s'il n'existe pas encore,
 * directement ACTIF (il n'a pas de profil dans un autre service). Un compte existant n'est jamais modifié.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminProperties properties;
    private final CompteService compteService;

    public AdminInitializer(AdminProperties properties, CompteService compteService) {
        this.properties = properties;
        this.compteService = compteService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.warn("ADMIN_EMAIL ou ADMIN_PASSWORD non défini : aucun compte admin n'est créé");
            return;
        }
        if (compteService.existe(properties.email())) {
            log.info("Compte admin {} déjà présent", properties.email());
            return;
        }
        compteService.creerActif(properties.email(), properties.password(), Role.ADMIN);
        log.info("Compte admin {} créé", properties.email());
    }
}
