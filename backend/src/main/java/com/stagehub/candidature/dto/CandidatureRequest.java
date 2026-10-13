package com.stagehub.candidature.dto;

import jakarta.validation.constraints.Size;

/** Corps optionnel de la candidature : la lettre de motivation n'est pas obligatoire. */
public record CandidatureRequest(@Size(max = 5000) String lettreMotivation) {
}
