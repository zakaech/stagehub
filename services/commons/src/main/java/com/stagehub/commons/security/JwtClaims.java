package com.stagehub.commons.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Noms des claims du JWT StageHub (identiques à ceux du monolithe) et lecture typée. */
public final class JwtClaims {

    /** Rôle ("ETUDIANT", "ENTREPRISE", "ADMIN" ; "SERVICE" pour les appels entre services), converti en ROLE_xxx. */
    public static final String ROLE = "role";
    /** Identifiant de l'utilisateur, qui est aussi celui de son profil étudiant/entreprise dans les autres services. */
    public static final String UTILISATEUR_ID = "uid";
    /** Émetteur attendu de tous les jetons (claim iss). */
    public static final String EMETTEUR = "stagehub";
    /** Rôle des jetons de service, signés par auth-service pour les appels internes (jamais attribué à un compte). */
    public static final String ROLE_SERVICE = "SERVICE";

    private JwtClaims() {
    }

    public static Long utilisateurId(Jwt jwt) {
        Number id = jwt.getClaim(UTILISATEUR_ID);
        return id.longValue();
    }
}
