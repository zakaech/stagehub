# syntax=docker/dockerfile:1

# =============================================================================
# Étape 1 — build Angular (Node), rien de tout cela ne part en production
# =============================================================================
# --platform=$BUILDPLATFORM : le build Angular (HTML/JS/CSS) ne dépend pas de l'architecture ;
# il est fait une seule fois, en natif, même pour une image arm64.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /build

# Dépendances d'abord (couche réutilisée tant que package-lock.json ne change pas) ;
# cache npm conservé par BuildKit d'un build à l'autre
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# =============================================================================
# Étape 2 — Nginx non-root : sert les fichiers statiques et relaie /api vers le backend
# =============================================================================
# Construite pour chaque architecture demandée (amd64, arm64)
FROM nginxinc/nginx-unprivileged:1.29-alpine

# Les fichiers de configuration placés dans templates/ passent par envsubst au démarrage :
# ${BACKEND_URL} et ${MAILPIT_URL} sont remplacés par les valeurs du conteneur.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist/stagehub/browser/ /usr/share/nginx/html/

ENV BACKEND_URL=http://backend:8080 \
    MAILPIT_URL=http://mailpit:8025

# L'image "unprivileged" tourne déjà avec l'utilisateur nginx (uid 101) et écoute sur 8080
EXPOSE 8080
