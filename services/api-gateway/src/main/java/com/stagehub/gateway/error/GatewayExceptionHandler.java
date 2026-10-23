package com.stagehub.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Erreurs produites par la gateway elle-même, au format ProblemDetail (RFC 9457) avec un champ {@code code}
 * stable, comme dans les services. Les erreurs renvoyées par un service sont relayées telles quelles.
 */
@RestControllerAdvice
public class GatewayExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return probleme(HttpStatus.UNAUTHORIZED, "Authentification requise ou jeton invalide", "NonAuthentifie");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return probleme(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits pour accéder à cette ressource", "AccesRefuse");
    }

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

    private static ProblemDetail probleme(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
