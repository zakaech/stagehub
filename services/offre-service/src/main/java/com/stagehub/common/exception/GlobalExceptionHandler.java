package com.stagehub.common.exception;

import com.stagehub.commons.error.ProblemDetailExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Format d'erreur commun (commons) + cas propres à la persistance et à la pagination. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handlePropertyReference(PropertyReferenceException ex) {
        return probleme(HttpStatus.BAD_REQUEST, "Critère de tri inconnu : " + ex.getPropertyName(), "TriInvalide");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violation de contrainte d'intégrité", ex);
        return probleme(HttpStatus.CONFLICT, "L'opération entre en conflit avec les données existantes", "ConflitDonnees");
    }
}
