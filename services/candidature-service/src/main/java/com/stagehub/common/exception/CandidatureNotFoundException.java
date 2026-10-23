package com.stagehub.common.exception;

import com.stagehub.commons.error.StageHubException;
import org.springframework.http.HttpStatus;

public class CandidatureNotFoundException extends StageHubException {

    public CandidatureNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Candidature introuvable : " + id);
    }
}
