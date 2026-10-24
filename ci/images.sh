#!/bin/sh
# Images Docker de StageHub (Jenkinsfile, étapes Images et Publication GHCR)
#
#   sh ci/images.sh construire <plateformes>     construit les images dont le tag n'existe pas encore
#   sh ci/images.sh publier <registre>           pousse toutes les images sur le registre (ex. ghcr.io/zakaech)
#
# Les tags viennent de images.env (produit par ci/tags.sh) ; la liste des services, de la variable SERVICES.
set -eu

action="$1"
parametre="$2"

set -a
. ./images.env
set +a

# Tag de l'image d'une application : variable TAG_<NOM> de images.env (ex. offre-service → TAG_OFFRE_SERVICE)
tag_de() {
    eval "echo \"\$TAG_$(echo "$1" | tr 'a-z-' 'A-Z_')\""
}

for app in frontend $SERVICES; do
    tag=$(tag_de "$app")
    case "$action" in
        construire)
            if docker image inspect "stagehub-$app:$tag" > /dev/null 2>&1; then
                echo "stagehub-$app:$tag : contenu inchangé, image déjà construite"
            elif [ "$app" = frontend ]; then
                # buildx (BuildKit) : une variante par plateforme, réunies sous un même tag (manifest list) ;
                # --load la range dans le magasin d'images de DinD (containerd, qui accepte le multi-architecture)
                docker buildx build --platform "$parametre" --tag "stagehub-frontend:$tag" --load frontend
            else
                # Microservices : un seul Dockerfile (services/), le module est choisi par SERVICE
                docker buildx build --platform "$parametre" --build-arg SERVICE="$app" \
                    --tag "stagehub-$app:$tag" --load services
            fi
            ;;
        publier)
            docker tag "stagehub-$app:$tag" "$parametre/stagehub-$app:$tag"
            docker push "$parametre/stagehub-$app:$tag"
            ;;
        *)
            echo "Action inconnue : $action" >&2
            exit 1
            ;;
    esac
done
