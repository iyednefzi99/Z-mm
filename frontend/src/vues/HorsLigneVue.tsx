import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  effacerBrouillon,
  emporterRucher,
  listerBrouillons,
  ouvrirFicheInspection,
} from '../api/client';
import type { Agent, Brouillon } from '../api/types';
import { gabarit } from '../i18n/console';
import { useFormats, useT } from '../i18n/langue';
import {
  ageEnJours,
  emports,
  purger,
  purgerTout,
  ranger,
  type EmportLocal,
} from '../offline/emport';
import { abandonner, reappliquer, refus, type MutationRefusee } from '../offline/file';
import { dejaInstallee, proposerInstallation, useInstallationPossible } from '../pwa';
import { useEconomie } from '../terrain/economie';
import { Bouton, ChampSelect, type Option } from '../ui/composants';

/**
 * Le terrain sans réseau (SPRINT-24, lot C).
 *
 * <p>Cet écran rassemble ce qui n'a de sens qu'ensemble : ce qu'on a emporté,
 * ce qu'on a commencé à saisir, ce que le serveur a refusé au retour, et les
 * deux réglages qui décident de la journée. Éparpiller ces quatre choses dans
 * les écrans métier les aurait rendues introuvables au moment précis où on les
 * cherche — c'est-à-dire avant de partir, et au retour.
 *
 * <p><strong>Ce qui n'est pas ici, et pourquoi.</strong> Le bouton « Emporter »
 * vit aussi sur l'écran des ruchers : c'est là qu'on choisit où l'on va. Cet
 * écran-ci rend compte de ce qui est emporté, il n'est pas le seul endroit d'où
 * l'on emporte.
 */
