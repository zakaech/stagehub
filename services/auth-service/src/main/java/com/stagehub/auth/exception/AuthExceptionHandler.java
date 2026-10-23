package com.stagehub.auth.exception;

import com.stagehub.commons.error.ProblemDetailExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Format d'erreur commun (commons) + cas propres à la persistance. */
@RestControllerAdvice
public class AuthExceptionHandler extends ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    /** Ex. deux inscriptions simultanées avec le même email : la contrainte unique tranche. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violation de contrainte d'intégrité", ex);
        return probleme(HttpStatus.CONFLICT, "L'opération entre en conflit avec les données existantes", "ConflitDonnees");
    }
}
