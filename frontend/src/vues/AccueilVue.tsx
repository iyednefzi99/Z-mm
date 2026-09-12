import type { ReactElement } from 'react';
import type { Session } from '../auth/session';
import { useT } from '../i18n/langue';
import { ROUTES_PUBLIQUES } from '../routage/routes';
import { Bouton } from '../ui/composants';
import { Icone, type NomIcone } from '../ui/icones';
import { BordDechire } from '../ui/papier';
import { CoquillePublique } from './CoquillePublique';

/** Les six domaines mis en avant, dans l'ordre du parcours métier. */
const FONCTIONNALITES = [
  { cle: 'pilotage', icone: 'graphique' },
  { cle: 'cheptel', icone: 'ruche' },
  { cle: 'terrain', icone: 'carte' },
  { cle: 'production', icone: 'miel' },
  { cle: 'capteurs', icone: 'capteur' },
  { cle: 'horsLigne', icone: 'horsLigne' },
] as const satisfies readonly { cle: string; icone: NomIcone }[];

const PROFILS = [
  { cle: 'apiculteur', icone: 'apiculteur' },
  { cle: 'agent', icone: 'agent' },
  { cle: 'responsable', icone: 'cle' },
] as const satisfies readonly { cle: string; icone: NomIcone }[];

/** Ce que la carte d'aperçu nomme, avec le pictogramme qui va avec. */
const APERCU = [
  { cle: 'ruches', icone: 'ruche' },
  { cle: 'capteurs', icone: 'capteur' },
  { cle: 'recoltes', icone: 'miel' },
] as const satisfies readonly { cle: string; icone: NomIcone }[];

const ETAPES = ['un', 'deux', 'trois'] as const;

const GARANTIES = ['cloisonnement', 'positions', 'session', 'langues'] as const;

/**
 * Silhouette de ruche décorative pour la ligne d'horizon du héros — un dôme
 * de skep, pas une photo : aucune n'a été fournie, et en générer une aurait
 * laissé la trace que ce dépôt interdit.
 *
 * <p>Reprend la forme du pictogramme `ruche` de `ui/icones.tsx` (un arc en
 * dôme, deux liserés horizontaux), rempli plutôt que tracé au trait : un
 * empilement d'ellipses tenté d'abord se lisait comme un sapin, pas une
 * ruche — le dôme, lui, ne s'y confond pas.
 */
function RucheSilhouette({ taille }: { taille: number }): ReactElement {
  return (
    <svg width={taille} height={taille * (100 / 120)} viewBox="0 0 120 100">
      <path d="M10 92 A50 50 0 0 1 110 92 Z" />
      <path d="M14.2 72h91.6" stroke="rgba(255, 255, 255, 0.16)" strokeWidth="2" />
      <path d="M30 52h60" stroke="rgba(255, 255, 255, 0.16)" strokeWidth="2" />
    </svg>
  );
}

/** Tailles de la ligne de ruches, à l'échelle : ni un alignement au cordeau, ni un hasard à chaque rendu. */
const TAILLES_RUCHERS = [72, 104, 80, 112, 76] as const;

/** Vol d'abeilles décoratif au-dessus de la carte d'aperçu — positions fixes, pour un rendu identique côté serveur et client. */
const VOL_ABEILLES = [
  { gauche: '4%', haut: '18%', taille: 6, opacite: 0.55 },
  { gauche: '28%', haut: '4%', taille: 8, opacite: 0.75 },
  { gauche: '52%', haut: '30%', taille: 5, opacite: 0.45 },
  { gauche: '70%', haut: '10%', taille: 7, opacite: 0.65 },
  { gauche: '90%', haut: '22%', taille: 6, opacite: 0.55 },
] as const;

/**
 * Les signaux de confiance repris DANS le héros, au-dessus de la ligne de
 * flottaison.
 *
 * <p>Trois et non quatre : « Trois langues » est déjà dit par l'intro, deux
 * lignes plus haut. Ne restent que les trois garanties de sécurité — celles
 * qu'aucun concurrent n'affiche, et qu'un visiteur ne devinerait pas.
 *
 * <p>Les libellés ne sont pas réécrits ici : ce sont ceux de la section « Ce qui
 * est protégé ». Les recopier les laisserait diverger dès la première retouche —
 * le travers que ce dépôt combat partout ailleurs.
 */
const PREUVES = ['cloisonnement', 'positions', 'session'] as const;

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
 * est celui de {@link ONGLETS}. Une vitrine qui promet un total que le produit a
 * dépassé depuis est une vitrine qui vieillit mal — et c'est arrivé à la prose de
 * ce fichier, qui annonçait encore seize écrans quand il y en avait dix-huit.
 *
 * <p><strong>Trois bandes, deux déchirures.</strong> Le papier arraché ne sépare
 * que des fonds qui changent réellement : ce que fait le produit, puis comment
 * on y entre. Ce n'est pas une frise posée entre chaque titre — une texture qui
 * revient à chaque section cesse d'être une matière et devient un tic. La
 * console, elle, n'en porte aucune : un tableau de saisie ne se lit pas sur du
 * papier froissé (voir {@link BordDechire}).
 */
