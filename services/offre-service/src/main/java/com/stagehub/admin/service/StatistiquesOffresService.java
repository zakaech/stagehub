package com.stagehub.admin.service;

import com.stagehub.admin.dto.StatistiquesOffresResponse;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.offre.entity.StatutOffre;
import com.stagehub.offre.repository.OffreRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatistiquesOffresService {

    static final int TAILLE_TOP_TECHNOLOGIES = 5;

    private final OffreRepository offreRepository;
    private final EntrepriseRepository entrepriseRepository;

    public StatistiquesOffresService(OffreRepository offreRepository, EntrepriseRepository entrepriseRepository) {
        this.offreRepository = offreRepository;
        this.entrepriseRepository = entrepriseRepository;
    }

    public StatistiquesOffresResponse calculer() {
        return new StatistiquesOffresResponse(
                offreRepository.count(),
                offreRepository.countByStatut(StatutOffre.OUVERTE),
                offreRepository.compterParTechnologie(PageRequest.of(0, TAILLE_TOP_TECHNOLOGIES)),
                entrepriseRepository.countByStatutValidation(StatutValidation.EN_ATTENTE));
    }
}
