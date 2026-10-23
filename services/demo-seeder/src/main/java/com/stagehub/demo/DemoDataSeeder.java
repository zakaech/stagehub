package com.stagehub.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Mêmes données de démo que le DemoDataLoader du monolithe (3 entreprises dont 1 en attente, 6 offres dont 1 fermée,
 * 3 étudiants, 7 candidatures), mais créées par l'API : chaque service enregistre sa part, les règles métier
 * s'appliquent (machine à états des candidatures, entreprise validée pour publier...), les emails et l'audit aussi.
 * Idempotent : si le compte de démo TechNova existe déjà, rien n'est fait.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String COMPTE_TEMOIN = "rh@technova.demo";

    private final ApiStageHub api;
    private final DemoProperties properties;
    private final Clock clock;

    public DemoDataSeeder(ApiStageHub api, DemoProperties properties, Clock clock) {
        this.api = api;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        attendreLApi();
        String mdp = properties.motDePasse();
        if (api.connecter(COMPTE_TEMOIN, mdp).isPresent()) {
            log.info("Données de démo déjà présentes ({} existe) : rien à faire", COMPTE_TEMOIN);
            return;
        }
        String admin = api.connecter(properties.adminEmail(), properties.adminPassword())
                .orElseThrow(() -> new IllegalStateException("Connexion admin impossible : vérifier ADMIN_EMAIL / ADMIN_PASSWORD"));
        attendreLesServices(admin);
        LocalDate aujourdhui = LocalDate.now(clock.withZone(ZoneId.of("Africa/Casablanca")));

        // Entreprises : deux validées par l'admin, une laissée en attente
        long technova = api.inscrireEntreprise(COMPTE_TEMOIN, mdp, "TechNova", "Éditeur logiciel", "Casablanca",
                "Éditeur de solutions SaaS pour la banque et l'assurance.");
        long datasphere = api.inscrireEntreprise("stages@datasphere.demo", mdp, "DataSphere", "Data & IA", "Rabat",
                "Cabinet de conseil spécialisé en data engineering et machine learning.");
        api.inscrireEntreprise("contact@greenbyte.demo", mdp, "GreenByte", "Green IT", "Tanger",
                "Startup qui mesure l'empreinte carbone des systèmes d'information.");
        api.validerEntreprise(admin, technova);
        api.validerEntreprise(admin, datasphere);
        String tn = jeton(COMPTE_TEMOIN);
        String ds = jeton("stages@datasphere.demo");

        long backend = api.creerOffre(tn, "Développeur Back-end Java / Spring Boot",
                "Conception d'API REST pour notre plateforme de souscription en ligne.", "Casablanca", "Développement web",
                List.of("Java", "Spring Boot", "PostgreSQL", "Docker"), 6, aujourdhui.plusDays(45));
        long frontend = api.creerOffre(tn, "Développeur Front-end Angular",
                "Refonte de l'espace client avec Angular et une bibliothèque de composants.", "Casablanca",
                "Développement web", List.of("Angular", "TypeScript", "RxJS"), 4, aujourdhui.plusDays(30));
        long devops = api.creerOffre(tn, "Ingénieur DevOps",
                "Mise en place de pipelines CI/CD et conteneurisation des services.", "Casablanca", "DevOps",
                List.of("Docker", "Jenkins", "Kubernetes", "Linux"), 6, aujourdhui.plusDays(60));
        long dataEng = api.creerOffre(ds, "Data Engineer",
                "Industrialisation de pipelines d'ingestion et de transformation de données.", "Rabat", "Data",
                List.of("Python", "Spark", "SQL", "Docker"), 6, aujourdhui.plusDays(40));
        long ml = api.creerOffre(ds, "Machine Learning Engineer",
                "Mise en production de modèles de scoring et suivi de leurs performances.", "Rabat",
                "Intelligence artificielle", List.of("Python", "scikit-learn", "MLflow"), 5, aujourdhui.plusDays(20));
        // L'API refuse une date limite passée : l'offre fermée est créée puis fermée par son entreprise
        long bi = api.creerOffre(ds, "Analyste BI", "Tableaux de bord pour les directions métier.", "Rabat", "Data",
                List.of("SQL", "Power BI"), 4, aujourdhui.plusDays(15));
        api.fermerOffre(ds, bi);

        api.inscrireEtudiant("sara.alaoui@etu.demo", mdp, "Alaoui", "Sara", "ENSIAS", "Génie logiciel", "Rabat");
        api.inscrireEtudiant("youssef.bennani@etu.demo", mdp, "Bennani", "Youssef", "EMI", "Informatique", "Casablanca");
        api.inscrireEtudiant("imane.tazi@etu.demo", mdp, "Tazi", "Imane", "INPT", "Data science", "Rabat");
        String sara = jeton("sara.alaoui@etu.demo");
        String youssef = jeton("youssef.bennani@etu.demo");
        String imane = jeton("imane.tazi@etu.demo");

        // Candidatures ; les statuts suivent la machine à états, décidés par l'entreprise propriétaire de l'offre
        api.postuler(sara, backend, "Passionnée par Spring Boot, je souhaite rejoindre votre équipe back-end.");
        statuts(ds, api.postuler(sara, dataEng, null), "ENTRETIEN");
        statuts(tn, api.postuler(youssef, backend, "Mon projet de fin d'année portait sur une API Spring."),
                "ENTRETIEN", "ACCEPTEE");
        statuts(tn, api.postuler(youssef, frontend, null), "REFUSEE");
        api.postuler(youssef, devops, "Je m'intéresse beaucoup à l'automatisation des déploiements.");
        statuts(ds, api.postuler(imane, ml, "Mon stage de 4e année portait sur la détection de fraude."), "ENTRETIEN");
        api.postuler(imane, dataEng, null);

        log.info("Données de démo créées : 3 entreprises (dont 1 en attente), 6 offres (dont 1 fermée), "
                + "3 étudiants, 7 candidatures");
    }

    private void attendreLApi() throws InterruptedException {
        Instant limite = clock.instant().plus(properties.attenteMax());
        while (!api.prete()) {
            if (!clock.instant().isBefore(limite)) {
                throw new IllegalStateException("API injoignable après " + properties.attenteMax());
            }
            log.info("API pas encore prête (services en cours de démarrage ou d'enregistrement dans Eureka)...");
            Thread.sleep(3000);
        }
    }

    /**
     * Inscrire un étudiant fait appeler candidature-service par auth-service (saga). Si auth-service ne le trouve pas
     * encore dans l'annuaire, la création du profil ET sa compensation échouent : le compte reste EN_ATTENTE (nettoyé
     * après 15 min) et l'email est bloqué entre-temps. On attend donc que les services répondent à travers la
     * gateway, puis le temps de propagation de l'annuaire vers les autres services.
     */
    private void attendreLesServices(String jetonAdmin) throws InterruptedException {
        Instant limite = clock.instant().plus(properties.attenteMax());
        while (!api.servicesPrets(jetonAdmin)) {
            if (!clock.instant().isBefore(limite)) {
                throw new IllegalStateException("Services injoignables après " + properties.attenteMax());
            }
            log.info("Services métier pas encore joignables à travers la gateway...");
            Thread.sleep(3000);
        }
        Thread.sleep(properties.propagation());
    }

    private String jeton(String email) {
        return api.connecter(email, properties.motDePasse())
                .orElseThrow(() -> new IllegalStateException("Connexion impossible pour " + email));
    }

    private void statuts(String jetonEntreprise, long candidatureId, String... parcours) {
        for (String statut : parcours) {
            api.changerStatut(jetonEntreprise, candidatureId, statut);
        }
    }
}
