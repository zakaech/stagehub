package com.stagehub.admin.service;

import com.stagehub.admin.dto.StatistiquesCandidaturesResponse;
import com.stagehub.candidature.dto.StatutStat;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StatistiquesService {

    private final CandidatureRepository candidatureRepository;

    public StatistiquesService(CandidatureRepository candidatureRepository) {
        this.candidatureRepository = candidatureRepository;
    }

    public StatistiquesCandidaturesResponse calculer() {
        // Tous les statuts apparaissent, à 0 s'il n'y a aucune candidature (le GROUP BY ne les renvoie pas)
        Map<StatutCandidature, Long> parStatut = new EnumMap<>(StatutCandidature.class);
        for (StatutCandidature statut : StatutCandidature.values()) {
            parStatut.put(statut, 0L);
        }
        for (StatutStat stat : candidatureRepository.compterParStatut()) {
            parStatut.put(stat.statut(), stat.nombre());
        }
        long total = parStatut.values().stream().mapToLong(Long::longValue).sum();
        return new StatistiquesCandidaturesResponse(total, parStatut);
    }
}
