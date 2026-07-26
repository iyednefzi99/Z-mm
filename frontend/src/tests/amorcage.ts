/**
 * Amorçage des tests front (US-049, SPRINT-10).
 *
 * Charge les assertions DOM de jest-dom et remet l'état global à zéro entre deux
 * tests : `localStorage` porte la file hors-ligne (US-011) et la langue choisie
 * (US-024), un test qui en laisserait la trace fausserait le suivant.
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

// jsdom n'implémente pas `getContext` et journalise une erreur bruyante à chaque
// appel. La carte interroge WebGL pour décider entre le fond MapLibre et le repli
// SVG (`webglDisponible`) : on répond « pas de contexte », ce qui est exactement
// la situation d'un environnement de test — et le repli SVG est alors testé.
HTMLCanvasElement.prototype.getContext = () => null;

beforeEach(() => {
  localStorage.clear();
  document.documentElement.lang = 'fr';
  document.documentElement.dir = 'ltr';
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
