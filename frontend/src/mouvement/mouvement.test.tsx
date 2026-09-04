import { act, render, renderHook, screen } from '@testing-library/react';
import { m } from 'motion/react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { MouvementLeger } from './MouvementLeger';
import { useApparition, useEchelonnement, useMouvementReduit } from './apparition';
import { jetonsMouvement, relachement, ressorts, transitions } from './jetons';
import { readFileSync } from 'node:fs';

// La feuille est lue sur le disque, et non importée en `?raw` : la configuration
// Vitest du dépôt pose `css: false`, sous laquelle un import `?raw` renvoie une
// chaîne VIDE. Le test serait alors vert sans avoir rien comparé — soit exactement
// la dérive silencieuse qu'il est là pour empêcher. Les types Node manquants sont
// déclarés dans `node.d.ts`, à côté ; le front n'embarque pas `@types/node`.
const CSS = readFileSync(process.cwd() + '/src/theme/tokens.css', 'utf8');

// `LazyMotion` charge ses fonctionnalités par un `import()` et pose son état quand
// la promesse tombe. Si elle tombe APRÈS la fin du test, jsdom n'existe plus et le
// `setState` casse sur `window is not defined` — un rejet non intercepté, qui fait
// echouer la campagne une fois sur quatre sans qu'aucun test ne soit rouge.
// Pré-charger le module met la promesse en cache : elle se résout alors dès la
// première micro-tâche, que le `flush` ci-dessous attend, du vivant de jsdom.
beforeAll(async () => {
  await import('./fonctionnalites');
});

const flush = () => act(async () => undefined);

// `design/tokens.json` est le miroir machine-lisible de la charte (format DTCG).
// L'inertie n'ayant aucun equivalent CSS, c'est LUI qui en est la source — d'ou
// cette seconde lecture, un cran plus haut que le front.
const CHARTE: {
  motion: { inertia: Record<string, { $value: number | string }> };
} = JSON.parse(readFileSync(process.cwd() + '/../design/tokens.json', 'utf8'));

/** Lit une variable CSS de `tokens.css`. Échoue si le jeton a été renommé. */
function jetonCss(nom: string): string {
  const trouve = new RegExp(nom + String.raw`:\s*([^;]+);`).exec(CSS);
  // Une feuille vide ferait echouer chaque jeton avec le meme message trompeur.
  if (CSS.length === 0) {
    throw new Error('theme/tokens.css lu vide — chemin de lecture a revoir.');
  }
  if (trouve === null) {
    throw new Error(`Jeton ${nom} absent de theme/tokens.css`);
  }
  return trouve[1].trim();
}

function millisecondes(nom: string): number {
  return Number.parseFloat(jetonCss(nom).replace('ms', '')) / 1000;
}

function bezier(courbe: readonly number[]): string {
  return `cubic-bezier(${courbe.join(', ')})`;
}

