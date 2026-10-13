package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class AccesNonAutoriseException extends StageHubException {

    public AccesNonAutoriseException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
