package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class FichierTropVolumineuxException extends StageHubException {

    public FichierTropVolumineuxException(long tailleMaxMo) {
        super(HttpStatus.CONTENT_TOO_LARGE, "Le fichier dépasse la taille maximale autorisée (" + tailleMaxMo + " Mo)");
    }
}
