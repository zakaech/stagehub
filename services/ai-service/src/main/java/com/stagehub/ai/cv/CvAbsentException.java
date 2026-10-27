package com.stagehub.ai.cv;

/** La candidature n'a pas de CV (ou n'existe plus) : candidature-service a répondu 404. Définitif. */
public class CvAbsentException extends RuntimeException {

    public CvAbsentException(Long candidatureId) {
        super("Aucun CV pour la candidature " + candidatureId);
    }
}
