import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { SanitaireVue } from './SanitaireVue';
import type { ComptageVarroa, Traitement } from '../api/types';

/**
 * Tests du registre sanitaire (SPRINT-20, US-086 à US-088).
 *
 * <p>Trois règles y sont vérifiées, et ce sont les trois qui échouent en
 * silence :
 *
 * <ol>
 *   <li>le dénominateur du comptage suit la MÉTHODE — un formulaire qui
 *       enverrait les deux, ou celui de l'autre méthode, se ferait refuser par
 *       `ck_varroa_denominateur` après la saisie ;
 *   <li>le taux ne s'affiche jamais sans son unité : « 3,5 » ne dit pas s'il
 *       s'agit de varroas par jour ou pour cent abeilles, et le seuil n'est pas
 *       le même ;
 *   <li>les carences en cours se lisent AVANT d'avoir choisi une ruche — c'est
 *       la réponse à « que puis-je récolter aujourd'hui », qui est une question
 *       d'exploitation, pas de colonie.
 * </ol>
 */
vi.mock('../api/client', () => ({
  ruches: { lister: vi.fn() },
  agents: { lister: vi.fn() },
  listerCarencesEnCours: vi.fn(),
  listerTraitements: vi.fn(),
  listerNourrissements: vi.fn(),
  listerComptagesVarroa: vi.fn(),
  enregistrerTraitement: vi.fn(),
  enregistrerNourrissement: vi.fn(),
  enregistrerComptageVarroa: vi.fn(),
  supprimerTraitement: vi.fn(),
  supprimerNourrissement: vi.fn(),
  supprimerComptageVarroa: vi.fn(),
  // Meme forme que la vraie : `messageErreur` lit `detail`, et un doublon
  // simplifie ferait passer un test qui n'affiche en realite aucun message.
  ErreurApi: class ErreurApi extends Error {
    constructor(
      readonly statut: number,
      readonly detail: string,
    ) {
      super(detail);
    }
  },
}));

const {
  agents,
  enregistrerComptageVarroa,
  enregistrerTraitement,
  listerCarencesEnCours,
  listerComptagesVarroa,
  listerNourrissements,
  listerTraitements,
  ruches,
} = await import('../api/client');

const SOUS_CARENCE: Traitement = {
  id: 5,
  rucheId: 1,
  rucheModele: 'Dadant 10',
  agentId: 3,
  agentNom: 'Awa Diop',
  visiteId: null,
  produit: 'Apivar',
  substanceActive: 'amitraze',
  cible: 'varroa',
  dose: 2,
  doseUnite: 'laniere',
  dateDebut: '2026-08-01',
  dateFin: '2026-08-20',
  delaiCarenceJours: 14,
  dateRetrait: '2026-09-03',
  sousCarence: true,
  ordonnance: null,
  note: null,
  creeLe: '2026-08-01T09:00:00Z',
  majLe: '2026-08-01T09:00:00Z',
};

const COMPTAGE_LANGE: ComptageVarroa = {
  id: 11,
  rucheId: 1,
  rucheModele: 'Dadant 10',
  agentId: 3,
  agentNom: 'Awa Diop',
  visiteId: null,
  dateComptage: '2026-08-25',
  methode: 'lange',
  varroasComptes: 21,
  abeillesEchantillon: null,
  joursExposition: 3,
  taux: 7,
  tauxUnite: 'varroas_par_jour',
  verdict: 'traiter',
  note: null,
  creeLe: '2026-08-25T09:00:00Z',
  majLe: '2026-08-25T09:00:00Z',
};

const monter = () =>
  render(
    <LangueProvider>
      <SanitaireVue />
    </LangueProvider>,
  );

/** Choisit la ruche 1 et l'agent 3, puis ouvre le volet demandé. */
const choisirRucheEtVolet = async (volet: string) => {
  await userEvent.selectOptions(await screen.findByLabelText('Choisir une ruche'), '1');
  await userEvent.selectOptions(screen.getByLabelText('Agent'), '3');
  await userEvent.click(await screen.findByRole('tab', { name: volet }));
};