export function HorsLigneVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const [economie, definirEconomie] = useEconomie();
  const installationPossible = useInstallationPossible();

  const [liste, setListe] = useState<EmportLocal[]>(emports);
  const [refuses, setRefuses] = useState<MutationRefusee[]>(refus);
  const [brouillons, setBrouillons] = useState<Brouillon[] | null>(null);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [agentId, setAgentId] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [enCours, setEnCours] = useState<number | null>(null);

  const rafraichir = useCallback(() => {
    setListe(emports());
    setRefuses(refus());
  }, []);

  useEffect(() => {
    void agents
      .lister()
      .then((liste: Agent[]) =>
        setOptAgents([
          { valeur: '', libelle: t.champs.aucun },
          ...liste.map((a) => ({ valeur: String(a.id), libelle: a.nom })),
        ]),
      )
      .catch(() => setOptAgents([]));
  }, [t.champs.aucun]);

  useEffect(() => {
    if (agentId === '') {
      // Tant qu'aucun agent n'est choisi, la liste reste vide plutôt que de
      // montrer les brouillons de quelqu'un : côté serveur, la politique RLS de
      // la V24 dit la même chose — un brouillon appartient à son agent, pas au
      // tenant, et une portée globale ne l'ouvre pas.
      setBrouillons([]);
      return;
    }
    setBrouillons(null);
    void listerBrouillons(Number(agentId))
      .then(setBrouillons)
      .catch(() => setBrouillons([]));
  }, [agentId]);

  /** Reprend l'instantané d'un rucher déjà emporté : même geste, date neuve. */
  const actualiser = async (emport: EmportLocal) => {
    setEnCours(emport.siteId);
    setMessage(null);
    try {
      const neuf = ranger(await emporterRucher(emport.siteId));
      setMessage(
        gabarit(t.horsLigne.emporte, {
          rucher: neuf.siteNom,
          instant: f.dateHeure(neuf.preleveLe),
        }),
      );
    } catch {
      setMessage(t.horsLigne.emportImpossible);
    } finally {
      setEnCours(null);
      rafraichir();
    }
  };

  const installer = async () => {
    const accepte = await proposerInstallation();
    setMessage(accepte ? t.horsLigne.installee : t.horsLigne.installationRefusee);
  };

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h2 className="z-section__titre">{t.onglets.horsligne}</h2>
          <p className="z-section__soustitre">{t.soustitres.horsligne}</p>
        </div>
      </header>

      {message && (
        <p className="z-info" role="status">
          {message}
        </p>
      )}

      {/* ─── Ruchers emportés ────────────────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.horsLigne.ruchersEmportes}</h3>
        {/* La date de prélèvement est répétée sur CHAQUE ligne, et c'est tout
            l'objet de l'ADR-012 : une donnée servie depuis le disque ne doit
            jamais pouvoir passer pour une donnée fraîche. */}
        <p className="z-info">{t.horsLigne.aide}</p>
        {liste.length === 0 ? (
          <p className="z-info">{t.horsLigne.aucunEmport}</p>
        ) : (
          <ul className="z-liste-simple">
            {liste.map((emport) => {
              const age = ageEnJours(emport);
              const trop = age >= 14;
              return (
                <li key={emport.siteId}>
                  <strong>{emport.siteNom}</strong>{' '}
                  <span className={trop ? 'z-erreur-texte' : 'z-info'}>
                    {gabarit(trop ? t.horsLigne.perime : t.horsLigne.preleveLe, {
                      instant: f.dateHeure(emport.preleveLe),
                      jours: String(age),
                    })}
                  </span>
                  <br />
                  <small className="z-info">
                    {gabarit(t.horsLigne.contenu, {
                      ruches: String(emport.contenu.ruches.length),
                      taches: String(emport.contenu.tachesOuvertes.length),
                      carences: String(emport.contenu.sousCarence.length),
                    })}
                  </small>
                  <br />
                  <button
                    type="button"
                    className="z-lien"
                    disabled={enCours === emport.siteId}
                    onClick={() => void actualiser(emport)}
                  >
                    {t.horsLigne.actualiser}
                  </button>{' '}
                  <button
                    type="button"
                    className="z-lien"
                    onClick={() => void ouvrirFicheInspection(emport.siteId).catch(() => undefined)}
                  >
                    {t.horsLigne.ficheVierge}
                  </button>{' '}
                  <button
                    type="button"
                    className="z-lien"
                    onClick={() => {
                      purger(emport.siteId);
                      rafraichir();
                    }}
                  >
                    {t.horsLigne.purger}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
        {liste.length > 0 && (
          <Bouton
            variante="fantome"
            onClick={() => {
              purgerTout();
              rafraichir();
            }}
          >
            {t.horsLigne.purgerTout}
          </Bouton>
        )}
      </article>

      {/* ─── Saisies refusées au retour ──────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.horsLigne.refuses}</h3>
        <p className="z-info">{t.horsLigne.refusAide}</p>
        {refuses.length === 0 ? (
          <p className="z-info">{t.horsLigne.aucunRefus}</p>
        ) : (
          <ul className="z-liste-simple">
            {refuses.map((r) => (
              <li key={r.mutation.id}>
                <strong>
                  {r.mutation.methode} {r.mutation.url}
                </strong>
                <br />
                <span className="z-erreur-texte">{r.detail}</span>
                <br />
                <small className="z-info">{f.dateHeure(r.refuseeLe)}</small>
                <br />
                {/* Deux issues, et le serveur n'en choisit aucune : décider
                    laquelle de deux observations dit vrai sur une colonie est un
                    arbitrage d'apiculteur, pas une règle de précédence. */}
                <button
                  type="button"
                  className="z-lien"
                  onClick={() => {
                    reappliquer(r.mutation.id);
                    rafraichir();
                  }}
                >
                  {t.horsLigne.reappliquer}
                </button>{' '}
                <button
                  type="button"
                  className="z-lien"
                  onClick={() => {
                    abandonner(r.mutation.id);
                    rafraichir();
                  }}
                >
                  {t.horsLigne.abandonner}
                </button>
              </li>
            ))}
          </ul>
        )}
      </article>

      {/* ─── Brouillons de visite ────────────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.horsLigne.brouillons}</h3>
        <p className="z-info">{t.horsLigne.brouillonsAide}</p>
        <ChampSelect
          libelle={t.visite.agent}
          valeur={agentId}
          options={optAgents}
          onChange={setAgentId}
        />
        {brouillons === null && <p className="z-info">{t.etats.chargement}</p>}
        {brouillons !== null && brouillons.length === 0 && (
          <p className="z-info">{t.horsLigne.aucunBrouillon}</p>
        )}
        {brouillons !== null && brouillons.length > 0 && (
          <ul className="z-liste-simple">
            {brouillons.map((b) => (
              <li key={b.id}>
                <strong>{b.rucheModele}</strong>
                {b.siteNom ? <small className="z-info"> · {b.siteNom}</small> : null}
                <br />
                <small className="z-info">
                  {gabarit(t.horsLigne.brouillonDe, {
                    instant: f.dateHeure(b.majLe),
                    appareil: b.appareil ?? t.horsLigne.appareilInconnu,
                  })}
                </small>
                <br />
                <button
                  type="button"
                  className="z-lien"
                  onClick={() => {
                    void effacerBrouillon(b.id)
                      .then(() => setBrouillons((avant) => (avant ?? []).filter((x) => x.id !== b.id)))
                      .catch(() => undefined);
                  }}
                >
                  {t.horsLigne.effacerBrouillon}
                </button>
              </li>
            ))}
          </ul>
        )}
      </article>

      {/* ─── Réglages du terrain ─────────────────────────────────────────── */}
      <article className="z-carte">
        <h3 className="z-carte__titre">{t.horsLigne.reglages}</h3>
        <label className="z-champ__case">
          <input
            type="checkbox"
            checked={economie}
            onChange={(e) => definirEconomie(e.target.checked)}
          />
          {t.horsLigne.economie}
        </label>
        {/* Le mode DIT ce qu'il coupe. Une application qui change de
            comportement sans l'annoncer passe pour cassée. */}
        <p className="z-info">{t.horsLigne.economieAide}</p>

        {dejaInstallee() ? (
          <p className="z-info">{t.horsLigne.dejaInstallee}</p>
        ) : (
          <>
            <p className="z-info">{t.horsLigne.installerAide}</p>
            {installationPossible ? (
              <Bouton variante="secondaire" onClick={() => void installer()}>
                {t.horsLigne.installer}
              </Bouton>
            ) : (
              // Safari n'émet jamais `beforeinstallprompt` : sans cette phrase,
              // la moitié du parc verrait un écran qui ne propose rien.
              <p className="z-info">{t.horsLigne.installationManuelle}</p>
            )}
          </>
        )}
      </article>
    </section>
  );
}