/** Force la réponse de `matchMedia` sur la requête de mouvement réduit. */
function preferer(reduit: boolean): void {
  vi.stubGlobal('matchMedia', (requete: string) => ({
    matches: requete.includes('prefers-reduced-motion') && reduit,
    media: requete,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
}

/**
 * Ces trois tests sont la raison d'être du fichier de jetons : ils échouent dès
 * qu'une durée ou une courbe diverge entre le CSS et son miroir TypeScript. Sans
 * eux, les deux couches dériveraient en silence — le piège que CLAUDE.md documente
 * sur les comptes de tests et les entités.
 */
describe('jetons de mouvement', () => {
  it('reprend exactement les durées de tokens.css', () => {
    expect(jetonsMouvement.duree.rapide).toBe(millisecondes('--z-duration-fast'));
    expect(jetonsMouvement.duree.base).toBe(millisecondes('--z-duration-base'));
    expect(jetonsMouvement.duree.lente).toBe(millisecondes('--z-duration-slow'));
    expect(jetonsMouvement.duree.modaleOuverture).toBe(millisecondes('--z-modal-open-dur'));
    expect(jetonsMouvement.duree.modaleFermeture).toBe(millisecondes('--z-modal-close-dur'));
  });

  it('reprend exactement les courbes de tokens.css', () => {
    expect(bezier(jetonsMouvement.courbe.sortie)).toBe(jetonCss('--z-ease-out'));
    expect(bezier(jetonsMouvement.courbe.entree)).toBe(jetonCss('--z-ease-in'));
    expect(bezier(jetonsMouvement.courbe.entreeSortie)).toBe(jetonCss('--z-ease-inout'));
  });

  it("reprend l'échelle de la modale", () => {
    expect(jetonsMouvement.echelle.apparition).toBe(Number(jetonCss('--z-modal-scale')));
  });

  it("sort une modale plus vite qu'elle ne l'ouvre", () => {
    // « Entrée vive, sortie posée » (design/motion/transitions.md). Une inversion
    // ici se verrait à peine et se corrigerait mal : le test la nomme.
    expect(transitions.modaleFermeture.duration).toBeLessThan(transitions.modaleOuverture.duration);
    expect(transitions.sortie.duration).toBeLessThan(transitions.entree.duration);
  });

  it('ne fait dépasser aucun ressort', () => {
    // Aucune courbe de la charte n'overshoote ; un `bounce` non nul introduirait
    // un vocabulaire de mouvement que le design ne contient pas.
    for (const ressort of Object.values(ressorts)) {
      expect(ressort.bounce).toBe(0);
    }
  });
});

describe('garde de mouvement réduit', () => {
  it('détecte la préférence système', () => {
    preferer(true);
    expect(renderHook(() => useMouvementReduit()).result.current).toBe(true);

    preferer(false);
    expect(renderHook(() => useMouvementReduit()).result.current).toBe(false);
  });

  it('vaut false quand matchMedia est absent', () => {
    vi.stubGlobal('matchMedia', undefined);
    expect(renderHook(() => useMouvementReduit()).result.current).toBe(false);
  });

  it('anime un fondu montant par défaut', () => {
    preferer(false);
    const { result } = renderHook(() => useApparition());
    expect(result.current.initial).toEqual({ opacity: 0, y: jetonsMouvement.distance.decalage });
    expect(result.current.animate).toEqual({ opacity: 1, y: 0 });
    expect(result.current.transition).toBe(transitions.entree);
  });

  it('immobilise tout sous prefers-reduced-motion', () => {
    preferer(true);
    const { result } = renderHook(() => useApparition());
    // Ni décalage ni fondu : la neutralisation est totale, comme la garde CSS.
    expect(result.current.initial).toEqual({ opacity: 1, y: 0 });
    expect(result.current.animate).toEqual({ opacity: 1, y: 0 });
    expect(result.current.transition).toEqual({ duration: 0 });
  });

  it("coupe l'échelonnement sous prefers-reduced-motion", () => {
    preferer(false);
    expect(renderHook(() => useEchelonnement()).result.current.conteneur.visible).toEqual({
      transition: { staggerChildren: jetonsMouvement.echelonnement },
    });

    preferer(true);
    expect(renderHook(() => useEchelonnement()).result.current.conteneur.visible).toEqual({
      transition: { staggerChildren: 0 },
    });
  });
});

describe('MouvementLeger', () => {
  it('rend son contenu avant même que les fonctionnalités soient chargées', async () => {
    preferer(false);
    render(
      <MouvementLeger>
        <m.div>ruche 12</m.div>
      </MouvementLeger>,
    );
    expect(await screen.findByText('ruche 12')).toBeInTheDocument();
    await flush();
  });
});

describe('relâchement', () => {
  it('ne projette rien', () => {
    // Le cœur de la décision : un élément lâché rejoint un état décidé, il ne
    // part pas vers une position calculée depuis la vitesse du geste.
    expect(relachement.power).toBe(0);
  });

  it('rappelle sans jamais dépasser la cible', () => {
    // Amortissement critique. En dessous, le rappel rebondit — un vocabulaire de
    // mouvement que la charte ne contient nulle part (cf. `ressorts`, bounce 0).
    expect(relachement.bounceDamping ** 2).toBe(4 * relachement.bounceStiffness);
  });

  it('se pose dans la durée standard de la charte', () => {
    // Un ressort critique a restitué ~98 % du deplacement a wt = 6, soit
    // t = 12 / amortissement. Ce test lie le rappel a `duree.base` : changer l'un
    // sans l'autre casse ici.
    expect(12 / relachement.bounceDamping).toBeCloseTo(jetonsMouvement.duree.base, 10);
  });

  it('ne laisserait pas une projection survivre au plus lent des mouvements', () => {
    // Trois constantes de temps couvrent 95 % du glissement.
    expect((3 * relachement.timeConstant) / 1000).toBeCloseTo(jetonsMouvement.duree.lente, 10);
  });

  it('ne diverge pas de design/tokens.json', () => {
    // Trois miroirs portent cette decision : la prose de motion/transitions.md,
    // tokens.json et ce fichier. Les deux derniers sont verifiables — ce test les
    // verifie, pour que « inscrit dans la charte » ne devienne pas « ecrit une
    // fois puis oublie d'un cote ».
    const charte = CHARTE.motion.inertia;
    expect(relachement.power).toBe(charte.power.$value);
    expect(relachement.bounceStiffness).toBe(charte['bounce-stiffness'].$value);
    expect(relachement.bounceDamping).toBe(charte['bounce-damping'].$value);
    expect(relachement.timeConstant).toBeCloseTo(
      Number.parseFloat(String(charte['time-constant'].$value)),
      2,
    );
  });

  it('est accepté tel quel par dragTransition', async () => {
    // Ce test vaut surtout à la compilation : si la forme du jeton cessait de
    // correspondre à ce que Motion attend, `npm run typecheck` échouerait ici.
    render(
      <MouvementLeger>
        <m.div drag="y" dragTransition={relachement} data-testid="glissable" />
      </MouvementLeger>,
    );
    expect(screen.getByTestId('glissable')).toBeInTheDocument();
    await flush();
  });
});
