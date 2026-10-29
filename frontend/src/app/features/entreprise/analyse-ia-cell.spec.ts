import { TestBed } from '@angular/core/testing';

import { AnalyseIa } from '../../core/models/candidature.models';
import { AnalyseIaCell } from './analyse-ia-cell';

const BASE: AnalyseIa = {
  statut: 'EN_ATTENTE',
  motifEchec: null,
  prochaineTentative: null,
  score: null,
  justification: null,
  resume: [],
  technologiesTrouvees: [],
  technologiesOffre: null,
  alerteEcart: false,
  contenuSuspect: false,
  modele: null,
  date: null,
};

describe('AnalyseIaCell', () => {
  const afficher = async (analyse: AnalyseIa | null) => {
    await TestBed.configureTestingModule({ imports: [AnalyseIaCell] }).compileComponents();
    const fixture = TestBed.createComponent(AnalyseIaCell);
    fixture.componentRef.setInput('analyse', analyse);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  };

  it('terminée : score indicatif, technologies « 2 sur 4 » et alertes à côté du score', async () => {
    const { fixture, element } = await afficher({
      ...BASE,
      statut: 'TERMINEE',
      score: 85,
      technologiesTrouvees: ['Docker', 'Jenkins'],
      technologiesOffre: 4,
      alerteEcart: true,
      contenuSuspect: true,
    });
    const details = vi.fn();
    fixture.componentInstance.details.subscribe(details);

    const texte = element.textContent!.replace(/\s+/g, ' ');
    expect(texte).toContain('85/100');
    expect(texte).toContain('indicatif');
    expect(texte).toContain('2 sur 4');
    expect(element.querySelector('[aria-label^="Écart flagrant"]')).not.toBeNull();
    expect(element.querySelector('[aria-label^="Contenu inhabituel"]')).not.toBeNull();
    element.querySelector<HTMLButtonElement>('button.details')!.click();
    expect(details).toHaveBeenCalled();
  });

  it('terminée sans alerte : aucune icône d’alerte', async () => {
    const { element } = await afficher({
      ...BASE,
      statut: 'TERMINEE',
      score: 40,
      technologiesOffre: 3,
    });

    expect(element.textContent).toContain('0 sur 3');
    expect(element.querySelector('.alerte')).toBeNull();
  });

  it('en cours', async () => {
    const { element } = await afficher({ ...BASE, statut: 'EN_COURS' });

    expect(element.textContent).toContain('Analyse en cours');
    expect(element.textContent).not.toContain('/100');
  });

  it('en attente, puis nouvelle tentative programmée après une panne', async () => {
    const { element } = await afficher(BASE);
    expect(element.textContent).toContain('En attente d’analyse');

    TestBed.resetTestingModule();
    const { element: reportee } = await afficher({
      ...BASE,
      prochaineTentative: new Date(2026, 9, 28, 14, 35).toISOString(),
    });
    expect(reportee.textContent).toContain('Nouvelle tentative à 14:35');
  });

  it('impossible : le motif en clair', async () => {
    const { element } = await afficher({ ...BASE, statut: 'ECHEC', motifEchec: 'CV_ABSENT' });

    expect(element.textContent).toContain('Analyse impossible : pas de CV joint');
  });

  it('IA désactivée', async () => {
    const { element } = await afficher({ ...BASE, statut: 'DESACTIVEE' });

    expect(element.textContent).toContain('Analyse automatique désactivée');
  });

  it('candidature antérieure à l’analyse : un tiret', async () => {
    const { element } = await afficher(null);

    expect(element.textContent!.trim()).toBe('—');
  });
});
