package com.stagehub.auth.dto;

import com.stagehub.auth.entity.Role;

/** Réponse du login. {@code expiresIn} est en secondes, comme dans OAuth2. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, Role role) {
}
