import type { ReactElement } from 'react';
import type { Session } from '../auth/session';
import { gabarit } from '../i18n/console';
import { LANGUES } from '../i18n/messages';
import { useLangue, useT } from '../i18n/langue';
import { ONGLETS, ROUTES_PUBLIQUES } from '../routage/routes';
import { SelecteurTheme } from '../theme/theme';
import { Bouton } from '../ui/composants';

/** Les six domaines mis en avant, dans l'ordre du parcours métier. */
const FONCTIONNALITES = [
  { cle: 'pilotage', icone: '📊' },
  { cle: 'cheptel', icone: '🐝' },
  { cle: 'terrain', icone: '🗺️' },
  { cle: 'production', icone: '🍯' },
  { cle: 'capteurs', icone: '📡' },
  { cle: 'horsLigne', icone: '📶' },
] as const;

const PROFILS = [
  { cle: 'apiculteur', icone: '🧑‍🌾' },
  { cle: 'agent', icone: '🥾' },
  { cle: 'responsable', icone: '🔑' },
] as const;

const ETAPES = ['un', 'deux', 'trois'] as const;

const GARANTIES = ['cloisonnement', 'positions', 'session', 'langues'] as const;

/**
 * Page d'accueil publique — la vitrine du produit (SPRINT-19).
 *
 * <p><strong>Ce qu'elle corrige.</strong> L'application n'avait qu'une porte :
 * sans session, l'écran de connexion occupait tout l'espace. Un visiteur — un
 * apiculteur qui découvre Zümm, un correcteur, un agent qui n'a pas encore son
 * code d'exploitation — ne pouvait donc rien savoir du produit avant d'avoir un
 * compte. Or on ne demande pas un compte pour un logiciel dont on ignore ce
 * qu'il fait.
 *
 * <p>Cette page est servie à <strong>tout le monde</strong>, visiteur compris,
 * et reste consultable une fois connecté : le bouton d'appel devient alors
 * « Ouvrir la console » au lieu de « Se connecter ». D'où la session en
 * paramètre plutôt qu'une lecture directe — la page reste ainsi rendable en
 * test sans monter toute l'application.
 *
 * <p><strong>Elle n'appelle aucune API.</strong> C'est délibéré : la seule page
 * atteignable sans jeton ne doit pas dépendre d'un endpoint protégé, sinon elle
 * s'affiche cassée au premier visiteur. Tout ce qu'elle montre vient des
 * traductions et de la table des routes.
 *
 * <p>Aucun chiffre n'y est écrit en dur non plus : le nombre d'écrans annoncé
 * est celui de {@link ONGLETS}. Une vitrine qui promet seize écrans alors que le
 * produit en sert dix-sept est une vitrine qui vieillit mal.
 */
