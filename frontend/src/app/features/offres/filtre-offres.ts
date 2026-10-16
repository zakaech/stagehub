import { ParamMap, Params } from '@angular/router';

import { OffreFiltre } from '../../core/models/offre.models';

/** Lit les filtres dans l'URL : ?ville=Rabat&domaine=Data&technologies=Java&technologies=SQL */
export function lireFiltreOffres(params: ParamMap): OffreFiltre {
  return {
    ville: nettoyer(params.get('ville')),
    domaine: nettoyer(params.get('domaine')),
    technologies: params
      .getAll('technologies')
      .map((t) => t.trim())
      .filter((t) => t.length > 0),
  };
}

/** Query params correspondant à un filtre ; null retire le paramètre de l'URL. */
export function paramsDeFiltreOffres(filtre: OffreFiltre): Params {
  return {
    ville: filtre.ville || null,
    domaine: filtre.domaine || null,
    technologies: filtre.technologies.length > 0 ? filtre.technologies : null,
  };
}

export function filtreActif(filtre: OffreFiltre): boolean {
  return !!filtre.ville || !!filtre.domaine || filtre.technologies.length > 0;
}

/** Les filtres ignorent la casse côté API : l'option "Rabat" doit apparaître sélectionnée pour ?ville=rabat. */
export function memeTexte(a: string | null, b: string | null): boolean {
  return (a ?? '').toLocaleLowerCase('fr') === (b ?? '').toLocaleLowerCase('fr');
}

function nettoyer(valeur: string | null): string | null {
  const propre = valeur?.trim();
  return propre ? propre : null;
}
