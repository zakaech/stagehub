package com.stagehub.commons.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.stream.Stream;

/**
 * Format d'erreur commun à tous les services : ProblemDetail (RFC 9457) avec un champ {@code code} stable, et la liste
 * {@code erreurs} des champs invalides pour une validation échouée. Même format que le monolithe : le frontend ne voit
 * pas de différence. Chaque service l'active en déclarant une sous-classe annotée {@code @RestControllerAdvice}
 * (et peut y ajouter ses propres cas).
 */
public abstract class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

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

    /** Adresse inconnue (aucune route ni aucun contrôleur) : même format, sans le message technique de Spring. */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        ProblemDetail body = probleme(HttpStatus.NOT_FOUND, "Aucune ressource à cette adresse", "RessourceIntrouvable");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Compte inconnu, mot de passe faux ou compte pas encore activé : même réponse (on ne révèle rien). */
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    ProblemDetail handleBadCredentials(AuthenticationException ex) {
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

    @ExceptionHandler(Exception.class)
    ProblemDetail handleInattendue(Exception ex) {
        log.error("Erreur inattendue", ex);
        return probleme(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue", "ErreurInterne");
    }

    public static ProblemDetail probleme(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
