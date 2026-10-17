package com.stagehub.entreprise.service;

import com.stagehub.audit.aspect.Audite;
import com.stagehub.audit.entity.ActionAudit;
import com.stagehub.common.dto.PageResponse;
import com.stagehub.common.exception.EntrepriseNotFoundException;
import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.event.EntrepriseValidationEvent;
import com.stagehub.entreprise.mapper.EntrepriseMapper;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EntrepriseService {

    private final EntrepriseRepository entrepriseRepository;
    private final EntrepriseMapper entrepriseMapper;
    private final ApplicationEventPublisher evenements;

    public EntrepriseService(EntrepriseRepository entrepriseRepository, EntrepriseMapper entrepriseMapper,
                             ApplicationEventPublisher evenements) {
        this.entrepriseRepository = entrepriseRepository;
        this.entrepriseMapper = entrepriseMapper;
        this.evenements = evenements;
    }

    /** Sans filtre de statut, renvoie toutes les entreprises. */
    public PageResponse<EntrepriseResponse> lister(StatutValidation statut, Pageable pageable) {
        Page<Entreprise> page = statut == null
                ? entrepriseRepository.findAll(pageable)
                : entrepriseRepository.findByStatutValidation(statut, pageable);
        return PageResponse.of(page, entrepriseMapper::toResponse);
    }

    /** Seule une entreprise EN_ATTENTE peut être validée ou refusée (sinon 409). */
    @Transactional
    @Audite(value = ActionAudit.VALIDATION_ENTREPRISE, idParam = "entrepriseId")
    public EntrepriseResponse statuer(Long entrepriseId, StatutValidation decision) {
        Entreprise entreprise = entrepriseRepository.findById(entrepriseId)
                .orElseThrow(() -> new EntrepriseNotFoundException(entrepriseId));
        StatutValidation actuel = entreprise.getStatutValidation();
        if (!actuel.peutPasserA(decision)) {
            throw new TransitionStatutInvalideException(
                    "Décision impossible : l'entreprise est %s, décision demandée %s".formatted(actuel, decision));
        }

        entreprise.setStatutValidation(decision);
        // Email envoyé après le commit (NotificationListener), jamais pour une décision annulée
        evenements.publishEvent(new EntrepriseValidationEvent(
                entreprise.getId(), entreprise.getUtilisateur().getEmail(), entreprise.getNom(), decision));
        return entrepriseMapper.toResponse(entreprise);
    }
}
