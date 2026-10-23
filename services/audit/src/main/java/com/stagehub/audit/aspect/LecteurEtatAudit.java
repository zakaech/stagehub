package com.stagehub.audit.aspect;

import java.util.Optional;

/**
 * Sait lire l'état "auditable" d'une entité (son statut) à partir de son id. Chaque service fournit les siens :
 * l'aspect reste générique et ne connaît aucune entité métier.
 */
public interface LecteurEtatAudit {

    /** Type d'entité lu (ex. "ENTREPRISE"), le même que dans {@link Audite#entite()}. */
    String entite();

    Optional<String> lireEtat(Long id);
}
