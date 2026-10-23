package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class ProfilDejaExistantException extends StageHubException {

    public ProfilDejaExistantException(Long id) {
        super(HttpStatus.CONFLICT, "Un profil existe déjà pour le compte " + id);
    }
}
