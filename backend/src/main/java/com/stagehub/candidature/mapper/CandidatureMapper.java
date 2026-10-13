package com.stagehub.candidature.mapper;

import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.common.config.MapStructConfig;
import com.stagehub.offre.dto.OffreResumeResponse;
import com.stagehub.offre.entity.Offre;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CandidatureMapper {

    /** Le champ offre est converti par {@link #toOffreResume}, que MapStruct utilise automatiquement. */
    CandidatureEtudiantResponse toEtudiantResponse(Candidature candidature);

    @Mapping(target = "entrepriseNom", source = "entreprise.nom")
    OffreResumeResponse toOffreResume(Offre offre);

    @Mapping(target = "offreId", source = "offre.id")
    @Mapping(target = "offreTitre", source = "offre.titre")
    @Mapping(target = "etudiantId", source = "etudiant.id")
    @Mapping(target = "etudiantNom", source = "etudiant.nom")
    @Mapping(target = "etudiantPrenom", source = "etudiant.prenom")
    @Mapping(target = "etudiantEmail", source = "etudiant.utilisateur.email")
    @Mapping(target = "etudiantEcole", source = "etudiant.ecole")
    @Mapping(target = "etudiantFiliere", source = "etudiant.filiere")
    CandidatureEntrepriseResponse toEntrepriseResponse(Candidature candidature);
}
