import { TestBed } from '@angular/core/testing';
import { MAT_DATE_LOCALE } from '@angular/material/core';

import { FrDateAdapter } from './fr-date-adapter';
import { dateDepassee, depuisDateIso, versDateIso } from './dates';

describe('dates', () => {
  it('convertit une Date locale en AAAA-MM-JJ sans décalage UTC (minuit pile)', () => {
    expect(versDateIso(new Date(2026, 10, 28, 0, 0))).toBe('2026-11-28');
    expect(versDateIso(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
  });

  it('relit AAAA-MM-JJ en Date locale', () => {
    const date = depuisDateIso('2026-11-28');
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 10, 28]);
  });

  it('considère la date limite du jour comme non dépassée (même règle que le backend)', () => {
    const aujourdhui = new Date(2026, 5, 1);
    expect(dateDepassee('2026-05-31', aujourdhui)).toBe(true);
    expect(dateDepassee('2026-06-01', aujourdhui)).toBe(false);
    expect(dateDepassee('2026-06-02', aujourdhui)).toBe(false);
  });
});

describe('FrDateAdapter', () => {
  let adapter: FrDateAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [FrDateAdapter, { provide: MAT_DATE_LOCALE, useValue: 'fr' }] });
    adapter = TestBed.inject(FrDateAdapter);
  });

  const jma = (d: Date | null) => (d ? [d.getDate(), d.getMonth() + 1, d.getFullYear()] : null);

  it('lit jj/mm/aaaa au format français', () => {
    expect(jma(adapter.parse('28/11/2026'))).toEqual([28, 11, 2026]);
    // Avec Date.parse, ce serait le 11 mai
    expect(jma(adapter.parse('05/11/2026'))).toEqual([5, 11, 2026]);
  });

  it('refuse une date impossible au lieu de la faire déborder', () => {
    const date = adapter.parse('31/02/2027');
    expect(date).not.toBeNull();
    expect(adapter.isValid(date!)).toBe(false);
  });
});
