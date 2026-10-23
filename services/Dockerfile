# syntax=docker/dockerfile:1

# Image d'un microservice StageHub. Un seul Dockerfile pour tous : l'argument SERVICE choisit le module.
#   docker build --build-arg SERVICE=api-gateway -t stagehub-api-gateway services
# Multi-stage (phase 5) : compilation en natif, JRE seul à l'exécution, utilisateur non-root.

# =============================================================================
# Étape 1 — compilation, sur l'architecture de la machine qui construit ($BUILDPLATFORM) : le jar est le même
# pour amd64 et arm64, seule l'étape 2 est construite (sous émulation) pour chaque plateforme
# =============================================================================
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
ARG SERVICE
WORKDIR /build

# Tout le projet multi-module est copié (POM parent + modules) ; le dépôt Maven est un cache BuildKit
# partagé entre les services et entre les builds : les dépendances communes ne sont téléchargées qu'une fois.
COPY . .
# -pl : seulement ce module ; -am (also make) : et les modules dont il dépend.
# sharing=locked : quand plusieurs services se construisent en parallèle (docker compose build), un seul Maven
# à la fois écrit dans le cache partagé (deux écritures simultanées pourraient corrompre un téléchargement).
RUN --mount=type=cache,target=/root/.m2,sharing=locked chmod +x mvnw \
    && ./mvnw -B -q -ntp -pl "$SERVICE" -am package -DskipTests -Djacoco.skip=true \
    && cp "$SERVICE"/target/"$SERVICE"-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extrait

# =============================================================================
# Étape 2 — exécution : JRE seul, utilisateur non-root
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# /data : dossier de données éventuel d'un service (ex. clé générée par auth-service). Un volume nommé monté à cet
# endroit hérite de ce propriétaire : le service y écrit sans être root.
RUN addgroup -S stagehub && adduser -S -H -G stagehub stagehub \
    && mkdir -p /data && chown stagehub:stagehub /data

WORKDIR /app
COPY --from=build /build/extrait/dependencies/ ./
COPY --from=build /build/extrait/spring-boot-loader/ ./
COPY --from=build /build/extrait/snapshot-dependencies/ ./
COPY --from=build /build/extrait/application/ ./

# Petits services, petits tas (limites de 320 à 448 Mo dans docker-compose.yml) :
#   - MaxRAMPercentage=60 : 60 % de la limite du conteneur pour le tas ; le reste (classes, code compilé, piles,
#     buffers : 150 à 180 Mo pour un service Spring Cloud, mesuré) doit tenir dans les 40 % restants ;
#   - SerialGC : le ramasse-miettes le plus économe en mémoire, adapté aux tas de quelques centaines de Mo ;
#   - Xss512k : piles de threads de 512 Ko au lieu de 1 Mo (Tomcat crée jusqu'à 200 threads).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080

USER stagehub
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
