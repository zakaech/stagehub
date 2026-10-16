package com.stagehub.common.exception;

import org.springframework.http.HttpStatus;

public class CvNotFoundException extends StageHubException {

    public CvNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Aucun CV disponible");
    }
}
