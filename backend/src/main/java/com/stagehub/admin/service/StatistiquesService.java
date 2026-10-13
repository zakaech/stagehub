package com.stagehub.admin.service;

import com.stagehub.admin.dto.StatistiquesResponse;
import com.stagehub.candidature.dto.StatutStat;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.offre.dto.TechnologieStat;
import com.stagehub.offre.entity.StatutOffre;
import com.stagehub.offre.repository.OffreRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StatistiquesService {

    static final int TAILLE_TOP_TECHNOLOGIES = 5;

    private final OffreRepository offreRepository;
    private final CandidatureRepository candidatureRepository;
    private final EntrepriseRepository entrepriseRepository;

    public StatistiquesService(OffreRepository offreRepository, CandidatureRepository candidatureRepository,
                               EntrepriseRepository entrepriseRepository) {
        this.offreRepository = offreRepository;
        this.candidatureRepository = candidatureRepository;
        this.entrepriseRepository = entrepriseRepository;
    }

    public StatistiquesResponse calculer() {
        // Tous les statuts apparaissent, à 0 s'il n'y a aucune candidature (le GROUP BY ne les renvoie pas)
        Map<StatutCandidature, Long> parStatut = new EnumMap<>(StatutCandidature.class);
        for (StatutCandidature statut : StatutCandidature.values()) {
            parStatut.put(statut, 0L);
        }
        for (StatutStat stat : candidatureRepository.compterParStatut()) {
            parStatut.put(stat.statut(), stat.nombre());
        }
        long totalCandidatures = parStatut.values().stream().mapToLong(Long::longValue).sum();

        List<TechnologieStat> topTechnologies =
                offreRepository.compterParTechnologie(PageRequest.of(0, TAILLE_TOP_TECHNOLOGIES));

        return new StatistiquesResponse(
                offreRepository.count(),
                offreRepository.countByStatut(StatutOffre.OUVERTE),
                totalCandidatures,
                parStatut,
                topTechnologies,
                entrepriseRepository.countByStatutValidation(StatutValidation.EN_ATTENTE));
    }
}
