package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class EmailDejaUtiliseException extends StageHubException {

    public EmailDejaUtiliseException(String email) {
        super(HttpStatus.CONFLICT, "Un compte existe déjà avec l'email " + email);
    }
}
