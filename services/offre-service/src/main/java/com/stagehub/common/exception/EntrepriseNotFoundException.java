package com.stagehub.common.exception;

import com.stagehub.commons.error.StageHubException;

import org.springframework.http.HttpStatus;

public class EntrepriseNotFoundException extends StageHubException {

    public EntrepriseNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Entreprise introuvable : " + id);
    }
}
