import { Suspense, lazy, useEffect, useState, type ReactElement } from 'react';
import { synchroniser } from './api/client';
import { consommerRouteDeRetour } from './auth/oidc';
import {
  rafraichirSession,
  sessionCourante,
  surSession,
  type Session,
} from './auth/session';
import { gabarit } from './i18n/console';
import { LANGUES } from './i18n/messages';
import { useLangue, useT } from './i18n/langue';
import { surFile } from './offline/file';
import { useNavigation } from './routage/navigation';
import {
  GROUPES,
  GROUPES_CLES,
  ICONES,
  ROUTES_PUBLIQUES,
  cheminDepuisOnglet,
  ongletDepuisChemin,
  routePubliqueDepuisChemin,
  type Onglet,
} from './routage/routes';
import { appliquerMiseAJour, useMiseAJourPwa } from './pwa';
import { SelecteurTheme } from './theme/theme';
import { Bouton, Squelette } from './ui/composants';
import { PaletteCommandes } from './ui/palette';
import { MenuProfil } from './ui/profil';
import { AccueilVue } from './vues/AccueilVue';
import { ConnexionVue } from './vues/ConnexionVue';
import { IntrouvableVue } from './vues/IntrouvableVue';
import './App.css';

/**
 * Chargement paresseux par route (US-051) : sans lui, les quinze vues partent
 * dans le même paquet, alors qu'une session n'en visite que quelques-unes.
 */
const VUES: Record<Onglet, React.LazyExoticComponent<() => ReactElement>> = {
  fermiers: lazy(() => import('./vues/FermiersVue').then((m) => ({ default: m.FermiersVue }))),
  fermes: lazy(() => import('./vues/FermesVue').then((m) => ({ default: m.FermesVue }))),
  sites: lazy(() => import('./vues/SitesVue').then((m) => ({ default: m.SitesVue }))),
  ruches: lazy(() => import('./vues/RuchesVue').then((m) => ({ default: m.RuchesVue }))),
  plannings: lazy(() => import('./vues/PlanningsVue').then((m) => ({ default: m.PlanningsVue }))),
  visites: lazy(() => import('./vues/VisitesVue').then((m) => ({ default: m.VisitesVue }))),
  taches: lazy(() => import('./vues/TachesVue').then((m) => ({ default: m.TachesVue }))),
  tableaux: lazy(() => import('./vues/TableauxVue').then((m) => ({ default: m.TableauxVue }))),
  capteurs: lazy(() => import('./vues/CapteursVue').then((m) => ({ default: m.CapteursVue }))),
  reines: lazy(() => import('./vues/ReinesVue').then((m) => ({ default: m.ReinesVue }))),
  recoltes: lazy(() => import('./vues/RecoltesVue').then((m) => ({ default: m.RecoltesVue }))),
  lots: lazy(() => import('./vues/LotsVue').then((m) => ({ default: m.LotsVue }))),
  carte: lazy(() => import('./vues/CarteVue').then((m) => ({ default: m.CarteVue }))),
  agents: lazy(() => import('./vues/AgentsVue').then((m) => ({ default: m.AgentsVue }))),
  invitations: lazy(() =>
    import('./vues/InvitationsVue').then((m) => ({ default: m.InvitationsVue })),
  ),
  config: lazy(() => import('./vues/ConfigVue').then((m) => ({ default: m.ConfigVue }))),
  audit: lazy(() => import('./vues/AuditVue').then((m) => ({ default: m.AuditVue }))),
};

/**
 * Console de gestion Zümm. Ossature : barre de navigation entre les ressources
 * metier, selecteur de langue (FR/EN/AR, RTL en arabe) et zone de contenu. Chaque
 * ecran a son adresse (US-051). Sans session, l'ecran de connexion prend toute la
 * place — l'URL, elle, est conservee, pour y revenir apres reconnexion.
 *
 * <p>Deux ecrans vivent HORS de cette coquille et sans session : la page
 * d'accueil publique (`/accueil`, et la racine tant qu'on n'est pas connecte) et
 * l'ecran d'entree (`/connexion`). L'accueil est importe normalement et non
 * paresseusement, contrairement aux vues metier : c'est la premiere page servie
 * a un visiteur, la mettre derriere un second aller-retour reseau ferait
 * commencer la decouverte du produit par un squelette de chargement.
 */
