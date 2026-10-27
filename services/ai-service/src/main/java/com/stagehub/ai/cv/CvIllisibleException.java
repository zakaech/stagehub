package com.stagehub.ai.cv;

/**
 * CV dont on ne peut pas tirer de texte : pas un PDF, PDF abîmé ou protégé par un mot de passe, ou PDF sans texte
 * (CV scanné : pas d'OCR). Définitif : réessayer ne changerait rien.
 */
public class CvIllisibleException extends RuntimeException {

    public CvIllisibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
