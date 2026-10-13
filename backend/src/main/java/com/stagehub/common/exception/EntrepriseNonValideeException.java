package com.stagehub.common.exception;

import com.stagehub.entreprise.entity.StatutValidation;
import org.springframework.http.HttpStatus;

public class EntrepriseNonValideeException extends StageHubException {

    public EntrepriseNonValideeException(StatutValidation statut) {
        super(HttpStatus.FORBIDDEN, switch (statut) {
            case EN_ATTENTE -> "Votre compte entreprise est en attente de validation par l'administrateur : "
                    + "vous ne pouvez pas encore publier d'offre";
            case REFUSEE -> "Votre compte entreprise a été refusé par l'administrateur : vous ne pouvez pas publier d'offre";
            case VALIDEE -> throw new IllegalArgumentException("Une entreprise validée n'est pas concernée");
        });
    }
}
