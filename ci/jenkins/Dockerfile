# syntax=docker/dockerfile:1

# Image Jenkins de StageHub : Jenkins LTS (JDK 21, Debian 13) + CLI Docker (buildx, compose) + Node 24,
# plugins figés et configuration en code. Construite par ci/docker-compose.yml.

# Binaires autonomes (Go) de la CLI Docker et de ses plugins buildx et compose
FROM docker:29.9.2-cli AS docker-cli
# Node 24 compilé pour Debian 13, comme l'image Jenkins
FROM node:24.22.1-trixie-slim AS node

FROM jenkins/jenkins:2.580.1-lts-jdk21

USER root
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/ /usr/local/libexec/docker/cli-plugins/
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules/ /usr/local/lib/node_modules/
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# Retour à l'utilisateur non-root de l'image (uid 1000) : il n'a besoin d'aucun groupe "docker",
# puisqu'il parle au démon DinD en TCP avec un certificat client
USER jenkins

# Plugins installés au build de l'image (et non à la main dans l'interface). Les plugins choisis ont une
# version figée ; leurs dépendances sont résolues par jenkins-plugin-cli à des versions compatibles.
# Mise à jour : changer les versions ici, reconstruire l'image (docs/CI.md).
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Configuration en code (JCasC) : appliquée à chaque démarrage, l'interface n'est pas la source de vérité
COPY casc.yaml /usr/share/jenkins/casc.yaml
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/casc.yaml \
    # Pas de message « nouvelle version de npm disponible » dans les logs des builds
    NPM_CONFIG_UPDATE_NOTIFIER=false
