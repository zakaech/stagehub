package com.stagehub.candidature.service;

import com.stagehub.candidature.client.OffreInterne;
import com.stagehub.candidature.dto.CandidatureRequest;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.event.CandidatureCreeeEvent;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.common.Topics;
import com.stagehub.common.exception.CandidatureDejaExistanteException;
import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.repository.EtudiantRepository;
import com.stagehub.outbox.service.OutboxService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écriture d'une candidature, dans une transaction courte. Séparée de {@link CandidatureService#postuler} (qui lit
 * d'abord l'offre dans offre-service, hors transaction) : aucune connexion à la base ni aucun verrou n'est tenu
 * pendant l'appel réseau. Classe à part, car un appel this.methode() ne passerait pas par le proxy transactionnel.
 */
@Service
public class EnregistrementCandidature {

    private final CandidatureRepository candidatureRepository;
    private final EtudiantRepository etudiantRepository;

    private final OutboxService outbox;

    public EnregistrementCandidature(CandidatureRepository candidatureRepository, EtudiantRepository etudiantRepository,
                                     OutboxService outbox) {
        this.candidatureRepository = candidatureRepository;
        this.etudiantRepository = etudiantRepository;
        this.outbox = outbox;
    }

    @Transactional
    public Candidature enregistrer(Long etudiantId, OffreInterne offre, CandidatureRequest request) {
        if (candidatureRepository.existsByEtudiantIdAndOffreId(etudiantId, offre.id())) {
            throw new CandidatureDejaExistanteException();
        }
        Candidature candidature = new Candidature();
        // Verrou sur l'étudiant : un dépôt de CV simultané attend la fin de cette transaction (voir CvService)
        Etudiant etudiant = etudiantRepository.findAvecVerrou(etudiantId)
                .orElseThrow(() -> new UtilisateurNotFoundException(etudiantId));
        candidature.setEtudiant(etudiant);
        // Le CV est figé : l'entreprise téléchargera ce fichier, même si l'étudiant le remplace ensuite
        candidature.setCvFichier(etudiant.getCheminCv());
        // L'offre aussi, telle qu'elle était au moment de postuler (voir Candidature)
        candidature.setOffreId(offre.id());
        candidature.setEntrepriseId(offre.entrepriseId());
        candidature.setOffreTitre(offre.titre());
        candidature.setEntrepriseNom(offre.entrepriseNom());
        candidature.setOffreVille(offre.ville());
        candidature.setLettreMotivation(request == null ? null : request.lettreMotivation());
        Candidature enregistree;
        try {
            enregistree = candidatureRepository.saveAndFlush(candidature);
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité : deux requêtes simultanées peuvent passer le exists() ;
            // la contrainte unique (etudiant_id, offre_id) tranche en base.
            throw new CandidatureDejaExistanteException();
        }
        // Dans la même transaction (outbox) : ai-service est prévenu si, et seulement si, la candidature existe
        outbox.enregistrer(Topics.CANDIDATURE_CREEE, enregistree.getId().toString(), new CandidatureCreeeEvent(
                enregistree.getId(), enregistree.getOffreId(), etudiant.getId(), enregistree.getCvFichier()));
        return enregistree;
    }
}
