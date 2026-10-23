package com.stagehub.common.exception;

import com.stagehub.commons.error.StageHubException;
import org.springframework.http.HttpStatus;

public class FichierVideException extends StageHubException {

    public FichierVideException() {
        super(HttpStatus.BAD_REQUEST, "Le fichier envoyé est vide");
    }
}
