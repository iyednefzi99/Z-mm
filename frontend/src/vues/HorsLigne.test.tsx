import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { definir, reinitialiserSession } from '../auth/session';
import { LangueProvider } from '../i18n/langue';
import { DialoguesProvider } from '../ui/dialogues';
import { HorsLigneVue } from './HorsLigneVue';
import { emports, ranger } from '../offline/emport';
import { enfiler, refus, rejouer, tailleFile } from '../offline/file';
import type { EmportRucher, Site } from '../api/types';

/**
 * Tests de l'écran « Hors ligne » (SPRINT-24, lot C).
 *
 * <p>Quatre comportements, et ce sont les quatre par lesquels une fonction hors
 * ligne trahit son utilisateur :
 *
 * <ol>
 *   <li>la <strong>date du prélèvement s'affiche</strong> — sans elle, une
 *       donnée du disque passe pour une donnée fraîche, ce qui est l'objection
 *       même que `vite.config.ts` opposait au cache depuis le SPRINT-13 ;
 *   <li>un emport <strong>périmé le dit</strong> plutôt que de se taire ;
 *   <li>une saisie refusée au retour <strong>reste consultable</strong> avec le
 *       motif du serveur, et l'apiculteur tranche ;
 *   <li>le mode économie <strong>dit ce qu'il coupe</strong> : une application
 *       qui change de comportement sans l'annoncer passe pour cassée.
 * </ol>
 */
vi.mock('../api/client', () => ({
  agents: { lister: vi.fn() },
  listerBrouillons: vi.fn(),
  effacerBrouillon: vi.fn(),
  emporterRucher: vi.fn(),
  ouvrirFicheInspection: vi.fn(),
}));

const client = await import('../api/client');

const instantane = (siteId: number, nom: string, preleveLe: string): EmportRucher => ({
  preleveLe,
  site: { id: siteId, nom } as Site,
  ruches: [{ id: 1 }, { id: 2 }] as EmportRucher['ruches'],
  dernieresVisites: [],
  tachesOuvertes: [{ id: 9 }] as EmportRucher['tachesOuvertes'],
  sousCarence: [],
});

const ilYA = (jours: number): string =>
  new Date(Date.now() - jours * 86_400_000).toISOString();

beforeEach(() => {
  vi.mocked(client.agents.lister).mockResolvedValue([]);
  vi.mocked(client.listerBrouillons).mockResolvedValue([]);
  definir({ utilisateur: 'lea', roles: ['responsable'], exploitation: 'demo' });
});

afterEach(() => {
  reinitialiserSession();
  vi.clearAllMocks();
});

const monter = () =>
  render(
    <LangueProvider>
      <DialoguesProvider>
        <HorsLigneVue />
      </DialoguesProvider>
    </LangueProvider>,
  );

describe('écran hors ligne — ruchers emportés', () => {
  it('affiche la date du prélèvement, jamais le seul nom du rucher', async () => {
    ranger(instantane(7, 'Rucher du causse', ilYA(1)));

    monter();

    // findBy plutôt que getBy : le montage déclenche aussi agents.lister()
    // (autre effet de l'écran), et attendre son règlement évite l'avertissement
    // React « update not wrapped in act » — un update non attendu ici, c'est
    // une course qui peut déraper sous charge.
    expect(await screen.findByText('Rucher du causse')).toBeInTheDocument();
    // C'est tout l'objet de l'ADR-012 : la date accompagne la donnée partout où
    // elle sert, pas seulement là où l'emport a été déclenché.
    expect(screen.getByText(/prélevé le/)).toBeInTheDocument();
    expect(screen.getByText(/2 ruche\(s\)/)).toBeInTheDocument();
  });

  it('signale un emport trop ancien au lieu de le présenter comme les autres', async () => {
    ranger(instantane(7, 'Rucher oublié', ilYA(30)));

    monter();

    expect(await screen.findByText(/trop ancien pour être consulté/)).toBeInTheDocument();
  });

  it('purge un rucher emporté', async () => {
    ranger(instantane(7, 'Rucher du causse', ilYA(1)));
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'Purger' }));

    expect(emports()).toEqual([]);
    expect(screen.getByText('Aucun rucher emporté.')).toBeInTheDocument();
  });

  it('dit qu’emporter demande du réseau quand l’appel échoue', async () => {
    ranger(instantane(7, 'Rucher du causse', ilYA(1)));
    vi.mocked(client.emporterRucher).mockRejectedValue(new Error('hors ligne'));
    monter();

    await userEvent.click(screen.getByRole('button', { name: 'Actualiser' }));

    // La seule fonction du produit dont l'échec hors ligne est normal : le dire
    // évite de la croire cassée.
    expect(await screen.findByText(/Emporter demande du réseau/)).toBeInTheDocument();
  });
});

describe('écran hors ligne — saisies refusées', () => {
  it('montre le motif du serveur et laisse l’apiculteur trancher', async () => {
    enfiler({ methode: 'PUT', url: '/api/visites/1', corps: '{"constatations":"cinq cadres"}' });
    await rejouer(() =>
      Promise.resolve({
        ok: false,
        reseau: false,
        refus: { statut: 409, detail: 'La visite 1 a été modifiée depuis votre dernière lecture.' },
      }),
    );

    monter();

    expect(screen.getByText(/La visite 1 a été modifiée/)).toBeInTheDocument();

    // « Ma saisie l'emporte » la remet dans la file : le serveur ne décide pas
    // laquelle de deux observations dit vrai sur une colonie.
    await userEvent.click(screen.getByRole('button', { name: 'Réappliquer ma saisie' }));
    expect(tailleFile()).toBe(1);
    expect(refus()).toEqual([]);
  });

  it('dit qu’il n’y a rien à arbitrer quand tout est passé', async () => {
    monter();
    expect(await screen.findByText('Aucune saisie refusée.')).toBeInTheDocument();
  });
});

describe('écran hors ligne — réglages du terrain', () => {
  it('dit ce que le mode économie coupe, et le mémorise', async () => {
    monter();

    const bascule = screen.getByLabelText('Mode économie');
    expect(screen.getByText(/Coupe le fond cartographique/)).toBeInTheDocument();

    await userEvent.click(bascule);

    expect(localStorage.getItem('zumm.economie')).toBe('oui');
    // La classe pilote les jetons : c'est elle qui coupe réellement les
    // animations, pas seulement la case cochée.
    expect(document.documentElement.classList.contains('z-economie')).toBe(true);
  });

  it('explique l’ajout manuel quand le navigateur ne propose pas l’installation', async () => {
    // Safari n'émet jamais `beforeinstallprompt` : sans cette phrase, la moitié
    // du parc verrait un écran qui ne propose rien.
    monter();
    expect(await screen.findByText(/menu de partage/)).toBeInTheDocument();
  });
});
