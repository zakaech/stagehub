package com.stagehub.candidature.service;

import com.stagehub.audit.aspect.Audite;
import com.stagehub.audit.entity.ActionAudit;
import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.dto.CandidatureRequest;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.event.CandidatureStatutChangeEvent;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.mapper.CandidatureMapper;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.common.dto.PageResponse;
import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.CandidatureDejaExistanteException;
import com.stagehub.common.exception.CandidatureNotFoundException;
import com.stagehub.common.exception.OffreNonDisponibleException;
import com.stagehub.common.exception.OffreNotFoundException;
import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.repository.EtudiantRepository;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.repository.OffreRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class CandidatureService {

    private final CandidatureRepository candidatureRepository;
    private final OffreRepository offreRepository;
    private final EtudiantRepository etudiantRepository;
    private final CandidatureMapper candidatureMapper;
    private final ApplicationEventPublisher evenements;
    private final Clock clock;

    public CandidatureService(CandidatureRepository candidatureRepository, OffreRepository offreRepository,
                              EtudiantRepository etudiantRepository, CandidatureMapper candidatureMapper,
                              ApplicationEventPublisher evenements, Clock clock) {
        this.candidatureRepository = candidatureRepository;
        this.offreRepository = offreRepository;
        this.etudiantRepository = etudiantRepository;
        this.candidatureMapper = candidatureMapper;
        this.evenements = evenements;
        this.clock = clock;
    }

    /** Spec §4.3 : offre OUVERTE et date limite non dépassée ; un doublon renvoie 409. */
    @Transactional
    public CandidatureEtudiantResponse postuler(Long etudiantId, Long offreId, CandidatureRequest request) {
        Offre offre = offreRepository.findById(offreId).orElseThrow(() -> new OffreNotFoundException(offreId));
        if (!offre.accepteCandidatures(LocalDate.now(clock))) {
            throw new OffreNonDisponibleException(
                    "Cette offre n'accepte plus de candidatures (fermée ou date limite dépassée)");
        }
        if (candidatureRepository.existsByEtudiantIdAndOffreId(etudiantId, offreId)) {
            throw new CandidatureDejaExistanteException();
        }

        Candidature candidature = new Candidature();
        // Verrou sur l'étudiant : un dépôt de CV simultané attend la fin de cette transaction (voir CvService)
        Etudiant etudiant = etudiantRepository.findAvecVerrou(etudiantId)
                .orElseThrow(() -> new UtilisateurNotFoundException(etudiantId));
        candidature.setEtudiant(etudiant);
        // Le CV est figé : l'entreprise téléchargera ce fichier, même si l'étudiant le remplace ensuite
        candidature.setCvFichier(etudiant.getCheminCv());
        candidature.setOffre(offre);
        candidature.setLettreMotivation(request == null ? null : request.lettreMotivation());

        try {
            candidatureRepository.saveAndFlush(candidature);
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité : deux requêtes simultanées peuvent passer le exists() ;
            // la contrainte unique (etudiant_id, offre_id) tranche en base.
            throw new CandidatureDejaExistanteException();
        }
        return candidatureMapper.toEtudiantResponse(candidature);
    }

    public PageResponse<CandidatureEtudiantResponse> listerCandidaturesEtudiant(Long etudiantId, Pageable pageable) {
        return PageResponse.of(candidatureRepository.findByEtudiantId(etudiantId, pageable),
                candidatureMapper::toEtudiantResponse);
    }

    public PageResponse<CandidatureEntrepriseResponse> listerCandidaturesOffre(Long entrepriseId, Long offreId,
                                                                               Pageable pageable) {
        Offre offre = offreRepository.findById(offreId).orElseThrow(() -> new OffreNotFoundException(offreId));
        if (!offre.appartientA(entrepriseId)) {
            throw new AccesNonAutoriseException("Cette offre n'appartient pas à votre entreprise");
        }
        return PageResponse.of(candidatureRepository.findByOffreId(offreId, pageable),
                candidatureMapper::toEntrepriseResponse);
    }

    /** La machine à états est appliquée par l'entité (→ 409 si transition invalide). */
    @Transactional
    @Audite(value = ActionAudit.CHANGEMENT_STATUT_CANDIDATURE, idParam = "candidatureId")
    public CandidatureEntrepriseResponse changerStatut(Long entrepriseId, Long candidatureId,
                                                       StatutCandidature nouveauStatut) {
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new CandidatureNotFoundException(candidatureId));
        if (!candidature.getOffre().appartientA(entrepriseId)) {
            throw new AccesNonAutoriseException("Cette candidature ne concerne pas une offre de votre entreprise");
        }

        StatutCandidature ancienStatut = candidature.getStatut();
        candidature.changerStatut(nouveauStatut);

        // Publié maintenant, traité après le commit (NotificationListener) : rien n'est envoyé si la transaction échoue
        Etudiant etudiant = candidature.getEtudiant();
        evenements.publishEvent(new CandidatureStatutChangeEvent(
                candidature.getId(),
                etudiant.getUtilisateur().getEmail(),
                etudiant.getPrenom(),
                candidature.getOffre().getTitre(),
                candidature.getOffre().getEntreprise().getNom(),
                ancienStatut,
                nouveauStatut));
        return candidatureMapper.toEntrepriseResponse(candidature);
    }
}
