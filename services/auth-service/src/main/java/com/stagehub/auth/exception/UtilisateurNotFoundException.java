package com.stagehub.auth.exception;

import com.stagehub.commons.error.StageHubException;
import org.springframework.http.HttpStatus;

public class UtilisateurNotFoundException extends StageHubException {

    public UtilisateurNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + id);
    }
}
