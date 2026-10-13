package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class UtilisateurNotFoundException extends StageHubException {

    public UtilisateurNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + id);
    }
}
