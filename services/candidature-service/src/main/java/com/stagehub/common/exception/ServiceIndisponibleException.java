package com.stagehub.common.exception;

import com.stagehub.commons.error.StageHubException;
import org.springframework.http.HttpStatus;

/** Un service appelé (offre-service) ne répond pas : l'opération n'a pas eu lieu, on peut réessayer. */
public class ServiceIndisponibleException extends StageHubException {

    public ServiceIndisponibleException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
