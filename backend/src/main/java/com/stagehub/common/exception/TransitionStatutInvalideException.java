package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class TransitionStatutInvalideException extends StageHubException {

    public TransitionStatutInvalideException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
