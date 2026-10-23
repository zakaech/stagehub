package com.stagehub.common.exception;

import com.stagehub.commons.error.StageHubException;
import org.springframework.http.HttpStatus;

public class CandidatureDejaExistanteException extends StageHubException {

    public CandidatureDejaExistanteException() {
        super(HttpStatus.CONFLICT, "Vous avez déjà postulé à cette offre");
    }
}
