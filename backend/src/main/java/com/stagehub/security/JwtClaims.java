package com.stagehub.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Noms des claims personnalisés du JWT StageHub et lecture typée de ces claims. */
public final class JwtClaims {

    /** Rôle de l'utilisateur ("ETUDIANT", "ENTREPRISE", "ADMIN"), converti en ROLE_xxx par Spring Security. */
    public static final String ROLE = "role";

    /** Identifiant de l'utilisateur, qui est aussi celui de son profil étudiant/entreprise (PK partagée). */
    public static final String UTILISATEUR_ID = "uid";

    private JwtClaims() {
    }

    public static Long utilisateurId(Jwt jwt) {
        Number id = jwt.getClaim(UTILISATEUR_ID);
        return id.longValue();
    }
}
