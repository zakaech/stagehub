package com.stagehub.auth.service;

import com.stagehub.auth.entity.Role;
import com.stagehub.auth.entity.StatutCompte;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.exception.EmailDejaUtiliseException;
import com.stagehub.auth.exception.UtilisateurNotFoundException;
import com.stagehub.auth.repository.UtilisateurRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Opérations sur les comptes, chacune dans <b>sa propre transaction locale</b> : ce sont les étapes de la saga
 * d'inscription ({@link InscriptionService}), qui les enchaîne avec des appels réseau entre elles.
 */
@Service
public class CompteService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public CompteService(UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Étape 1 de la saga : compte créé mais inutilisable tant que le profil n'existe pas. */
    @Transactional
    public Utilisateur creerEnAttente(String emailBrut, String motDePasse, Role role) {
        return creer(emailBrut, motDePasse, role, StatutCompte.EN_ATTENTE);
    }

    /** Compte utilisable immédiatement (admin d'amorçage : il n'a pas de profil). */
    @Transactional
    public Utilisateur creerActif(String emailBrut, String motDePasse, Role role) {
        return creer(emailBrut, motDePasse, role, StatutCompte.ACTIF);
    }

    /** Étape 3 de la saga : le profil existe, le compte peut se connecter. */
    @Transactional
    public Utilisateur activer(Long id) {
        Utilisateur utilisateur = trouver(id);
        utilisateur.setStatut(StatutCompte.ACTIF);
        return utilisateur;
    }

    /** Compensation de l'étape 1 (sans effet si le compte n'existe plus). */
    @Transactional
    public void supprimer(Long id) {
        utilisateurRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Utilisateur trouver(Long id) {
        return utilisateurRepository.findById(id).orElseThrow(() -> new UtilisateurNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public boolean existe(String emailBrut) {
        return utilisateurRepository.existsByEmail(Utilisateur.normaliserEmail(emailBrut));
    }

    private Utilisateur creer(String emailBrut, String motDePasse, Role role, StatutCompte statut) {
        String email = Utilisateur.normaliserEmail(emailBrut);
        if (utilisateurRepository.existsByEmail(email)) {
            throw new EmailDejaUtiliseException(email);
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasse));
        utilisateur.setRole(role);
        utilisateur.setStatut(statut);
        utilisateur.setDateCreation(clock.instant());
        return utilisateurRepository.save(utilisateur);
    }
}
