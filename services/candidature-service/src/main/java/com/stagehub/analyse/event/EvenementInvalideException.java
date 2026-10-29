package com.stagehub.analyse.event;

/** Message illisible ou incomplet : envoyé dans le topic « .DLT » sans nouvelle tentative (il ne changera pas). */
public class EvenementInvalideException extends RuntimeException {

    public EvenementInvalideException(String message, Throwable cause) {
        super(message, cause);
    }
}
