package com.stagehub.audit.aspect;

import com.stagehub.audit.entity.EntiteAuditee;

import java.util.Optional;

/**
 * Sait lire l'état "auditable" d'une entité (son statut) à partir de son id. Chaque fonctionnalité fournit le
 * sien : l'aspect reste générique et ne connaît aucune entité métier.
 */
public interface LecteurEtatAudit {

    EntiteAuditee entite();

    Optional<String> lireEtat(Long id);
}
