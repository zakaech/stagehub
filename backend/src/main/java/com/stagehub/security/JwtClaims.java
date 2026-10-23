package com.stagehub.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Noms des claims du JWT StageHub (émis par auth-service) et lecture typée de ces claims. */
public final class JwtClaims {

    /** Rôle de l'utilisateur ("ETUDIANT", "ENTREPRISE", "ADMIN"), converti en ROLE_xxx par Spring Security. */
    public static final String ROLE = "role";
    /** Identifiant de l'utilisateur, qui est aussi celui de son profil étudiant/entreprise. */
    public static final String UTILISATEUR_ID = "uid";
    /** Émetteur attendu (claim iss). */
    public static final String EMETTEUR = "stagehub";
    /** Rôle des jetons de service (appels internes signés par auth-service). */
    public static final String ROLE_SERVICE = "SERVICE";

    private JwtClaims() {
    }

    public static Long utilisateurId(Jwt jwt) {
        Number id = jwt.getClaim(UTILISATEUR_ID);
        return id.longValue();
    }
}
