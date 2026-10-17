package com.stagehub.notification.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService service = new EmailService(mailSender,
            new ModeleEmails(new DefaultResourceLoader()), "no-reply@stagehub.local");

    private static final Map<String, String> VARIABLES = Map.of("nom", "TechNova", "lienEspace", "http://x/espace");

    @Test
    void envoyer_construitLeMessageDepuisLeModele() {
        boolean envoye = service.envoyer("rh@technova.ma", "entreprise-validee", VARIABLES);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(envoye).isTrue();
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@stagehub.local");
        assertThat(message.getValue().getTo()).containsExactly("rh@technova.ma");
        assertThat(message.getValue().getSubject()).isEqualTo("Votre compte StageHub est validé");
        assertThat(message.getValue().getText()).contains("Le compte de TechNova a été validé", "http://x/espace");
    }

    @Test
    void echecSmtp_estLogueSansException() {
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(SimpleMailMessage.class));

        // Aucune exception ne remonte : l'action métier, déjà validée, n'est pas affectée
        assertThat(service.envoyer("rh@technova.ma", "entreprise-validee", VARIABLES)).isFalse();
    }
}