export function AccueilVue({
  session,
  onNaviguer,
}: {
  session: Session | null;
  onNaviguer: (chemin: string) => void;
}): ReactElement {
  const t = useT();
  const a = t.accueil;

  const versConnexion = () => onNaviguer(ROUTES_PUBLIQUES.connexion);
  const versConsole = () => onNaviguer('/');
  const versFonctionnalites = () => onNaviguer(ROUTES_PUBLIQUES.fonctionnalites);

  return (
    <CoquillePublique session={session} onNaviguer={onNaviguer}>
      <section className="z-accueil__hero">
        {/* Scène plein cadre — aube sur le rucher — construite en CSS et SVG,
            sans photographie : aucune n'a été fournie. Purement décorative :
            le titre et l'intro, plus bas, portent seuls le sens. */}
        <div className="z-accueil__hero-sol" aria-hidden="true" />
        <div className="z-accueil__hero-ruchers" aria-hidden="true">
          {TAILLES_RUCHERS.map((taille, i) => (
            <RucheSilhouette key={i} taille={taille} />
          ))}
        </div>

        <div className="z-accueil__hero-cadre">
          <div className="z-accueil__hero-voile" aria-hidden="true" />
          <div className="z-accueil__hero-lueur" aria-hidden="true" />
          <div className="z-accueil__hero-vol" aria-hidden="true">
            {VOL_ABEILLES.map((p) => (
              <span
                key={`${p.gauche}-${p.haut}`}
                style={{ left: p.gauche, top: p.haut, width: p.taille, height: p.taille, opacity: p.opacite }}
              />
            ))}
          </div>

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
                  {/* Deuxième appel : « en savoir plus » avant « entrer ». Un
                      visiteur qui découvre le produit n'est pas prêt à se
                      connecter, et lui proposer deux fois la même porte ne
                      l'avance pas. */}
                  <Bouton variante="secondaire" onClick={versFonctionnalites}>
                    {a.decouvrir}
                  </Bouton>
                </>
              )}
            </div>

            <ul className="z-accueil__preuves">
              {PREUVES.map((cle) => (
                <li key={cle}>{a.confiance[cle].titre}</li>
              ))}
            </ul>
          </div>

          {/* Aperçu illustratif. Il nomme ce que l'application suit, sans
                afficher la moindre valeur : une vitrine qui montre des chiffres
                inventés ment sur le produit, et un visiteur non connecté n'a de
                toute façon aucune donnée à voir. Carte en verre : le fond du
                héros porte déjà le dégradé signature, et un second aplat de
                couleur ferait deux points d'attraction là où il en faut un. */}
          <aside className="z-accueil__apercu" aria-label={a.apercu.titre}>
            <div className="z-accueil__halo" aria-hidden="true" />
            <p className="z-accueil__apercu-titre">{a.apercu.titre}</p>
            <ul className="z-accueil__apercu-liste">
              {APERCU.map(({ cle, icone }) => (
                <li key={cle}>
                  <Icone nom={icone} /> {a.apercu[cle]}
                </li>
              ))}
            </ul>
          </aside>
        </div>
      </section>

      {/* Première bande : ce que fait le produit, et pour qui. */}
      <div className="z-bande z-bande--surface">
        <BordDechire teinte="fond" />

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
                <span className="z-alveole" aria-hidden="true">
                  <Icone nom={icone} />
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
                <span className="z-alveole" aria-hidden="true">
                  <Icone nom={icone} />
                </span>
                <h3 className="z-accueil__carte-titre">{a.profils[cle].titre}</h3>
                <p className="z-accueil__carte-texte">{a.profils[cle].texte}</p>
              </li>
            ))}
          </ul>
        </section>
      </div>

      {/* Seconde bande : pourquoi vous faire confiance, puis comment on entre.
            La preuve technique (Confiance) précède la preuve sociale
            (Témoignages) : la première est vérifiable dès aujourd'hui, la
            seconde ne l'est pas encore, et l'ordre le dit sans un mot. Les
            étapes et l'appel final viennent après — une fois la confiance
            posée, reste à montrer que commencer est simple. */}
      <div className="z-bande z-bande--fond">
        <BordDechire teinte="surface" />

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

        {/* Preuve sociale. Aucune exploitation cliente n'a encore été
              interrogée : afficher une citation attribuée à un nom inventé
              mentirait exactement comme le ferait un chiffre inventé dans
              l'aperçu du héros. La section existe donc déjà, avec le rôle qui
              parlera (les mêmes trois profils que plus haut), mais chaque
              carte porte l'étiquette « à venir » au lieu d'une citation. */}
        <section className="z-accueil__section" aria-labelledby="accueil-temoignages">
          <h2 className="z-accueil__titre" id="accueil-temoignages">
            {a.temoignages.titre}
          </h2>
          <p className="z-accueil__soustitre">{a.temoignages.soustitre}</p>
          {/* Pas de <h3> ici : « Agent de terrain » etc. sont déjà des titres
                de section dans « Pour qui », un cran plus haut. Les répéter
                comme titre ici doublerait l'esquisse de la page pour qui
                navigue au clavier par en-têtes, sans rien ajouter — le rôle
                reste lisible en légende de carte. */}
          <ul className="z-accueil__grille z-accueil__grille--trois">
            {PROFILS.map(({ cle, icone }) => (
              <li key={cle} className="z-accueil__carte">
                <span className="z-alveole" aria-hidden="true">
                  <Icone nom={icone} />
                </span>
                <p className="z-accueil__carte-titre">{a.profils[cle].titre}</p>
                <p className="z-accueil__carte-texte">
                  <strong>{a.temoignages.badge}</strong> — {a.temoignages[cle].texte}
                </p>
              </li>
            ))}
          </ul>
        </section>

        <section className="z-accueil__section" aria-labelledby="accueil-etapes">
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
      </div>
    </CoquillePublique>
  );
}
