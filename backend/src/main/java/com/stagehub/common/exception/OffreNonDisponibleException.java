package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class OffreNonDisponibleException extends StageHubException {

    public OffreNonDisponibleException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
