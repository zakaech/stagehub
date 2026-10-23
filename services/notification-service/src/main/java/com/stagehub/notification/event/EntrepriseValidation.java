package com.stagehub.notification.event;

/** Message du topic {@code stagehub.entreprise.validation} (copie propre à ce service, comme CandidatureStatutChange). */
public record EntrepriseValidation(Long entrepriseId, String email, String nom, String decision) {
}