export default function App(): ReactElement {
  const t = useT();
  const { langue, definirLangue } = useLangue();
  const majDisponible = useMiseAJourPwa();
  const { chemin, naviguer } = useNavigation();
  // `undefined` tant que le serveur n'a pas repondu : l'ecran de connexion ne
  // doit pas clignoter le temps d'un aller-retour.
  const [session, setSession] = useState<Session | null | undefined>(sessionCourante());
  const [enAttente, setEnAttente] = useState(0);
  const [horsLigne, setHorsLigne] = useState(!navigator.onLine);
  const [palette, setPalette] = useState(false);

  const onglet = ongletDepuisChemin(chemin);
  const routePublique = routePubliqueDepuisChemin(chemin);

  useEffect(() => surSession(setSession), []);

  // Un utilisateur déjà connecté n'a rien à faire sur l'écran d'entrée : s'il y
  // arrive par un favori ou par le bouton retour, on le ramène à la console.
  // L'accueil, lui, reste accessible connecté — c'est une vitrine, pas un sas.
  useEffect(() => {
    if (session && routePublique === 'connexion') {
      naviguer('/');
    }
  }, [session, routePublique, naviguer]);

  // Ctrl/⌘ + K ouvre la palette depuis n'importe quel écran. Le raccourci est
  // capté sur le document et non sur un champ : il doit répondre où que soit le
  // focus. `preventDefault` retire la recherche intégrée du navigateur, qui
  // porte la même combinaison sur certaines plateformes.
  useEffect(() => {
    const surTouche = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() === 'k' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        setPalette((ouverte) => !ouverte);
      }
    };
    document.addEventListener('keydown', surTouche);
    return () => document.removeEventListener('keydown', surTouche);
  }, []);

  // État de la session, demandé au serveur (ADR-006). Le navigateur ne détient
  // plus de jeton : il ne peut plus savoir de lui-même s'il est connecté, ni
  // jusqu'à quand. Le renouvellement se fait côté serveur — il n'y a donc plus
  // rien à planifier ici, et c'est tout le bénéfice du BFF.
  useEffect(() => {
    void rafraichirSession().then((etat) => {
      // Retour de connexion : le serveur ramène à la racine, la PWA restaure
      // l'écran quitté (US-051).
      const retour = etat ? consommerRouteDeRetour() : null;
      if (retour) {
        naviguer(retour);
      }
    });
  }, [naviguer]);

  // Synchronisation différée (US-011) : file d'attente + retour du réseau.
  useEffect(() => {
    const desabonner = surFile(setEnAttente);
    const enLigne = () => {
      setHorsLigne(false);
      void synchroniser();
    };
    const deconnecte = () => setHorsLigne(true);
    window.addEventListener('online', enLigne);
    window.addEventListener('offline', deconnecte);
    if (navigator.onLine) {
      void synchroniser();
    }
    return () => {
      desabonner();
      window.removeEventListener('online', enLigne);
      window.removeEventListener('offline', deconnecte);
    };
  }, []);

  // Trois etats, et non deux : « pas encore su » n'est pas « pas connecte ».
  // Les confondre ferait clignoter l'ecran de connexion a chaque chargement,
  // devant un utilisateur pourtant deja authentifie.
  if (session === undefined) {
    return (
      <main className="z-entree__panneau">
        <p className="z-info">{t.etats.chargement}</p>
      </main>
    );
  }
  // La vitrine passe AVANT le contrôle de session : c'est la seule page que
  // l'application sert à un visiteur sans compte, et elle reste consultable une
  // fois connecté. Sans session, elle tient aussi lieu de racine — arriver sur
  // un formulaire de connexion quand on ne connaît pas encore le produit ne dit
  // rien de ce qu'on est venu voir.
  if (routePublique === 'accueil' || (session === null && chemin === '/')) {
    return <AccueilVue session={session} onNaviguer={naviguer} />;
  }
  if (session === null) {
    // Toute autre adresse demande un compte. Le chemin n'est pas effacé : après
    // authentification, l'écran demandé s'ouvre de lui-même (US-051).
    return <ConnexionVue onAccueil={() => naviguer(ROUTES_PUBLIQUES.accueil)} />;
  }

  const Vue = onglet === null ? null : VUES[onglet];

  return (
    <div className="z-app">
      {majDisponible && (
        // Bandeau, pas rechargement automatique : sur un rucher, recharger sans
        // prevenir ferait perdre un rapport de visite en cours de saisie.
        <div className="z-bandeau-maj" role="status">
          <span>{t.pwa.majDisponible}</span>
          <Bouton variante="primaire" onClick={() => void appliquerMiseAJour()}>
            {t.pwa.actualiser}
          </Bouton>
        </div>
      )}
      <header className="z-topbar">
        <div className="z-marque">
          <span className="z-marque__pastille" aria-hidden="true" />
          <span className="z-marque__nom">{t.marque}</span>
          <span className="z-marque__baseline">{t.baseline}</span>
        </div>
        <div className="z-topbar__actions">
          {/* La palette est un accélérateur, pas la voie d'accès : elle a donc un
              bouton visible, qui affiche aussi le raccourci. Un ⌘K que personne
              ne découvre ne fait naviguer personne. */}
          <button
            type="button"
            className="z-recherche"
            onClick={() => setPalette(true)}
            aria-label={t.palette.ouvrir}
          >
            <span aria-hidden="true">🔍</span>
            <span className="z-recherche__texte" aria-hidden="true">
              {t.palette.invite}
            </span>
            <kbd className="z-recherche__touche" aria-hidden="true">
              {t.palette.raccourci}
            </kbd>
          </button>
          {(horsLigne || enAttente > 0) && (
            <span className={`z-sync ${horsLigne ? 'z-sync--hors-ligne' : ''}`} role="status">
              {horsLigne ? t.sync.horsLigne : gabarit(t.sync.enAttente, { n: String(enAttente) })}
            </span>
          )}
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
          <MenuProfil session={session} />
        </div>
      </header>

      {/* Un SEUL élément de navigation dans le DOM, présenté différemment selon
          la largeur : rail latéral persistant au bureau, barre du bas au pouce
          sur mobile. Dupliquer la navigation aurait dupliqué seize noms
          accessibles, et un lecteur d'écran aurait annoncé deux fois chaque
          écran. Tout se joue donc en CSS. */}
      <div className="z-corps">
        <nav className="z-rail" aria-label={t.groupes.navigation}>
          {GROUPES_CLES.map((groupe) => (
            <div key={groupe} className="z-rail__groupe">
              {/* La famille situe, elle ne se clique pas. Masquée sur mobile,
                  où la barre du bas n'a pas la hauteur d'un intertitre. */}
              <span className="z-rail__famille">{t.groupes[groupe]}</span>
              {GROUPES[groupe].map((cle) => (
                <button
                  key={cle}
                  type="button"
                  className="z-onglet"
                  aria-current={cle === onglet}
                  onClick={() => naviguer(cheminDepuisOnglet(cle))}
                >
                  {/* Décoratif : le libellé traduit porte seul le sens. Sans
                      `aria-hidden`, le lecteur annoncerait « abeille Ruches ». */}
                  <span className="z-onglet__icone" aria-hidden="true">
                    {ICONES[cle]}
                  </span>
                  <span className="z-onglet__libelle">{t.onglets[cle]}</span>
                </button>
              ))}
            </div>
          ))}
        </nav>

        <main className="z-vue">
          <Suspense
            fallback={
              // Un squelette plutôt qu'un « Chargement… » : il dit ce que la
              // phrase ne dit pas — que la réponse aura la forme d'une liste, et
              // à peu près sa longueur. L'écran ne se réorganise donc pas à
              // l'arrivée du module. Le texte reste, pour les lecteurs d'écran :
              // le squelette est décoratif et n'annonce rien.
              <>
                <p className="z-visuellement-cache" role="status">
                  {t.etats.chargement}
                </p>
                <Squelette lignes={6} />
              </>
            }
          >
            {Vue === null ? (
              <IntrouvableVue onRetour={() => naviguer('/')} />
            ) : (
              <Vue />
            )}
          </Suspense>
        </main>
      </div>

      {palette && (
        <PaletteCommandes
          onFermer={() => setPalette(false)}
          onChoisir={(cle) => {
            setPalette(false);
            naviguer(cheminDepuisOnglet(cle));
          }}
        />
      )}
    </div>
  );
}
