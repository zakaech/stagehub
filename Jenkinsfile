// Pipeline CI/CD de StageHub (phase 6), exécuté par le job multibranche « StageHub » de Jenkins pour
// chaque branche du dépôt. Guide : docs/CI.md.
//
//   Préparation → Tests (services ∥ frontend) → Images → Déploiement recette (main)
//                                                                  → Publication GHCR + déploiement distant (main, désactivés)
//
// Toute étape en échec arrête le pipeline : les étapes suivantes sont sautées (« skipped »).
//
// Reconstruction sélective (phase 7 f) : seuls les modules modifiés depuis le dernier build réussi sont testés (avec
// ceux qui en dépendent), et seules les images dont le contenu a changé sont reconstruites (tag = empreinte du
// contenu). Build complet : paramètre COMPLET, ou chaque jour vers midi sur main ; il reteste tout et reconstruit
// toutes les images en retéléchargeant les images de base (--pull). Règles : ci/selection.sh, ci/tags.sh,
// ci/images.sh.

pipeline {
    agent any

    options {
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timestamps()
        // Tests en échec relevés par junit → build UNSTABLE : on ne construit ni ne déploie quand même
        skipStagesAfterUnstable()
    }

    // Build complet planifié sur main, vers midi (H : minute choisie par Jenkins, stable pour ce job). Pas la nuit :
    // Jenkins tourne sur le PC de développement, et un créneau manqué (PC éteint) n'est pas rattrapé.
    triggers {
        cron(env.BRANCH_NAME == 'main' ? 'H 12 * * *' : '')
    }

    parameters {
        booleanParam(name: 'COMPLET', defaultValue: false,
            description: 'Tout tester et reconstruire toutes les images (images de base retéléchargées), sans tenir compte des fichiers modifiés')
        booleanParam(name: 'DEPLOIEMENT_DISTANT', defaultValue: false,
            description: 'Publier les images sur GHCR et déployer sur le serveur (branche main ; credentials requis, voir docs/CI.md)')
    }

    environment {
        REGISTRE = 'ghcr.io/zakaech'
        // Microservices construits depuis services/ (phase 7 ; le monolithe a été supprimé en sous-étape d)
        SERVICES = 'discovery-server api-gateway auth-service notification-service offre-service candidature-service ai-service demo-seeder'
    }

    stages {
        stage('Préparation') {
            steps {
                script {
                    // amd64 seul sur les branches de travail (rapide) ; amd64 + arm64 sur main (serveur Oracle ARM)
                    env.PLATEFORMES = env.BRANCH_NAME == 'main' ? 'linux/amd64,linux/arm64' : 'linux/amd64'
                    boolean planifie = !currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause').isEmpty()
                    // Build complet : demandé à la main (COMPLET) ou planifié ; lu aussi par l'étape Images
                    env.BUILD_COMPLET = (params.COMPLET || planifie).toString()
                    // Commit du dernier build réussi de cette branche (plugin Git) ; absent au premier build
                    String reference = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: ''
                    Map selection = lireCleValeur(sh(returnStdout: true,
                            script: "sh ci/selection.sh '${reference}' ${env.BUILD_COMPLET}"))
                    env.MODULES = selection.MODULES
                    env.FRONTEND = selection.FRONTEND
                    echo "Sélection (${selection.RAISON}) : modules Java = ${env.MODULES ?: 'aucun'} ; frontend = ${env.FRONTEND}"
                }
                // Tag de chaque image = empreinte de son contenu (et des plateformes) ; lu par docker compose
                sh 'sh ci/tags.sh "$PLATEFORMES" frontend $SERVICES > images.env && cat images.env'
                sh 'docker version --format "Démon Docker (DinD) : {{.Server.Version}}"'
            }
        }

        stage('Tests') {
            parallel {
                stage('Services') {
                    when { expression { env.MODULES } }
                    steps {
                        dir('services') {
                            // Tests unitaires + tests d'intégration (Testcontainers, sur DinD) + rapport JaCoCo +
                            // contrôle des seuils : un test rouge OU une couverture insuffisante → échec
                            script {
                                if (env.MODULES == 'TOUS') {
                                    sh './mvnw -B -ntp verify'
                                } else {
                                    // 1. Modules dont dépend la sélection (commons...), installés sans leurs tests :
                                    //    l'étape 2 les lit dans le dépôt Maven local
                                    sh "./mvnw -B -ntp -q install -DskipTests -Djacoco.skip=true -pl ${env.MODULES} -am"
                                    // 2. Modules modifiés et ceux qui en dépendent (-amd) : toucher outbox reteste
                                    //    offre-service et candidature-service
                                    sh "./mvnw -B -ntp verify -pl ${env.MODULES} -amd"
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit testResults: 'services/*/target/surefire-reports/TEST-*.xml, services/*/target/failsafe-reports/TEST-*.xml',
                                  allowEmptyResults: true
                            recordCoverage tools: [[parser: 'JACOCO', pattern: 'services/*/target/site/jacoco/jacoco.xml']],
                                           id: 'jacoco-services', name: 'Couverture services (JaCoCo)',
                                           sourceDirectories: [[path: 'services/commons/src/main/java'],
                                                               [path: 'services/outbox/src/main/java'],
                                                               [path: 'services/audit/src/main/java'],
                                                               [path: 'services/discovery-server/src/main/java'],
                                                               [path: 'services/api-gateway/src/main/java'],
                                                               [path: 'services/auth-service/src/main/java'],
                                                               [path: 'services/notification-service/src/main/java'],
                                                               [path: 'services/offre-service/src/main/java'],
                                                               [path: 'services/candidature-service/src/main/java'],
                                                               [path: 'services/ai-service/src/main/java'],
                                                               [path: 'services/demo-seeder/src/main/java']],
                                           sourceCodeRetention: 'LAST_BUILD'
                        }
                    }
                }
                stage('Frontend') {
                    when { expression { env.FRONTEND == 'true' } }
                    steps {
                        dir('frontend') {
                            sh 'npm ci --no-audit --no-fund'
                            sh 'npm run format:check'
                            // Vitest : rapport JUnit dans test-results/junit.xml + affichage habituel
                            sh 'npm run test:ci'
                        }
                    }
                    post {
                        always {
                            junit testResults: 'frontend/test-results/junit.xml', allowEmptyResults: true
                        }
                    }
                }
            }
        }

        stage('Images') {
            steps {
                // Une image dont le tag (empreinte du contenu) existe déjà dans DinD n'est pas reconstruite, sauf
                // en build complet : toutes le sont, images de base retéléchargées (correctifs de sécurité)
                sh 'sh ci/images.sh construire "$PLATEFORMES" "$BUILD_COMPLET"'
            }
        }

        stage('Déploiement recette') {
            when { branch 'main' }
            steps {
                // .env de recette : credential « Secret file », copié dans un fichier temporaire le temps de l'étape
                withCredentials([file(credentialsId: 'stagehub-env-recette', variable: 'ENV_RECETTE')]) {
                    // --no-build : les images de l'étape précédente ; --wait : attend les healthchecks
                    // (tous les services « healthy »), sinon l'étape échoue
                    // Second --env-file : les tags des images (images.env). Un service dont le tag n'a pas changé
                    // n'est pas recréé : la recette ne redémarre que ce qui a été modifié.
                    sh 'docker compose -p stagehub-recette --env-file "$ENV_RECETTE" --env-file images.env up -d --no-build --wait --wait-timeout 300'
                    // Données de démo créées par l'API (ne fait rien si elles existent déjà) ; ses logs restent dans
                    // la console du build. Un échec ici signale aussi un parcours cassé à travers la gateway.
                    // --no-deps : ses dépendances viennent d'être démarrées par « up --wait » ; sans cette option,
                    // compose relance les conteneurs ponctuels (kafka-init : ~1 min 30 de plus à chaque build).
                    sh 'docker compose -p stagehub-recette --env-file "$ENV_RECETTE" --env-file images.env run --rm --no-deps demo-seeder'
                }
                // Test de fumée à travers Nginx et la gateway (le nom « docker » désigne le conteneur DinD).
                // Nouvelles tentatives : la gateway peut répondre 503 quelques secondes, le temps de trouver le
                // service dans l'annuaire Eureka
                sh 'curl -fsS --retry 12 --retry-delay 5 --retry-all-errors "http://docker:8090/api/v1/offres?size=1" > /dev/null && echo "Recette OK : http://localhost:8095"'
            }
        }

        // ---- Déploiement distant : écrit mais désactivé (paramètre DEPLOIEMENT_DISTANT, faux par défaut) ----

        stage('Publication GHCR') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOIEMENT_DISTANT }
                }
            }
            steps {
                // Token GitHub « write:packages » stocké dans Jenkins, transmis par l'entrée standard (jamais dans
                // la ligne de commande ni dans les logs)
                withCredentials([usernamePassword(credentialsId: 'ghcr-stagehub',
                                                  usernameVariable: 'GHCR_UTILISATEUR', passwordVariable: 'GHCR_TOKEN')]) {
                    sh '''
                        echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_UTILISATEUR" --password-stdin
                        sh ci/images.sh publier "$REGISTRE"
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout ghcr.io > /dev/null 2>&1 || true'
                }
            }
        }

        stage('Déploiement distant') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOIEMENT_DISTANT }
                }
            }
            steps {
                // Clé SSH, empreinte du serveur et adresse : trois credentials Jenkins. StrictHostKeyChecking=yes avec
                // l'empreinte épinglée : on refuse de parler à un serveur qui ne serait pas le nôtre.
                // Le .env de production reste sur le serveur : aucun secret de prod ne passe par Jenkins.
                withCredentials([
                    sshUserPrivateKey(credentialsId: 'oracle-ssh', keyFileVariable: 'CLE_SSH', usernameVariable: 'UTILISATEUR_SSH'),
                    file(credentialsId: 'oracle-known-hosts', variable: 'KNOWN_HOSTS'),
                    string(credentialsId: 'oracle-hote', variable: 'HOTE_SSH')
                ]) {
                    sh '''
                        # Tags des images (images.env) transmis comme variables : le serveur tire exactement ces images
                        TAGS=$(sed 's/^/export /; s/$/;/' images.env | paste -sd ' ' -)
                        ssh -i "$CLE_SSH" -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$KNOWN_HOSTS" \
                            "$UTILISATEUR_SSH@$HOTE_SSH" \
                            "cd ~/stagehub && git pull --ff-only \
                             && $TAGS export IMAGE_REGISTRY=$REGISTRE/ \
                             && docker compose -f docker-compose.yml -f docker-compose.prod.yml pull \
                             && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build --wait"
                    '''
                }
            }
        }
    }

    post {
        always {
            // Les images gardent leur tag d'un build à l'autre (réutilisées si le contenu n'a pas changé) : on ne
            // supprime que celles qu'aucun conteneur n'utilise et créées il y a plus de 14 jours, plus les couches
            // orphelines. Le disque de DinD reste sous contrôle ; au pire, une image supprimée est reconstruite.
            sh 'docker image prune -af --filter "until=336h" > /dev/null 2>&1 || true'
            sh 'docker image prune -f > /dev/null 2>&1 || true'
        }
    }
}

/** Lignes « clé=valeur » (sortie de ci/selection.sh) → Map. */
Map lireCleValeur(String texte) {
    Map valeurs = [:]
    texte.trim().readLines().each { String ligne ->
        int egal = ligne.indexOf('=')
        valeurs[ligne.substring(0, egal)] = ligne.substring(egal + 1)
    }
    return valeurs
}
