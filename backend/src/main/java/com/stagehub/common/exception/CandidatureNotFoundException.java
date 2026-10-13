package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class CandidatureNotFoundException extends StageHubException {

    public CandidatureNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Candidature introuvable : " + id);
    }
}
