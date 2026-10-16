package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class FichierNonPdfException extends StageHubException {

    public FichierNonPdfException() {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Le fichier n'est pas un PDF valide");
    }
}
