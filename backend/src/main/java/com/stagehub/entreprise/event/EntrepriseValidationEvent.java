package com.stagehub.entreprise.event;

import com.stagehub.entreprise.entity.StatutValidation;

/** Publié quand l'admin valide ou refuse un compte entreprise ; autonome, comme CandidatureStatutChangeEvent. */
public record EntrepriseValidationEvent(Long entrepriseId, String email, String nom, StatutValidation decision) {
}
