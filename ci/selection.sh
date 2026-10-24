#!/bin/sh
# Que faut-il tester ? (Jenkinsfile, étape Préparation)
#
#   sh ci/selection.sh <commit du dernier build réussi, ou vide> <complet : true|false>
#
# Affiche trois lignes clé=valeur :
#   MODULES  = modules Maven modifiés, séparés par des virgules (Jenkins ajoute ceux qui en dépendent : -amd),
#              TOUS, ou vide (aucun test Java)
#   FRONTEND = true si frontend/ a changé
#   RAISON   = explication affichée dans la console du build
#
# Tout est retesté si on ne peut pas raisonner sur un diff (premier build, historique réécrit, build complet demandé)
# ou si un fichier commun à tous les modules a changé : POM parent, Dockerfile commun, wrapper Maven, pipeline.
set -eu

base="${1:-}"
complet="${2:-false}"

tout() {
    echo "MODULES=TOUS"
    echo "FRONTEND=true"
    echo "RAISON=$1"
    exit 0
}

[ "$complet" = true ] && tout "build complet demandé (paramètre COMPLET ou build planifié)"
[ -z "$base" ] && tout "aucun build réussi précédent sur cette branche"
git cat-file -e "$base^{commit}" 2>/dev/null || tout "commit de référence $base absent de l'historique"

modules=""
frontend=false
for fichier in $(git diff --name-only "$base" HEAD); do
    case "$fichier" in
        Jenkinsfile | ci/selection.sh | ci/tags.sh | services/pom.xml | services/Dockerfile | services/.dockerignore \
            | services/mvnw | services/mvnw.cmd | services/.mvn/*)
            tout "$fichier modifié : concerne tous les modules"
            ;;
        services/*/*)
            module=${fichier#services/}
            module=${module%%/*}
            # Module supprimé dans ce diff : plus rien à tester
            if [ -f "services/$module/pom.xml" ]; then
                case ",$modules," in
                    *",$module,"*) ;;
                    *) modules="${modules:+$modules,}$module" ;;
                esac
            fi
            ;;
        frontend/*)
            frontend=true
            ;;
    esac
done

echo "MODULES=$modules"
echo "FRONTEND=$frontend"
echo "RAISON=fichiers modifiés depuis $(git rev-parse --short "$base")"
