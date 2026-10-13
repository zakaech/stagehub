package com.stagehub.candidature.mapper;

import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.common.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CandidatureMapper {

    @Mapping(target = "offreId", source = "offre.id")
    @Mapping(target = "offreTitre", source = "offre.titre")
    @Mapping(target = "entrepriseNom", source = "offre.entreprise.nom")
    CandidatureEtudiantResponse toEtudiantResponse(Candidature candidature);

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
