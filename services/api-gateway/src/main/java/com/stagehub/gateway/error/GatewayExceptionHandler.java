package com.stagehub.gateway.error;

import com.stagehub.commons.error.ProblemDetailExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Erreurs produites par la gateway elle-même : format commun (commons : 401, 403, 500...) + pannes propres à une
 * passerelle (service injoignable, trop lent, absent de l'annuaire). Les erreurs d'un service sont relayées telles quelles.
 */
@RestControllerAdvice
public class GatewayExceptionHandler extends ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /** Le service n'a pas répondu : connexion refusée (502) ou délai dépassé (504). */
    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail handleServiceInjoignable(ResourceAccessException ex) {
        if (ex.getCause() instanceof HttpTimeoutException || ex.getCause() instanceof SocketTimeoutException) {
            log.warn("Délai dépassé en attendant un service : {}", ex.getMessage());
            return probleme(HttpStatus.GATEWAY_TIMEOUT,
                    "Le service n'a pas répondu à temps, réessayez dans un instant", "DelaiDepasse");
        }
        log.warn("Service injoignable : {}", ex.getMessage());
        return probleme(HttpStatus.BAD_GATEWAY,
                "Le service est momentanément indisponible, réessayez dans un instant", "ServiceIndisponible");
    }

    /** Aucune instance du service dans l'annuaire Eureka (arrêté ou en cours de démarrage) : 503 de la gateway. */
    @Override
    protected ResponseEntity<Object> handleErrorResponseException(ErrorResponseException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            ProblemDetail body = probleme(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service est momentanément indisponible, réessayez dans un instant", "ServiceIndisponible");
            return handleExceptionInternal(ex, body, headers, status, request);
        }
        return super.handleErrorResponseException(ex, headers, status, request);
    }
}
