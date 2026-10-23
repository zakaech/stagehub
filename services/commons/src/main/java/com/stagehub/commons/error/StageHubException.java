package com.stagehub.commons.error;

import org.springframework.http.HttpStatus;

/**
 * Racine des exceptions métier de tous les services. Chaque sous-classe porte son code HTTP :
 * {@link ProblemDetailExceptionHandler} la traduit en ProblemDetail sans switch à maintenir.
 */
public abstract class StageHubException extends RuntimeException {

    private final HttpStatus status;

    protected StageHubException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Code stable exposé au client (ex. "EmailDejaUtilise"), dérivé du nom de la classe. */
    public String getCode() {
        return getClass().getSimpleName().replaceFirst("Exception$", "");
    }
}
