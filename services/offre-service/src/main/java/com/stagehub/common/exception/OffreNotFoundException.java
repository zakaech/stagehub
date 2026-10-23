package com.stagehub.common.exception;

import com.stagehub.commons.error.StageHubException;

import org.springframework.http.HttpStatus;

public class OffreNotFoundException extends StageHubException {

    public OffreNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Offre introuvable : " + id);
    }
}
