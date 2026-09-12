import { useEffect, useState, type ReactElement } from 'react';
import { agents, fermes, ruches, sites } from '../api/client';
import type {
  Agent,
  CauseCloture,
  CouleurRuche,
  EtatRuche,
  Ferme,
  OrigineRuche,
  PrioriteTerrain,
  Ruche,
  RucheCorps,
  Site,
  TypeRuche,
} from '../api/types';
import {
  CAUSES_CLOTURE,
  COULEURS_RUCHE,
  ETATS_RUCHE,
  ORIGINES_RUCHE,
  PRIORITES_TERRAIN,
  TYPES_RUCHE,
} from '../api/types';
import { chargeQrRuche, codeCourt, PlancheEtiquettes, QrImage } from '../ui/etiquettes';
import { ecrireEtiquette, nfcDisponible } from '../terrain/nfc';
import { useT } from '../i18n/langue';
import { useRessource, useRoles } from '../hooks';
import { peutEcrire } from '../routage/routes';
import { Bouton, ChampNombre, ChampSelect, ChampTexte, Colonne, Modale, Option, Table } from '../ui/composants';
import { CorpsSection } from './CorpsSection';
import { PanneauPhotos } from '../photos/PanneauPhotos';

const MAX_HAUSSES = 5;

export function RuchesVue(): ReactElement {
  const t = useT();
  const etat = useRessource<Ruche, RucheCorps>(ruches);
  const ecriture = peutEcrire('ruches', useRoles());
  const [optSites, setOptSites] = useState<Option[]>([]);
  const [optFermes, setOptFermes] = useState<Option[]>([]);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [edition, setEdition] = useState<Ruche | null>(null);
  const [modele, setModele] = useState('');
  const [siteId, setSiteId] = useState('');
  const [fermeId, setFermeId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [etatRuche, setEtatRuche] = useState<EtatRuche>('creee');
  const [typeRuche, setTypeRuche] = useState('');
  const [couleur, setCouleur] = useState('');
  const [origine, setOrigine] = useState('');
  const [causeCloture, setCauseCloture] = useState('');
  // SPRINT-23 : une ruche souche se traite avant les autres, et la tournee
  // comme les agregats la remontent.
  const [priorite, setPriorite] = useState<PrioriteTerrain>('normale');
  const [corpsCadres, setCorpsCadres] = useState('10');
  const [hausses, setHausses] = useState<string[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);
  // Etiquetage durable (SPRINT-25, lot J) : la ruche dont on affiche le QR, et
  // le rucher dont on imprime la planche.
  const [etiquetee, setEtiquetee] = useState<Ruche | null>(null);
  const [messageNfc, setMessageNfc] = useState<string | null>(null);
  const [plancheSiteId, setPlancheSiteId] = useState('');

  const optionsEtat: Option[] = ETATS_RUCHE.map((e) => ({ valeur: e, libelle: t.etatsRuche[e] }));
  const aucun: Option = { valeur: '', libelle: t.champs.aucun };
  const r = t.referentielRuche;
  const optionsType: Option[] = [aucun, ...TYPES_RUCHE.map((v) => ({ valeur: v, libelle: r.types[v] }))];
  const optionsCouleur: Option[] = [aucun, ...COULEURS_RUCHE.map((v) => ({ valeur: v, libelle: r.couleurs[v] }))];
  const optionsOrigine: Option[] = [aucun, ...ORIGINES_RUCHE.map((v) => ({ valeur: v, libelle: r.origines[v] }))];
  const optionsCause: Option[] = [aucun, ...CAUSES_CLOTURE.map((v) => ({ valeur: v, libelle: r.causesCloture[v] }))];

  const colonnes: Colonne<Ruche>[] = [
    { entete: t.champs.modele, rendu: (x) => x.modele },
    { entete: t.champs.site, rendu: (x) => x.siteNom },
    { entete: t.champs.typeRuche, rendu: (x) => (x.typeRuche ? r.types[x.typeRuche] : '—') },
    { entete: t.champs.etat, rendu: (x) => t.etatsRuche[x.etat] },
    { entete: t.champs.hausses, rendu: (x) => String(x.nbHausses) },
    {
      // Le code court se lit dans la LISTE, pas seulement sur l'etiquette :
      // c'est ce qu'un apiculteur a sous les yeux quand il tient une ruche dont
      // le QR ne se scanne plus et cherche laquelle c'est.
      entete: t.etiquettes.code,
      rendu: (x) => (
        <button type="button" className="z-lien" onClick={() => ouvrirEtiquette(x)}>
          {codeCourt(x.id)}
        </button>
      ),
    },
  ];

  const ouvrirEtiquette = (ruche: Ruche) => {
    setMessageNfc(null);
    setEtiquetee(ruche);
  };

  /**
   * Ecrit l'etiquette NFC de la ruche affichee.
   *
   * <p>La promesse ne se resout qu'au contact du tag : l'ecran doit donc dire
   * « approchez l'etiquette » plutot que de rester muet. Le contenu ecrit est
   * exactement celui du QR — deux charges utiles pour le meme objet donneraient
   * un jour deux reponses.
   */
  const ecrireNfc = async (ruche: Ruche) => {
    setMessageNfc(t.etiquettes.nfcApprochez);
    try {
      await ecrireEtiquette(chargeQrRuche(ruche.id));
      setMessageNfc(t.etiquettes.nfcEcrite);
    } catch {
      setMessageNfc(t.etiquettes.nfcEchec);
    }
  };

  useEffect(() => {
    void sites.lister().then((l: Site[]) => setOptSites(l.map((s) => ({ valeur: String(s.id), libelle: s.nom })))).catch(() => setOptSites([]));
    void fermes.lister().then((l: Ferme[]) => setOptFermes(l.map((f) => ({ valeur: String(f.id), libelle: f.nom })))).catch(() => setOptFermes([]));
    void agents
      .lister()
      .then((l: Agent[]) =>
        setOptAgents([{ valeur: '', libelle: t.champs.aucun }, ...l.map((a) => ({ valeur: String(a.id), libelle: a.nom }))]),
      )
      .catch(() => setOptAgents([{ valeur: '', libelle: t.champs.aucun }]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [etat.elements]);

  const ouvrir = (ruche: Ruche | null) => {
    setEdition(ruche);
    setModele(ruche?.modele ?? '');
    setSiteId(ruche ? String(ruche.siteId) : '');
    setFermeId(ruche ? String(ruche.fermeId) : '');
    setAgentId(ruche?.agentResponsableId != null ? String(ruche.agentResponsableId) : '');
    setEtatRuche(ruche?.etat ?? 'creee');
    setTypeRuche(ruche?.typeRuche ?? '');
    setCouleur(ruche?.couleur ?? '');
    setOrigine(ruche?.origine ?? '');
    setCauseCloture(ruche?.causeCloture ?? '');
    setPriorite(ruche?.priorite ?? 'normale');
    const corps = ruche?.compartiments.find((c) => c.type === 'corps');
    setCorpsCadres(corps ? String(corps.nbCadres) : '10');
    setHausses(
      ruche ? ruche.compartiments.filter((c) => c.type === 'hausse').map((c) => String(c.nbCadres)) : [],
    );
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (siteId === '' || fermeId === '') {
      setErreur(`${t.champs.site} / ${t.champs.ferme} ?`);
      return;
    }
    const corps: RucheCorps = {
      modele,
      siteId: Number(siteId),
      fermeId: Number(fermeId),
      agentResponsableId: agentId === '' ? null : Number(agentId),
      etat: etatRuche,
      compartiments: [
        { type: 'corps', nbCadres: Number(corpsCadres) },
        ...hausses.map((h) => ({ type: 'hausse' as const, nbCadres: Number(h) })),
      ],
      typeRuche: typeRuche === '' ? null : (typeRuche as TypeRuche),
      couleur: couleur === '' ? null : (couleur as CouleurRuche),
      origine: origine === '' ? null : (origine as OrigineRuche),
      // La base refuse une cause de cloture sur une ruche encore active : ne
      // l'envoyer que dans l'etat qui la justifie evite un 400 sur une saisie
      // que l'utilisateur croirait bonne — le champ est deja masque au-dessus.
      causeCloture:
        etatRuche === 'cloturee' && causeCloture !== '' ? (causeCloture as CauseCloture) : null,
      priorite,
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const majHausse = (index: number, valeur: string) =>
    setHausses((liste) => liste.map((h, i) => (i === index ? valeur : h)));

  return (
    <CorpsSection
      titre={t.onglets.ruches}
      sousTitre={t.soustitres.ruches}
      etat={etat}
      onNouveau={() => ouvrir(null)}
      ecriture={ecriture}
      actions={
        <ChampSelect
          libelle={t.etiquettes.planche}
          valeur={plancheSiteId}
          options={[{ valeur: '', libelle: t.etiquettes.choisirRucher }, ...optSites]}
          onChange={setPlancheSiteId}
        />
      }
    >
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={(e) => void etat.supprimer(e.id)} ecriture={ecriture} />
      )}
      {etiquetee && (
        <Modale
          titre={`${t.etiquettes.titre} — ${codeCourt(etiquetee.id)}`}
          onFermer={() => setEtiquetee(null)}
        >
          <div className="z-form">
            <QrImage payload={chargeQrRuche(etiquetee.id)} />
            <p className="z-info" style={{ textAlign: 'center' }}>
              <strong>{codeCourt(etiquetee.id)}</strong> · {etiquetee.modele} ·{' '}
              {etiquetee.siteNom}
            </p>
            {/* Le code court EST l'identifiant de la ruche, prefixe. En inventer
                un second, opaque et joli, aurait cree deux facons de nommer la
                meme colonie — et un jour deux reponses. */}
            <p className="z-info">{t.etiquettes.aide}</p>
            {messageNfc && (
              <p className="z-info" role="status">
                {messageNfc}
              </p>
            )}
            <div className="z-form__actions">
              {/* Le NFC n'apparait QUE la ou il existe : `NDEFReader` est absent
                  d'iOS et de Firefox. Un bouton qui echoue une fois sur deux
                  apprend a ne plus l'essayer, y compris la ou il marche. */}
              {nfcDisponible() && (
                <Bouton variante="secondaire" onClick={() => void ecrireNfc(etiquetee)}>
                  {t.etiquettes.ecrireNfc}
                </Bouton>
              )}
              <Bouton variante="fantome" onClick={() => setEtiquetee(null)}>
                {t.actions.fermer}
              </Bouton>
            </div>
            {!nfcDisponible() && <p className="z-info">{t.etiquettes.nfcIndisponible}</p>}
          </div>
        </Modale>
      )}

      {plancheSiteId !== '' && (
        <Modale titre={t.etiquettes.planche} onFermer={() => setPlancheSiteId('')}>
          <div className="z-form">
            <p className="z-info z-sans-impression">{t.etiquettes.plancheAide}</p>
            <PlancheEtiquettes
              rucherNom={
                optSites.find((o) => o.valeur === plancheSiteId)?.libelle ?? ''
              }
              ruches={etat.elements.filter((x) => String(x.siteId) === plancheSiteId)}
            />
            <div className="z-form__actions z-sans-impression">
              <Bouton variante="fantome" onClick={() => setPlancheSiteId('')}>
                {t.actions.fermer}
              </Bouton>
              <Bouton variante="primaire" onClick={() => window.print()}>
                {t.etiquettes.imprimer}
              </Bouton>
            </div>
          </div>
        </Modale>
      )}

      {ouvert && (
        <Modale titre={t.onglets.ruches} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={t.champs.modele} valeur={modele} onChange={setModele} requis />
            <div className="z-form__grille">
              <ChampSelect libelle={t.champs.site} valeur={siteId} options={optSites} onChange={setSiteId} requis />
              <ChampSelect libelle={t.champs.ferme} valeur={fermeId} options={optFermes} onChange={setFermeId} requis />
            </div>
            <div className="z-form__grille">
              <ChampSelect
                libelle={t.champs.agentResponsable}
                valeur={agentId}
                options={optAgents}
                onChange={setAgentId}
              />
              <ChampSelect
                libelle={t.champs.etat}
                valeur={etatRuche}
                options={optionsEtat}
                onChange={(v) => setEtatRuche(v as EtatRuche)}
              />
            </div>

            <div className="z-form__grille">
              <ChampSelect
                libelle={t.champs.typeRuche}
                valeur={typeRuche}
                options={optionsType}
                onChange={setTypeRuche}
              />
              <ChampSelect
                libelle={t.champs.couleur}
                valeur={couleur}
                options={optionsCouleur}
                onChange={setCouleur}
              />
              <ChampSelect
                libelle={t.champs.origine}
                valeur={origine}
                options={optionsOrigine}
                onChange={setOrigine}
              />
            </div>
            <ChampSelect
              libelle={t.champs.prioriteTerrain}
              valeur={priorite}
              options={PRIORITES_TERRAIN.map((niveau) => ({
                valeur: niveau,
                libelle: t.prioriteTerrain[niveau],
              }))}
              onChange={(valeur) => setPriorite(valeur as PrioriteTerrain)}
            />
            {/* La cause de cloture n'apparait que sur une ruche cloturee : la
                demander avant serait demander pourquoi on ferme une porte
                ouverte, et la base le refuse de toute facon. */}
            {etatRuche === 'cloturee' && (
              <ChampSelect
                libelle={t.champs.causeCloture}
                valeur={causeCloture}
                options={optionsCause}
                onChange={setCauseCloture}
              />
            )}

            <fieldset className="z-composition">
              <legend className="z-champ__libelle">
                {t.champs.corps} / {t.champs.hausses}
              </legend>
              <ChampNombre
                libelle={`${t.champs.corps} — ${t.champs.cadres}`}
                valeur={corpsCadres}
                onChange={setCorpsCadres}
                pas="1"
                requis
              />
              {hausses.map((h, i) => (
                <div key={i} className="z-composition__hausse">
                  <ChampNombre
                    libelle={`${t.champs.hausses} ${i + 1} — ${t.champs.cadres}`}
                    valeur={h}
                    onChange={(v) => majHausse(i, v)}
                    pas="1"
                    requis
                  />
                  <Bouton variante="fantome" onClick={() => setHausses((l) => l.filter((_, j) => j !== i))}>
                    ✕
                  </Bouton>
                </div>
              ))}
              <Bouton
                variante="secondaire"
                onClick={() => setHausses((l) => (l.length < MAX_HAUSSES ? [...l, '9'] : l))}
                disabled={hausses.length >= MAX_HAUSSES}
              >
                + {t.actions.ajouterHausse}
              </Bouton>
            </fieldset>

            {/* Pas de cible avant le premier enregistrement : une ruche en
                cours de creation n'a pas encore d'id a porter. */}
            {edition && <PanneauPhotos cible="RUCHE" cibleId={edition.id} ecriture={ecriture} />}

            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>
                {t.actions.annuler}
              </Bouton>
              <Bouton variante="primaire" type="submit">
                {t.actions.enregistrer}
              </Bouton>
            </div>
          </form>
        </Modale>
      )}
    </CorpsSection>
  );
}
