package com.stagehub.candidature.mapper;

import com.stagehub.candidature.dto.AnalyseIaResponse;
import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.dto.OffreResumeResponse;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.common.config.MapStructConfig;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class, imports = AnalyseIaResponse.class)
public interface CandidatureMapper {

    /** Le résumé de l'offre vient d'offre-service (à jour), ou de l'instantané de la candidature en repli. */
    default CandidatureEtudiantResponse toEtudiantResponse(Candidature candidature, OffreResumeResponse offre) {
        return new CandidatureEtudiantResponse(candidature.getId(), offre, candidature.getLettreMotivation(),
                candidature.getStatut(), candidature.getDateCandidature());
    }

    /** Offre telle qu'au moment de postuler ; statut inconnu (null) : on ne sait pas si elle a été fermée depuis. */
    default OffreResumeResponse toOffreResumeDegrade(Candidature candidature) {
        return new OffreResumeResponse(candidature.getOffreId(), candidature.getOffreTitre(),
                candidature.getEntrepriseNom(), candidature.getOffreVille(), null);
    }

    @Mapping(target = "etudiantId", source = "etudiant.id")
    @Mapping(target = "etudiantNom", source = "etudiant.nom")
    @Mapping(target = "etudiantPrenom", source = "etudiant.prenom")
    @Mapping(target = "etudiantEmail", source = "etudiant.email")
    @Mapping(target = "etudiantEcole", source = "etudiant.ecole")
    @Mapping(target = "etudiantFiliere", source = "etudiant.filiere")
    @Mapping(target = "cvDisponible", expression = "java(candidature.getCvFichier() != null)")
    @Mapping(target = "analyse", expression = "java(AnalyseIaResponse.de(candidature.getAnalyse(), iaActive))")
    CandidatureEntrepriseResponse toEntrepriseResponse(Candidature candidature, @Context boolean iaActive);
}