export function AccueilVue({
  session,
  onNaviguer,
}: {
  session: Session | null;
  onNaviguer: (chemin: string) => void;
}): ReactElement {
  const t = useT();
  const { langue, definirLangue } = useLangue();
  const a = t.accueil;

  const versConnexion = () => onNaviguer(ROUTES_PUBLIQUES.connexion);
  const versConsole = () => onNaviguer('/');

  return (
    <div className="z-accueil">
      {/* Barre publique : la marque, les commandes qui n'exigent pas de compte
          (langue, thème) et l'entrée dans l'application. Volontairement plus
          légère que la barre de la console — il n'y a rien à naviguer ici. */}
      <header className="z-accueil__barre">
        <div className="z-marque">
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{t.marque}</span>
          <span className="z-marque__baseline">{t.baseline}</span>
        </div>
        <div className="z-accueil__barre-actions">
          <nav className="z-langues" aria-label={t.langue}>
            {LANGUES.map((code) => (
              <button
                key={code}
                type="button"
                className="z-langue"
                aria-current={code === langue}
                onClick={() => definirLangue(code)}
              >
                {code.toUpperCase()}
              </button>
            ))}
          </nav>
          <SelecteurTheme />
          {session ? (
            <Bouton variante="primaire" onClick={versConsole}>
              {a.ouvrirConsole}
            </Bouton>
          ) : (
            <Bouton variante="primaire" onClick={versConnexion}>
              {a.seConnecter}
            </Bouton>
          )}
        </div>
      </header>

      <main>
        <section className="z-accueil__hero">
          <div className="z-accueil__hero-texte">
            <h1 className="z-accueil__accroche">{a.accroche}</h1>
            <p className="z-accueil__intro">{a.intro}</p>
            <div className="z-accueil__cta">
              {session ? (
                <Bouton variante="primaire" onClick={versConsole}>
                  {a.ouvrirConsole}
                </Bouton>
              ) : (
                <>
                  <Bouton variante="primaire" onClick={versConnexion}>
                    {a.creerCompte}
                  </Bouton>
                  <Bouton variante="secondaire" onClick={versConnexion}>
                    {a.seConnecter}
                  </Bouton>
                </>
              )}
            </div>
          </div>

          {/* Aperçu illustratif. Il nomme ce que l'application suit, sans
              afficher la moindre valeur : une vitrine qui montre des chiffres
              inventés ment sur le produit, et un visiteur non connecté n'a de
              toute façon aucune donnée à voir. */}
          <aside className="z-accueil__apercu" aria-label={a.apercu.titre}>
            <div className="z-accueil__halo" aria-hidden="true" />
            <p className="z-accueil__apercu-titre">{a.apercu.titre}</p>
            <ul className="z-accueil__apercu-liste">
              <li>
                <span aria-hidden="true">🐝</span> {a.apercu.ruches}
              </li>
              <li>
                <span aria-hidden="true">📡</span> {a.apercu.capteurs}
              </li>
              <li>
                <span aria-hidden="true">🍯</span> {a.apercu.recoltes}
              </li>
            </ul>
          </aside>
        </section>

        <section className="z-accueil__section" aria-labelledby="accueil-fonctionnalites">
          <h2 className="z-accueil__titre" id="accueil-fonctionnalites">
            {a.fonctionnalites.titre}
          </h2>
          <p className="z-accueil__soustitre">{a.fonctionnalites.soustitre}</p>
          <ul className="z-accueil__grille">
            {FONCTIONNALITES.map(({ cle, icone }) => (
              <li key={cle} className="z-accueil__carte">
                {/* Décoratif : le titre traduit porte seul le sens, comme dans
                    le rail de la console. */}
                <span className="z-accueil__carte-icone" aria-hidden="true">
                  {icone}
                </span>
                <h3 className="z-accueil__carte-titre">{a.fonctionnalites[cle].titre}</h3>
                <p className="z-accueil__carte-texte">{a.fonctionnalites[cle].texte}</p>
              </li>
            ))}
          </ul>
        </section>

        <section className="z-accueil__section" aria-labelledby="accueil-profils">
          <h2 className="z-accueil__titre" id="accueil-profils">
            {a.profils.titre}
          </h2>
          <p className="z-accueil__soustitre">{a.profils.soustitre}</p>
          <ul className="z-accueil__grille z-accueil__grille--trois">
            {PROFILS.map(({ cle, icone }) => (
              <li key={cle} className="z-accueil__carte">
                <span className="z-accueil__carte-icone" aria-hidden="true">
                  {icone}
                </span>
                <h3 className="z-accueil__carte-titre">{a.profils[cle].titre}</h3>
                <p className="z-accueil__carte-texte">{a.profils[cle].texte}</p>
              </li>
            ))}
          </ul>
        </section>

        <section className="z-accueil__section z-accueil__section--teinte" aria-labelledby="accueil-etapes">
          <h2 className="z-accueil__titre" id="accueil-etapes">
            {a.etapes.titre}
          </h2>
          <p className="z-accueil__soustitre">{a.etapes.soustitre}</p>
          {/* Une liste ordonnée, pas trois cartes : l'ordre EST l'information.
              Le numéro affiché est celui du compteur CSS, il n'est donc pas
              répété en texte — un lecteur d'écran annonce déjà « 1 sur 3 ». */}
          <ol className="z-accueil__etapes">
            {ETAPES.map((cle) => (
              <li key={cle} className="z-accueil__etape">
                <h3 className="z-accueil__carte-titre">{a.etapes[cle].titre}</h3>
                <p className="z-accueil__carte-texte">{a.etapes[cle].texte}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="z-accueil__section" aria-labelledby="accueil-confiance">
          <h2 className="z-accueil__titre" id="accueil-confiance">
            {a.confiance.titre}
          </h2>
          <p className="z-accueil__soustitre">{a.confiance.soustitre}</p>
          <ul className="z-accueil__garanties">
            {GARANTIES.map((cle) => (
              <li key={cle} className="z-accueil__garantie">
                <h3 className="z-accueil__carte-titre">{a.confiance[cle].titre}</h3>
                <p className="z-accueil__carte-texte">{a.confiance[cle].texte}</p>
              </li>
            ))}
          </ul>
        </section>

        <section className="z-accueil__final" aria-labelledby="accueil-final">
          <h2 className="z-accueil__titre" id="accueil-final">
            {a.final.titre}
          </h2>
          <p className="z-accueil__soustitre">{a.final.texte}</p>
          <div className="z-accueil__cta">
            {session ? (
              <Bouton variante="primaire" onClick={versConsole}>
                {a.ouvrirConsole}
              </Bouton>
            ) : (
              <Bouton variante="primaire" onClick={versConnexion}>
                {a.creerCompte}
              </Bouton>
            )}
          </div>
        </section>
      </main>

      <footer className="z-accueil__pied">
        <span>{a.pied.note}</span>
        <span aria-hidden="true">·</span>
        <span>{gabarit(a.pied.ecrans, { n: String(ONGLETS.length) })}</span>
        <span aria-hidden="true">·</span>
        <span>{a.pied.langues}</span>
      </footer>
    </div>
  );
}
