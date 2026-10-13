package com.stagehub.auth.config;

import com.stagehub.auth.entity.Role;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Pas d'inscription publique pour l'admin (spec §4.5) : le compte est créé au démarrage
 * s'il n'existe pas encore. Un compte existant n'est jamais modifié.
 */
@Component
@Order(1)
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminProperties properties;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(AdminProperties properties, UtilisateurRepository utilisateurRepository,
                            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.warn("ADMIN_EMAIL ou ADMIN_PASSWORD non défini : aucun compte admin n'est créé");
            return;
        }

        String email = Utilisateur.normaliserEmail(properties.email());
        if (utilisateurRepository.existsByEmail(email)) {
            log.info("Compte admin {} déjà présent", email);
            return;
        }

        Utilisateur admin = new Utilisateur();
        admin.setEmail(email);
        admin.setMotDePasse(passwordEncoder.encode(properties.password()));
        admin.setRole(Role.ADMIN);
        utilisateurRepository.save(admin);
        log.info("Compte admin {} créé", email);
    }
}
