package com.stagehub.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final ModeleEmails modeles;
    private final String expediteur;

    public EmailService(JavaMailSender mailSender, ModeleEmails modeles,
                        @Value("${stagehub.mail.expediteur}") String expediteur) {
        this.mailSender = mailSender;
        this.modeles = modeles;
        this.expediteur = expediteur;
    }

    /**
     * Envoie un email texte construit à partir d'un modèle.
     * Un échec SMTP est <b>propagé</b> : l'email est envoyé en traitant un message Kafka, et l'exception fait rejouer
     * le message (nouvelles tentatives, puis topic d'erreurs), au lieu d'un email perdu en silence.
     */
    public void envoyer(String destinataire, String modele, Map<String, String> variables) {
        ModeleEmails.EmailRendu email = modeles.rendre(modele, variables);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject(email.objet());
        message.setText(email.corps());
        mailSender.send(message);
        log.info("Email « {} » envoyé à {}", modele, destinataire);
    }
}