describe('registre sanitaire', () => {
  beforeEach(() => {
    vi.mocked(ruches.lister).mockResolvedValue([{ id: 1, modele: 'Dadant 10' } as never]);
    vi.mocked(agents.lister).mockResolvedValue([{ id: 3, nom: 'Awa Diop' } as never]);
    vi.mocked(listerCarencesEnCours).mockResolvedValue([]);
    vi.mocked(listerTraitements).mockResolvedValue([]);
    vi.mocked(listerNourrissements).mockResolvedValue([]);
    vi.mocked(listerComptagesVarroa).mockResolvedValue([]);
  });

  it('annonce les ruches sous carence avant tout choix de ruche', async () => {
    vi.mocked(listerCarencesEnCours).mockResolvedValue([SOUS_CARENCE]);
    monter();

    // La ruche, le produit et surtout la date de fin : dire « non » sans dire
    // « jusqu'à quand » revient à faire contourner la règle. C'est la ligne du
    // bandeau qu'on lit — « Dadant 10 » figure aussi dans la liste des ruches.
    const ligne = await screen.findByRole('listitem');
    expect(ligne).toHaveTextContent('Dadant 10');
    expect(ligne).toHaveTextContent('Apivar');
    expect(ligne).toHaveTextContent('Récolte possible à partir du');
  });

  it('ne demande que le dénominateur de la méthode choisie', async () => {
    monter();
    await choisirRucheEtVolet('Varroa');

    // Lange : une durée de pose, et pas un nombre d'abeilles.
    expect(screen.getByLabelText('Jours de pose du lange')).toBeInTheDocument();
    expect(screen.queryByLabelText('Abeilles de l’échantillon')).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Méthode'), 'sucre_glace');

    expect(screen.getByLabelText('Abeilles de l’échantillon')).toBeInTheDocument();
    expect(screen.queryByLabelText('Jours de pose du lange')).not.toBeInTheDocument();
  });

  it('envoie le dénominateur de la méthode, et met l’autre à null', async () => {
    monter();
    await choisirRucheEtVolet('Varroa');

    await userEvent.type(screen.getByLabelText('Date'), '2026-08-25');
    await userEvent.type(screen.getByLabelText('Varroas comptés'), '21');
    await userEvent.type(screen.getByLabelText('Jours de pose du lange'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer un comptage' }));

    expect(enregistrerComptageVarroa).toHaveBeenCalledWith(
      expect.objectContaining({
        methode: 'lange',
        varroasComptes: 21,
        joursExposition: 3,
        abeillesEchantillon: null,
      }),
    );
  });

  it('affiche le taux avec son unité et le verdict en toutes lettres', async () => {
    vi.mocked(listerComptagesVarroa).mockResolvedValue([COMPTAGE_LANGE]);
    monter();
    await choisirRucheEtVolet('Varroa');

    // Ni « 7 » nu, ni une pastille rouge seule : l'unité et le mot.
    expect(await screen.findByText('7,00 varroas/jour')).toBeInTheDocument();
    expect(screen.getByText('Traiter')).toBeInTheDocument();
  });

  it('refuse une dose sans son unité — côté serveur, et sans perdre la saisie', async () => {
    const { ErreurApi } = await import('../api/client');
    vi.mocked(enregistrerTraitement).mockRejectedValue(
      new ErreurApi(400, 'Une dose est indiquee sans son unite : preciser mg, g, ml, l, laniere.'),
    );
    monter();
    await choisirRucheEtVolet('Traitements');

    await userEvent.type(screen.getByLabelText('Produit'), 'Apivar');
    await userEvent.type(screen.getByLabelText('Début'), '2026-08-01');
    await userEvent.type(screen.getByLabelText('Dose'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer un traitement' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('unite');
    // Le formulaire garde ce qui a été tapé : l'utilisateur corrige l'unité, il
    // ne recommence pas la saisie.
    expect(screen.getByLabelText('Produit')).toHaveValue('Apivar');
  });
});
