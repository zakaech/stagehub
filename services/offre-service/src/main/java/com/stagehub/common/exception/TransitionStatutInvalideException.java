package com.stagehub.common.exception;

import com.stagehub.commons.error.StageHubException;

import org.springframework.http.HttpStatus;

public class TransitionStatutInvalideException extends StageHubException {

    public TransitionStatutInvalideException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
