import { useEffect, useState, type ReactElement } from 'react';
import { agents, fermes, ruches, sites } from '../api/client';
import type { Agent, EtatRuche, Ferme, Ruche, RucheCorps, Site } from '../api/types';
import { ETATS_RUCHE } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import { Bouton, ChampNombre, ChampSelect, ChampTexte, Colonne, Modale, Option, Table } from '../ui/composants';
import { CorpsSection } from './CorpsSection';

const MAX_HAUSSES = 5;

export function RuchesVue(): ReactElement {
  const t = useT();
  const etat = useRessource<Ruche, RucheCorps>(ruches);
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
  const [corpsCadres, setCorpsCadres] = useState('10');
  const [hausses, setHausses] = useState<string[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);

  const optionsEtat: Option[] = ETATS_RUCHE.map((e) => ({ valeur: e, libelle: t.etatsRuche[e] }));

  const colonnes: Colonne<Ruche>[] = [
    { entete: t.champs.modele, rendu: (r) => r.modele },
    { entete: t.champs.site, rendu: (r) => r.siteNom },
    { entete: t.champs.etat, rendu: (r) => t.etatsRuche[r.etat] },
    { entete: t.champs.hausses, rendu: (r) => String(r.nbHausses) },
  ];

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

  const ouvrir = (r: Ruche | null) => {
    setEdition(r);
    setModele(r?.modele ?? '');
    setSiteId(r ? String(r.siteId) : '');
    setFermeId(r ? String(r.fermeId) : '');
    setAgentId(r?.agentResponsableId != null ? String(r.agentResponsableId) : '');
    setEtatRuche(r?.etat ?? 'creee');
    const corps = r?.compartiments.find((c) => c.type === 'corps');
    setCorpsCadres(corps ? String(corps.nbCadres) : '10');
    setHausses(r ? r.compartiments.filter((c) => c.type === 'hausse').map((c) => String(c.nbCadres)) : []);
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
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const supprimer = (r: Ruche) => {
    if (window.confirm(gabarit(t.etats.confirmerSuppression, { nom: r.modele }))) {
      void etat.supprimer(r.id);
    }
  };

  const majHausse = (index: number, valeur: string) =>
    setHausses((liste) => liste.map((h, i) => (i === index ? valeur : h)));

  return (
    <CorpsSection titre={t.onglets.ruches} etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={supprimer} />
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
