#!/bin/sh
# Tag de chaque image = empreinte de ce qui entre dans l'image (Jenkinsfile, étape Préparation)
#
#   sh ci/tags.sh <plateformes> frontend <service>...   >   images.env
#
# Affiche une ligne TAG_<NOM>=<empreinte> par image (ex. TAG_OFFRE_SERVICE=3f9c0a1b2d4e), lue par docker compose.
# L'empreinte combine les arbres git (contenu commité) du module, des modules internes dont il dépend (commons,
# outbox, audit : lus dans son pom.xml, de proche en proche), du POM parent, du Dockerfile commun et du wrapper Maven,
# plus les plateformes : même contenu → même tag → image déjà construite, réutilisée telle quelle.
set -eu

plateformes="$1"
shift

arbre() {
    git rev-parse "HEAD:$1" 2>/dev/null || echo "absent"
}

# Modules internes dont dépend services/$1, directement ou non
dependances() {
    a_voir="$1"
    vus=""
    while [ -n "$a_voir" ]; do
        module=${a_voir%% *}
        a_voir=${a_voir#"$module"}
        a_voir=${a_voir# }
        for artefact in $(sed -n 's:.*<artifactId>\(.*\)</artifactId>.*:\1:p' "services/$module/pom.xml"); do
            if [ "$artefact" != "$module" ] && [ -f "services/$artefact/pom.xml" ]; then
                case " $vus " in
                    *" $artefact "*) ;;
                    *) vus="$vus $artefact" ; a_voir="${a_voir:+$a_voir }$artefact" ;;
                esac
            fi
        done
    done
    echo $vus
}

empreinte() {
    sha256sum | cut -c1-12
}

for nom in "$@"; do
    if [ "$nom" = frontend ]; then
        tag=$(printf '%s\n' "$plateformes" "$(arbre frontend)" | empreinte)
    else
        tag=$( {
            printf '%s\n' "$plateformes" "$(arbre services/pom.xml)" "$(arbre services/Dockerfile)" \
                "$(arbre services/.dockerignore)" "$(arbre services/.mvn)" "$(arbre "services/$nom")"
            for dependance in $(dependances "$nom"); do
                arbre "services/$dependance"
            done
        } | empreinte )
    fi
    echo "TAG_$(echo "$nom" | tr 'a-z-' 'A-Z_')=$tag"
done
