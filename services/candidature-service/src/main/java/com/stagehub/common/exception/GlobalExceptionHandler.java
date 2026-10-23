package com.stagehub.common.exception;

import com.stagehub.commons.error.ProblemDetailExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Erreurs propres à candidature-service ; le format commun (ProblemDetail + code, validation, 401/403, erreurs
 * métier) vient de {@link ProblemDetailExceptionHandler} (module commons).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Fichier refusé par Spring avant même d'atteindre le contrôleur (spring.servlet.multipart.max-file-size). */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        ProblemDetail body = probleme(HttpStatus.CONTENT_TOO_LARGE,
                "Le fichier dépasse la taille maximale autorisée", "FichierTropVolumineux");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

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
