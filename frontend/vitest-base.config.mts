import { defineConfig } from 'vitest/config';

// Configuration Vitest ajoutée à celle du builder Angular (angular.json : test.options.runnerConfig).
export default defineConfig({
  test: {
    // 5 s par défaut. Le premier test d'un fichier compile le composant et Angular Material : 1 à 2 s d'habitude,
    // mais plus de 6 s quand Jenkins tourne sur un PC chargé (build n° 14, faux échec). 15 s laissent de la marge
    // sans masquer un vrai blocage.
    testTimeout: 15_000,
  },
});
