package com.stagehub.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.stream.Stream;

/**
 * Traduit toutes les erreurs en ProblemDetail (RFC 9457).
 * Hériter de {@link ResponseEntityExceptionHandler} couvre déjà les erreurs Spring MVC
 * (JSON illisible, méthode non supportée, 404 de route...) au même format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ChampInvalide(String champ, String message) {
    }

    @ExceptionHandler(StageHubException.class)
    ProblemDetail handleStageHub(StageHubException ex) {
        return probleme(ex.getStatus(), ex.getMessage(), ex.getCode());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<ChampInvalide> erreurs = Stream.concat(
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> new ChampInvalide(e.getField(), e.getDefaultMessage())),
                ex.getBindingResult().getGlobalErrors().stream()
                        .map(e -> new ChampInvalide(e.getObjectName(), e.getDefaultMessage()))
        ).toList();

        ProblemDetail body = probleme(HttpStatus.BAD_REQUEST, "La requête contient des champs invalides", "ValidationEchouee");
        body.setProperty("erreurs", erreurs);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail body = probleme(HttpStatus.BAD_REQUEST,
                "Corps de requête illisible : JSON mal formé ou valeur incompatible (ex. statut inconnu)", "CorpsIllisible");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Fichier refusé par Spring avant même d'atteindre le contrôleur (spring.servlet.multipart.max-file-size). */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        ProblemDetail body = probleme(HttpStatus.CONTENT_TOO_LARGE,
                "Le fichier dépasse la taille maximale autorisée", "FichierTropVolumineux");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return probleme(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect", "IdentifiantsInvalides");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return probleme(HttpStatus.UNAUTHORIZED, "Authentification requise ou jeton invalide", "NonAuthentifie");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return probleme(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits pour accéder à cette ressource", "AccesRefuse");
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

    @ExceptionHandler(Exception.class)
    ProblemDetail handleInattendue(Exception ex) {
        log.error("Erreur inattendue", ex);
        return probleme(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue", "ErreurInterne");
    }

    private static ProblemDetail probleme(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
