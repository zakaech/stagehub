/**
 * Conversions entre Date (datepicker) et "AAAA-MM-JJ" (LocalDate côté Java), en heure locale.
 * On évite toISOString() : elle convertit en UTC et peut décaler la date d'un jour (ex. minuit à Casablanca).
 */
export function versDateIso(date: Date): string {
  const mois = String(date.getMonth() + 1).padStart(2, '0');
  const jour = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${mois}-${jour}`;
}

export function depuisDateIso(iso: string): Date {
  const [annee, mois, jour] = iso.split('-').map(Number);
  return new Date(annee, mois - 1, jour);
}

/** Vrai si la date "AAAA-MM-JJ" est avant aujourd'hui (même règle que Offre.dateLimiteDepassee). */
export function dateDepassee(iso: string, aujourdhui = new Date()): boolean {
  return iso < versDateIso(aujourdhui);
}
