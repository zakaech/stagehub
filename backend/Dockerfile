# syntax=docker/dockerfile:1

# =============================================================================
# Étape 1 — compilation : JDK + Maven, rien de tout cela ne part en production
# =============================================================================
# --platform=$BUILDPLATFORM : cette étape tourne toujours sur l'architecture de la machine qui construit
# (amd64 sur le PC), même quand on vise arm64. Le jar produit est le même partout : inutile de compiler
# deux fois, et surtout de compiler sous émulation QEMU (environ 10 fois plus lent).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Dépendances d'abord, dans une couche à part : tant que pom.xml ne change pas,
# Docker réutilise cette couche et ne retélécharge rien.
# --mount=type=cache : le dépôt Maven (~/.m2) est un cache de BuildKit, conservé d'un build à l'autre
# mais hors de l'image ; un pom.xml modifié ne fait télécharger que les nouvelles dépendances.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Puis le code. Les tests tournent dans "mvn verify" (pipeline Jenkins), pas ici.
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
    && cp target/stagehub-backend-*.jar application.jar \
    # Découpe le jar en couches : dépendances (changent rarement) / chargeur Spring / code (change souvent)
    && java -Djarmode=tools -jar application.jar extract --layers --destination extrait

# =============================================================================
# Étape 2 — exécution : JRE seul, utilisateur non-root
# =============================================================================
# Sans --platform : cette étape est construite pour chaque architecture demandée (amd64, arm64)
FROM eclipse-temurin:21-jre-alpine

# Utilisateur système sans privilèges ; /data/cv lui appartient : un volume nommé vide monté à cet
# endroit hérite de ce propriétaire, l'application peut donc y écrire sans être root.
RUN addgroup -S stagehub && adduser -S -H -G stagehub stagehub \
    && mkdir -p /data/cv && chown -R stagehub:stagehub /data

WORKDIR /app
# Une couche Docker par couche du jar, de la plus stable à la plus changeante :
# après une modification du code, seule la dernière couche est reconstruite et retransférée.
COPY --from=build /build/extrait/dependencies/ ./
COPY --from=build /build/extrait/spring-boot-loader/ ./
COPY --from=build /build/extrait/snapshot-dependencies/ ./
COPY --from=build /build/extrait/application/ ./

# Mémoire : la JVM lit la limite du conteneur, mais par défaut n'en donne que 25 % au tas.
# 75 % laisse le reste au metaspace, aux piles des threads et aux buffers.
# ExitOnOutOfMemoryError : le conteneur s'arrête (et Docker le relance) plutôt que de rester instable.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    CV_STORAGE_DIR=/data/cv

USER stagehub
EXPOSE 8080

# Forme "exec" (tableau JSON) : java est le processus principal et reçoit directement le SIGTERM de
# "docker stop", ce qui déclenche l'arrêt propre de Spring (server.shutdown=graceful).
ENTRYPOINT ["java", "-jar", "application.jar"]
