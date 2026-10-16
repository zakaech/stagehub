package com.stagehub.offre.service;

import com.stagehub.common.dto.PageResponse;
import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.EntrepriseNonValideeException;
import com.stagehub.common.exception.EntrepriseNotFoundException;
import com.stagehub.common.exception.OffreNotFoundException;
import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.offre.dto.FiltresDisponiblesResponse;
import com.stagehub.offre.dto.OffreFiltre;
import com.stagehub.offre.dto.OffreRequest;
import com.stagehub.offre.dto.OffreResponse;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import com.stagehub.offre.mapper.OffreMapper;
import com.stagehub.offre.repository.OffreRepository;
import com.stagehub.offre.repository.OffreSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Lecture seule par défaut (classe) ; les méthodes d'écriture redéclarent @Transactional.
 * Le mapping entité → DTO se fait ici, dans la transaction : avec open-in-view=false,
 * les associations paresseuses ne sont plus accessibles une fois sorti du service.
 */
@Service
@Transactional(readOnly = true)
public class OffreService {

    private final OffreRepository offreRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final OffreMapper offreMapper;
    private final Clock clock;

    public OffreService(OffreRepository offreRepository, EntrepriseRepository entrepriseRepository,
                        OffreMapper offreMapper, Clock clock) {
        this.offreRepository = offreRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.offreMapper = offreMapper;
        this.clock = clock;
    }

    public PageResponse<OffreResponse> rechercherOffresOuvertes(OffreFiltre filtre, Pageable pageable) {
        return PageResponse.of(
                offreRepository.findAll(OffreSpecifications.ouvertesCorrespondantA(filtre), pageable),
                offreMapper::toResponse);
    }

    public FiltresDisponiblesResponse filtresDisponibles() {
        return new FiltresDisponiblesResponse(
                offreRepository.villesDistinctes(StatutOffre.OUVERTE),
                offreRepository.domainesDistincts(StatutOffre.OUVERTE),
                offreRepository.technologiesDistinctes(StatutOffre.OUVERTE));
    }

    public OffreResponse getOffreOuverte(Long offreId) {
        return offreRepository.findByIdAndStatut(offreId, StatutOffre.OUVERTE)
                .map(offreMapper::toResponse)
                .orElseThrow(() -> new OffreNotFoundException(offreId));
    }

    public PageResponse<OffreResponse> listerOffresEntreprise(Long entrepriseId, Pageable pageable) {
        return PageResponse.of(offreRepository.findByEntrepriseId(entrepriseId, pageable), offreMapper::toResponse);
    }

    public OffreResponse getOffreEntreprise(Long entrepriseId, Long offreId) {
        return offreMapper.toResponse(chargerOffreDeLEntreprise(entrepriseId, offreId));
    }

    @Transactional
    public OffreResponse creer(Long entrepriseId, OffreRequest request) {
        Entreprise entreprise = chargerEntrepriseValidee(entrepriseId);

        Offre offre = offreMapper.toEntity(request);
        offre.setEntreprise(entreprise);
        offre.setStatut(StatutOffre.OUVERTE);
        return offreMapper.toResponse(offreRepository.save(offre));
    }

    @Transactional
    public OffreResponse modifier(Long entrepriseId, Long offreId, OffreRequest request) {
        chargerEntrepriseValidee(entrepriseId);
        Offre offre = chargerOffreDeLEntreprise(entrepriseId, offreId);

        offreMapper.mettreAJour(request, offre);
        // Pas de save() : l'entité est "managed", Hibernate détecte les changements au commit (dirty checking)
        return offreMapper.toResponse(offre);
    }

    /**
     * Fermer est toujours permis ; rouvrir seulement si la date limite n'est pas dépassée (sinon 409),
     * pour qu'une offre rouverte puisse effectivement recevoir des candidatures. Idempotent.
     */
    @Transactional
    public OffreResponse changerStatut(Long entrepriseId, Long offreId, StatutOffre nouveauStatut) {
        Offre offre = chargerOffreDeLEntreprise(entrepriseId, offreId);

        if (nouveauStatut == StatutOffre.OUVERTE && offre.getStatut() == StatutOffre.FERMEE
                && offre.dateLimiteDepassee(LocalDate.now(clock))) {
            throw new TransitionStatutInvalideException(
                    "Impossible de rouvrir l'offre : sa date limite (%s) est dépassée ; modifiez-la d'abord"
                            .formatted(offre.getDateLimite()));
        }
        offre.setStatut(nouveauStatut);
        return offreMapper.toResponse(offre);
    }

    /** Règle de propriété (spec §4.4) : 404 si l'offre n'existe pas, 403 si elle appartient à une autre entreprise. */
    private Offre chargerOffreDeLEntreprise(Long entrepriseId, Long offreId) {
        Offre offre = offreRepository.findById(offreId).orElseThrow(() -> new OffreNotFoundException(offreId));
        if (!offre.appartientA(entrepriseId)) {
            throw new AccesNonAutoriseException("Cette offre n'appartient pas à votre entreprise");
        }
        return offre;
    }

    /** Règle de l'entreprise en attente (spec §4.1) : 403 explicite tant que le compte n'est pas VALIDEE. */
    private Entreprise chargerEntrepriseValidee(Long entrepriseId) {
        Entreprise entreprise = entrepriseRepository.findById(entrepriseId)
                .orElseThrow(() -> new EntrepriseNotFoundException(entrepriseId));
        if (!entreprise.estValidee()) {
            throw new EntrepriseNonValideeException(entreprise.getStatutValidation());
        }
        return entreprise;
    }
}
