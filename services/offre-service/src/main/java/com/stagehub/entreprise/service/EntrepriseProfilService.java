package com.stagehub.entreprise.service;

import com.stagehub.common.exception.EntrepriseNotFoundException;
import com.stagehub.common.exception.ProfilDejaExistantException;
import com.stagehub.entreprise.dto.CreationEntrepriseRequest;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.mapper.EntrepriseMapper;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profils entreprises pour auth-service (endpoints /internal) : même rôle qu'{@code EtudiantProfilService}. */
@Service
@Transactional(readOnly = true)
public class EntrepriseProfilService {

    private final EntrepriseRepository entrepriseRepository;
    private final EntrepriseMapper entrepriseMapper;

    public EntrepriseProfilService(EntrepriseRepository entrepriseRepository, EntrepriseMapper entrepriseMapper) {
        this.entrepriseRepository = entrepriseRepository;
        this.entrepriseMapper = entrepriseMapper;
    }

    /** Une nouvelle entreprise est toujours EN_ATTENTE de validation par l'admin (règle métier §4.1). */
    @Transactional
    public EntrepriseResponse creer(CreationEntrepriseRequest demande) {
        if (entrepriseRepository.existsById(demande.id())) {
            throw new ProfilDejaExistantException(demande.id());
        }
        Entreprise entreprise = new Entreprise();
        entreprise.setId(demande.id());
        entreprise.setEmail(demande.email());
        entreprise.setNom(demande.nom());
        entreprise.setSecteur(demande.secteur());
        entreprise.setVille(demande.ville());
        entreprise.setDescription(demande.description());
        entreprise.setStatutValidation(StatutValidation.EN_ATTENTE);
        return entrepriseMapper.toResponse(entrepriseRepository.save(entreprise));
    }

    public EntrepriseResponse lire(Long id) {
        return entrepriseRepository.findById(id)
                .map(entrepriseMapper::toResponse)
                .orElseThrow(() -> new EntrepriseNotFoundException(id));
    }

    /** Idempotent : un profil déjà absent n'est pas une erreur (la compensation peut être rejouée). */
    @Transactional
    public void supprimer(Long id) {
        entrepriseRepository.deleteById(id);
    }
}
