package com.stagehub.auth.exception;

import com.stagehub.commons.error.StageHubException;
import org.springframework.http.HttpStatus;

/** Le service des profils n'a pas répondu (ou a refusé) : l'opération n'a pas eu lieu, on peut réessayer. */
public class ServiceIndisponibleException extends StageHubException {

    public ServiceIndisponibleException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
