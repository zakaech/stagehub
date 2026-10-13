package com.stagehub.security;

import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.repository.UtilisateurRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Utilisé uniquement au login : charge l'utilisateur et son hash BCrypt pour vérifier le mot de passe. */
@Service
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurDetailsService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return utilisateurRepository.findByEmail(Utilisateur.normaliserEmail(email))
                .map(u -> User.withUsername(u.getEmail())
                        .password(u.getMotDePasse())
                        .roles(u.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
    }
}
