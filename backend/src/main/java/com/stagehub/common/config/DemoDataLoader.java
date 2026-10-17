package com.stagehub.common.config;

import com.stagehub.auth.entity.Role;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.repository.UtilisateurRepository;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.repository.EtudiantRepository;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import com.stagehub.offre.repository.OffreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Jeu de données de démonstration, chargé en profil {@code dev} (développement) ou {@code demo} (instance de
 * démonstration : SPRING_PROFILES_ACTIVE=prod,demo), et seulement si la base ne contient encore aucune entreprise.
 * Une vraie production (profil prod seul) n'en charge jamais. Mots de passe hachés par le vrai PasswordEncoder.
 */
@Component
@Profile({"dev", "demo"})
@Order(2)
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final UtilisateurRepository utilisateurRepository;
    private final EtudiantRepository etudiantRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final OffreRepository offreRepository;
    private final CandidatureRepository candidatureRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String motDePasseDemo;

    public DemoDataLoader(UtilisateurRepository utilisateurRepository, EtudiantRepository etudiantRepository,
                          EntrepriseRepository entrepriseRepository, OffreRepository offreRepository,
                          CandidatureRepository candidatureRepository, PasswordEncoder passwordEncoder, Clock clock,
                          @Value("${stagehub.demo.mot-de-passe}") String motDePasseDemo) {
        this.utilisateurRepository = utilisateurRepository;
        this.etudiantRepository = etudiantRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.offreRepository = offreRepository;
        this.candidatureRepository = candidatureRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.motDePasseDemo = motDePasseDemo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (entrepriseRepository.count() > 0) {
            log.info("Données de démo ignorées : la base contient déjà des entreprises");
            return;
        }

        // Un seul hash calculé : BCrypt est volontairement lent (~100 ms par hash)
        String hash = passwordEncoder.encode(motDePasseDemo);
        LocalDate aujourdhui = LocalDate.now(clock);

        Entreprise technova = entreprise("rh@technova.demo", hash, "TechNova", "Éditeur logiciel", "Casablanca",
                "Éditeur de solutions SaaS pour la banque et l'assurance.", StatutValidation.VALIDEE);
        Entreprise datasphere = entreprise("stages@datasphere.demo", hash, "DataSphere", "Data & IA", "Rabat",
                "Cabinet de conseil spécialisé en data engineering et machine learning.", StatutValidation.VALIDEE);
        entreprise("contact@greenbyte.demo", hash, "GreenByte", "Green IT", "Tanger",
                "Startup qui mesure l'empreinte carbone des systèmes d'information.", StatutValidation.EN_ATTENTE);

        Offre backend = offre(technova, "Développeur Back-end Java / Spring Boot",
                "Conception d'API REST pour notre plateforme de souscription en ligne.", "Casablanca", "Développement web",
                List.of("Java", "Spring Boot", "PostgreSQL", "Docker"), 6, aujourdhui.plusDays(45), StatutOffre.OUVERTE);
        Offre frontend = offre(technova, "Développeur Front-end Angular",
                "Refonte de l'espace client avec Angular et une bibliothèque de composants.", "Casablanca",
                "Développement web", List.of("Angular", "TypeScript", "RxJS"), 4, aujourdhui.plusDays(30), StatutOffre.OUVERTE);
        Offre devops = offre(technova, "Ingénieur DevOps",
                "Mise en place de pipelines CI/CD et conteneurisation des services.", "Casablanca", "DevOps",
                List.of("Docker", "Jenkins", "Kubernetes", "Linux"), 6, aujourdhui.plusDays(60), StatutOffre.OUVERTE);
        Offre dataEng = offre(datasphere, "Data Engineer",
                "Industrialisation de pipelines d'ingestion et de transformation de données.", "Rabat", "Data",
                List.of("Python", "Spark", "SQL", "Docker"), 6, aujourdhui.plusDays(40), StatutOffre.OUVERTE);
        Offre ml = offre(datasphere, "Machine Learning Engineer",
                "Mise en production de modèles de scoring et suivi de leurs performances.", "Rabat",
                "Intelligence artificielle", List.of("Python", "scikit-learn", "MLflow"), 5, aujourdhui.plusDays(20),
                StatutOffre.OUVERTE);
        offre(datasphere, "Analyste BI",
                "Tableaux de bord pour les directions métier.", "Rabat", "Data",
                List.of("SQL", "Power BI"), 4, aujourdhui.minusDays(10), StatutOffre.FERMEE);

        Etudiant sara = etudiant("sara.alaoui@etu.demo", hash, "Alaoui", "Sara", "ENSIAS", "Génie logiciel", "Rabat");
        Etudiant youssef = etudiant("youssef.bennani@etu.demo", hash, "Bennani", "Youssef", "EMI",
                "Informatique", "Casablanca");
        Etudiant imane = etudiant("imane.tazi@etu.demo", hash, "Tazi", "Imane", "INPT", "Data science", "Rabat");

        candidature(sara, backend, "Passionnée par Spring Boot, je souhaite rejoindre votre équipe back-end.");
        candidature(sara, dataEng, null, StatutCandidature.ENTRETIEN);
        candidature(youssef, backend, "Mon projet de fin d'année portait sur une API Spring.",
                StatutCandidature.ENTRETIEN, StatutCandidature.ACCEPTEE);
        candidature(youssef, frontend, null, StatutCandidature.REFUSEE);
        candidature(youssef, devops, "Je m'intéresse beaucoup à l'automatisation des déploiements.");
        candidature(imane, ml, "Mon stage de 4e année portait sur la détection de fraude.", StatutCandidature.ENTRETIEN);
        candidature(imane, dataEng, null);

        log.info("Données de démo chargées : 3 entreprises (dont 1 en attente), 6 offres, 3 étudiants, 7 candidatures");
    }

    private Utilisateur utilisateur(String email, String hash, Role role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse(hash);
        utilisateur.setRole(role);
        return utilisateurRepository.save(utilisateur);
    }

    private Entreprise entreprise(String email, String hash, String nom, String secteur, String ville,
                                  String description, StatutValidation statut) {
        Entreprise entreprise = new Entreprise();
        entreprise.setUtilisateur(utilisateur(email, hash, Role.ENTREPRISE));
        entreprise.setNom(nom);
        entreprise.setSecteur(secteur);
        entreprise.setVille(ville);
        entreprise.setDescription(description);
        entreprise.setStatutValidation(statut);
        return entrepriseRepository.save(entreprise);
    }

    private Etudiant etudiant(String email, String hash, String nom, String prenom, String ecole, String filiere,
                              String ville) {
        Etudiant etudiant = new Etudiant();
        etudiant.setUtilisateur(utilisateur(email, hash, Role.ETUDIANT));
        etudiant.setNom(nom);
        etudiant.setPrenom(prenom);
        etudiant.setEcole(ecole);
        etudiant.setFiliere(filiere);
        etudiant.setVille(ville);
        return etudiantRepository.save(etudiant);
    }

    private Offre offre(Entreprise entreprise, String titre, String description, String ville, String domaine,
                        List<String> technologies, int dureeMois, LocalDate dateLimite, StatutOffre statut) {
        Offre offre = new Offre();
        offre.setEntreprise(entreprise);
        offre.setTitre(titre);
        offre.setDescription(description);
        offre.setVille(ville);
        offre.setDomaine(domaine);
        offre.setTechnologies(new LinkedHashSet<>(technologies));
        offre.setDureeMois(dureeMois);
        offre.setDateLimite(dateLimite);
        offre.setStatut(statut);
        return offreRepository.save(offre);
    }

    /** Les statuts passent par changerStatut : les données de démo respectent la machine à états. */
    private void candidature(Etudiant etudiant, Offre offre, String lettre, StatutCandidature... parcours) {
        Candidature candidature = new Candidature();
        candidature.setEtudiant(etudiant);
        candidature.setOffre(offre);
        candidature.setLettreMotivation(lettre);
        for (StatutCandidature statut : parcours) {
            candidature.changerStatut(statut);
        }
        candidatureRepository.save(candidature);
    }
}
