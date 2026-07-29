import { useEffect, useState, type ReactElement } from 'react';
import {
  agents,
  ajouterPhoto,
  listerPhotos,
  plannings,
  ruches,
  supprimerPhoto,
  telechargerRapportVisite,
  visites,
} from '../api/client';
import type {
  Agent,
  EffectifQualitatif,
  EtatSante,
  Photo,
  Planning,
  RaisonVisite,
  Ruche,
  Visite,
  VisiteCorps,
} from '../api/types';
import { RAISONS_VISITE } from '../api/types';
import { useFormats, useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampHeure,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  ChampZone,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { CorpsSection } from './CorpsSection';

const EFFECTIFS: EffectifQualitatif[] = ['faible', 'moyen', 'fort'];
const SANTES: EtatSante[] = ['bon', 'moyen', 'mauvais'];
const PRODUCTIVITES = ['1', '2', '3'];

export function VisitesVue(): ReactElement {
  const t = useT();
  const f = useFormats();
  const etat = useRessource<Visite, VisiteCorps>(visites);
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [optAgents, setOptAgents] = useState<Option[]>([]);
  const [approuves, setApprouves] = useState<Planning[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [edition, setEdition] = useState<Visite | null>(null);
  const [rucheId, setRucheId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [planningId, setPlanningId] = useState('');
  const [dateVisite, setDateVisite] = useState('');
  const [heureVisite, setHeureVisite] = useState('');
  const [dureeMin, setDureeMin] = useState('');
  const [raison, setRaison] = useState<RaisonVisite>('controle');
  const [constatations, setConstatations] = useState('');
  const [actionsPrevues, setActionsPrevues] = useState('');
  const [actionsEffectuees, setActionsEffectuees] = useState('');
  const [recommandations, setRecommandations] = useState('');
  const [effectif, setEffectif] = useState('');
  const [sante, setSante] = useState('');
  const [productivite, setProductivite] = useState('');
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [urlPhoto, setUrlPhoto] = useState('');
  const [legende, setLegende] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);

  const optRaison: Option[] = RAISONS_VISITE.map((r) => ({ valeur: r, libelle: t.visite.raisons[r] }));
  const vide: Option = { valeur: '', libelle: t.champs.aucun };
  const optEffectif: Option[] = [vide, ...EFFECTIFS.map((e) => ({ valeur: e, libelle: t.visite.effectifs[e] }))];
  const optSante: Option[] = [vide, ...SANTES.map((s) => ({ valeur: s, libelle: t.visite.santes[s] }))];
  const optProd: Option[] = [vide, ...PRODUCTIVITES.map((p) => ({ valeur: p, libelle: p }))];

  // Un rapport ne se rattache qu'a un planning APPROUVE (US-008), et seulement a
  // ceux de la ruche visitee : proposer les autres inviterait a une incoherence
  // que le serveur refuserait de toute facon.
  const optPlannings: Option[] = [
    vide,
    ...approuves
      .filter((p) => rucheId === '' || String(p.rucheId) === rucheId)
      .map((p) => ({
        valeur: String(p.id),
        libelle: `${f.date(p.datePrevue)} — ${t.visite.raisons[p.raison]}`,
      })),
  ];

  const colonnes: Colonne<Visite>[] = [
    { entete: t.champs.modele, rendu: (v) => v.rucheModele },
    { entete: t.visite.agent, rendu: (v) => v.agentNom },
    { entete: t.visite.date, rendu: (v) => f.date(v.dateVisite) },
    { entete: t.visite.sante, rendu: (v) => (v.etatSante ? t.visite.santes[v.etatSante] : '—') },
    { entete: t.visite.photos, rendu: (v) => String(v.photos.length) },
    {
      entete: t.visite.rapport,
      rendu: (v) => (
        <Bouton variante="fantome" onClick={() => void telechargerRapportVisite(v.id)}>
          ⬇ {t.visite.rapportPdf}
        </Bouton>
      ),
    },
  ];

  useEffect(() => {
    void ruches.lister().then((l: Ruche[]) => setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele })))).catch(() => setOptRuches([]));
    void agents.lister().then((l: Agent[]) => setOptAgents(l.map((a) => ({ valeur: String(a.id), libelle: a.nom })))).catch(() => setOptAgents([]));
    void plannings
      .lister()
      .then((l: Planning[]) => setApprouves(l.filter((p) => p.statut === 'approuve')))
      .catch(() => setApprouves([]));
  }, [etat.elements]);

  const ouvrir = (v: Visite | null) => {
    setEdition(v);
    setRucheId(v ? String(v.rucheId) : '');
    setAgentId(v ? String(v.agentId) : '');
    setPlanningId(v?.planningId != null ? String(v.planningId) : '');
    setDateVisite(v?.dateVisite ?? '');
    // Le serveur publie « 14:30:00 » ; `input type="time"` veut « 14:30 ».
    setHeureVisite(v?.heureVisite != null ? v.heureVisite.slice(0, 5) : '');
    setDureeMin(v?.dureeMin != null ? String(v.dureeMin) : '');
    setRaison(v?.raison ?? 'controle');
    setConstatations(v?.constatations ?? '');
    setActionsPrevues(v?.actionsPrevues ?? '');
    setActionsEffectuees(v?.actionsEffectuees ?? '');
    setRecommandations(v?.recommandations ?? '');
    setEffectif(v?.effectifQualitatif ?? '');
    setSante(v?.etatSante ?? '');
    setProductivite(v?.productivite != null ? String(v.productivite) : '');
    setPhotos(v?.photos ?? []);
    setUrlPhoto('');
    setLegende('');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (rucheId === '' || agentId === '') {
      setErreur(t.etats.champsRequis);
      return;
    }
    const texte = (valeur: string) => (valeur.trim() === '' ? null : valeur.trim());
    const corps: VisiteCorps = {
      rucheId: Number(rucheId),
      agentId: Number(agentId),
      planningId: planningId === '' ? null : Number(planningId),
      dateVisite,
      heureVisite: heureVisite === '' ? null : heureVisite,
      dureeMin: dureeMin === '' ? null : Number(dureeMin),
      raison,
      constatations: texte(constatations),
      actionsPrevues: texte(actionsPrevues),
      actionsEffectuees: texte(actionsEffectuees),
      recommandations: texte(recommandations),
      effectifQualitatif: effectif === '' ? null : (effectif as EffectifQualitatif),
      etatSante: sante === '' ? null : (sante as EtatSante),
      productivite: productivite === '' ? null : Number(productivite),
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const ajouterUnePhoto = async () => {
    if (!edition || urlPhoto.trim() === '') return;
    try {
      await ajouterPhoto(edition.id, { url: urlPhoto, legende: legende.trim() === '' ? null : legende });
      setPhotos(await listerPhotos(edition.id));
      setUrlPhoto('');
      setLegende('');
      etat.recharger();
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const retirerPhoto = async (photo: Photo) => {
    if (!edition) return;
    await supprimerPhoto(edition.id, photo.id);
    setPhotos(await listerPhotos(edition.id));
    etat.recharger();
  };

  return (
    <CorpsSection
      titre={t.onglets.visites}
      sousTitre={t.soustitres.visites}
      etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={(e) => void etat.supprimer(e.id)} />
      )}
      {ouvert && (
        <Modale titre={t.onglets.visites} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <div className="z-form__grille">
              <ChampSelect libelle={t.champs.modele} valeur={rucheId} options={optRuches} onChange={setRucheId} requis />
              <ChampSelect libelle={t.visite.agent} valeur={agentId} options={optAgents} onChange={setAgentId} requis />
            </div>
            <ChampSelect libelle={t.visite.planning} valeur={planningId} options={optPlannings} onChange={setPlanningId} />
            <div className="z-form__grille">
              <ChampDate libelle={t.visite.date} valeur={dateVisite} onChange={setDateVisite} requis />
              <ChampHeure libelle={t.visite.heure} valeur={heureVisite} onChange={setHeureVisite} />
              <ChampNombre libelle={t.visite.duree} valeur={dureeMin} onChange={setDureeMin} pas="1" />
            </div>
            <ChampSelect libelle={t.visite.raison} valeur={raison} options={optRaison} onChange={(v) => setRaison(v as RaisonVisite)} />
            <ChampZone libelle={t.visite.constatations} valeur={constatations} onChange={setConstatations} />
            <ChampZone libelle={t.visite.actionsPrevues} valeur={actionsPrevues} onChange={setActionsPrevues} />
            <ChampZone libelle={t.visite.actionsEffectuees} valeur={actionsEffectuees} onChange={setActionsEffectuees} />
            <ChampZone libelle={t.visite.recommandations} valeur={recommandations} onChange={setRecommandations} />
            <div className="z-form__grille">
              <ChampSelect libelle={t.visite.effectif} valeur={effectif} options={optEffectif} onChange={setEffectif} />
              <ChampSelect libelle={t.visite.sante} valeur={sante} options={optSante} onChange={setSante} />
              <ChampSelect libelle={t.visite.productivite} valeur={productivite} options={optProd} onChange={setProductivite} />
            </div>

            {edition && (
              <fieldset className="z-composition">
                <legend className="z-champ__libelle">{t.visite.photos}</legend>
                {photos.map((p) => (
                  <div key={p.id} className="z-composition__hausse">
                    <span className="z-photo-ref">{p.legende ?? p.url}</span>
                    <Bouton variante="fantome" onClick={() => void retirerPhoto(p)}>
                      ✕
                    </Bouton>
                  </div>
                ))}
                <div className="z-form__grille">
                  <ChampTexte libelle={t.visite.url} valeur={urlPhoto} onChange={setUrlPhoto} />
                  <ChampTexte libelle={t.visite.legende} valeur={legende} onChange={setLegende} />
                </div>
                <Bouton variante="secondaire" onClick={() => void ajouterUnePhoto()}>
                  + {t.actions.ajouterPhoto}
                </Bouton>
              </fieldset>
            )}

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
