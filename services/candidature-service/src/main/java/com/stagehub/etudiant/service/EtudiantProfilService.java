package com.stagehub.etudiant.service;

import com.stagehub.common.exception.ProfilDejaExistantException;
import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.etudiant.dto.CreationEtudiantRequest;
import com.stagehub.etudiant.dto.EtudiantResponse;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.mapper.EtudiantMapper;
import com.stagehub.etudiant.repository.EtudiantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profils étudiants pour auth-service (endpoints /internal) : création à l'inscription, lecture pour /auth/me,
 * suppression pour la compensation de la saga d'inscription.
 */
@Service
@Transactional(readOnly = true)
public class EtudiantProfilService {

    private final EtudiantRepository etudiantRepository;
    private final EtudiantMapper etudiantMapper;

    public EtudiantProfilService(EtudiantRepository etudiantRepository, EtudiantMapper etudiantMapper) {
        this.etudiantRepository = etudiantRepository;
        this.etudiantMapper = etudiantMapper;
    }

    @Transactional
    public EtudiantResponse creer(CreationEtudiantRequest demande) {
        if (etudiantRepository.existsById(demande.id())) {
            throw new ProfilDejaExistantException(demande.id());
        }
        Etudiant etudiant = new Etudiant();
        etudiant.setId(demande.id());
        etudiant.setEmail(demande.email());
        etudiant.setNom(demande.nom());
        etudiant.setPrenom(demande.prenom());
        etudiant.setEcole(demande.ecole());
        etudiant.setFiliere(demande.filiere());
        etudiant.setVille(demande.ville());
        return etudiantMapper.toResponse(etudiantRepository.save(etudiant));
    }

    public EtudiantResponse lire(Long id) {
        return etudiantRepository.findById(id)
                .map(etudiantMapper::toResponse)
                .orElseThrow(() -> new UtilisateurNotFoundException(id));
    }

    /** Idempotent : un profil déjà absent n'est pas une erreur (la compensation peut être rejouée). */
    @Transactional
    public void supprimer(Long id) {
        etudiantRepository.deleteById(id);
    }
}
