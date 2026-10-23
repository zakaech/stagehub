// Pipeline CI/CD de StageHub (phase 6), exécuté par le job multibranche « StageHub » de Jenkins pour
// chaque branche du dépôt. Guide : docs/CI.md.
//
//   Préparation → Tests (services ∥ frontend) → Images → Déploiement recette (main)
//                                                                  → Publication GHCR + déploiement distant (main, désactivés)
//
// Toute étape en échec arrête le pipeline : les étapes suivantes sont sautées (« skipped »).

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

    parameters {
        booleanParam(name: 'DEPLOIEMENT_DISTANT', defaultValue: false,
            description: 'Publier les images sur GHCR et déployer sur le serveur (branche main ; credentials requis, voir docs/CI.md)')
    }

    environment {
        REGISTRE = 'ghcr.io/zakaech'
        // Microservices construits depuis services/ (phase 7 ; le monolithe a été supprimé en sous-étape d)
        SERVICES = 'discovery-server api-gateway auth-service notification-service offre-service candidature-service demo-seeder'
    }

    stages {
        stage('Préparation') {
            steps {
                script {
                    // Tag des images = SHA court du commit : on sait toujours quel code tourne
                    env.IMAGE_TAG = env.GIT_COMMIT.take(7)
                    // amd64 seul sur les branches de travail (rapide) ; amd64 + arm64 sur main (serveur Oracle ARM)
                    env.PLATEFORMES = env.BRANCH_NAME == 'main' ? 'linux/amd64,linux/arm64' : 'linux/amd64'
                }
                echo "Branche ${env.BRANCH_NAME}, images stagehub-*:${env.IMAGE_TAG} pour ${env.PLATEFORMES}"
                sh 'docker version --format "Démon Docker (DinD) : {{.Server.Version}}"'
            }
        }

        stage('Tests') {
            parallel {
                stage('Services') {
                    steps {
                        dir('services') {
                            // Tous les microservices (POM parent multi-module) : tests unitaires + tests d'intégration
                            // (Testcontainers, sur DinD) + rapport JaCoCo + contrôle des seuils : un test rouge OU une
                            // couverture insuffisante → échec
                            sh './mvnw -B -ntp verify'
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
                                                               [path: 'services/demo-seeder/src/main/java']],
                                           sourceCodeRetention: 'LAST_BUILD'
                        }
                    }
                }
                stage('Frontend') {
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
                // buildx (BuildKit) : une variante par plateforme, réunies sous un même tag (manifest list).
                // --load les range dans le magasin d'images de DinD (containerd, qui accepte le multi-architecture).
                sh '''
                    docker buildx build --platform "$PLATEFORMES" --tag "stagehub-frontend:$IMAGE_TAG" --load frontend
                    # Microservices : un seul Dockerfile (services/), le module est choisi par SERVICE
                    for service in $SERVICES; do
                        docker buildx build --platform "$PLATEFORMES" --build-arg SERVICE="$service" \
                            --tag "stagehub-$service:$IMAGE_TAG" --load services
                    done
                    docker image ls --tree "stagehub-*:$IMAGE_TAG"
                '''
            }
        }

        stage('Déploiement recette') {
            when { branch 'main' }
            steps {
                // .env de recette : credential « Secret file », copié dans un fichier temporaire le temps de l'étape
                withCredentials([file(credentialsId: 'stagehub-env-recette', variable: 'ENV_RECETTE')]) {
                    // --no-build : les images de l'étape précédente ; --wait : attend les healthchecks
                    // (tous les services « healthy »), sinon l'étape échoue
                    sh 'docker compose -p stagehub-recette --env-file "$ENV_RECETTE" up -d --no-build --wait --wait-timeout 300'
                    // Données de démo créées par l'API (ne fait rien si elles existent déjà) ; ses logs restent dans
                    // la console du build. Un échec ici signale aussi un parcours cassé à travers la gateway.
                    sh 'docker compose -p stagehub-recette --env-file "$ENV_RECETTE" run --rm demo-seeder'
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
                        for app in frontend $SERVICES; do
                            docker tag "stagehub-$app:$IMAGE_TAG" "$REGISTRE/stagehub-$app:$IMAGE_TAG"
                            docker push "$REGISTRE/stagehub-$app:$IMAGE_TAG"
                        done
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
                        ssh -i "$CLE_SSH" -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$KNOWN_HOSTS" \
                            "$UTILISATEUR_SSH@$HOTE_SSH" \
                            "cd ~/stagehub && git pull --ff-only \
                             && export IMAGE_REGISTRY=$REGISTRE/ IMAGE_TAG=$IMAGE_TAG \
                             && docker compose -f docker-compose.yml -f docker-compose.prod.yml pull \
                             && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build --wait"
                    '''
                }
            }
        }
    }

    post {
        always {
            // Couches orphelines laissées par les builds successifs : le disque de DinD reste sous contrôle
            sh 'docker image prune -f > /dev/null 2>&1 || true'
        }
    }
}
