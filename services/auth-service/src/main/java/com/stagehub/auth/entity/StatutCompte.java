package com.stagehub.auth.entity;

/**
 * État d'un compte pendant l'inscription (saga) : créé {@code EN_ATTENTE}, puis {@code ACTIF} une fois le profil créé
 * dans l'autre service. Un compte en attente ne peut pas se connecter ; s'il le reste (panne au milieu de la saga),
 * il est supprimé par {@code NettoyageInscriptionsJob}.
 */
public enum StatutCompte {
    EN_ATTENTE,
    ACTIF
}
