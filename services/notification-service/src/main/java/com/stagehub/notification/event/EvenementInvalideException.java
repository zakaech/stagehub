package com.stagehub.notification.event;

/**
 * Message illisible (JSON mal formé, champ obligatoire ou id d'événement absent). Le relire ne changera rien :
 * il part directement dans le topic d'erreurs, sans nouvelles tentatives.
 */
public class EvenementInvalideException extends RuntimeException {

    public EvenementInvalideException(String message, Throwable cause) {
        super(message, cause);
    }
}
