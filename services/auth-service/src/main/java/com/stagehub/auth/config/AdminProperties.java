package com.stagehub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Identifiants du compte admin créé au démarrage ({@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}). */
@ConfigurationProperties("stagehub.admin")
public record AdminProperties(String email, String password) {
}
