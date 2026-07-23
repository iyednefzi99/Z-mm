import { useEffect, useState, type ReactElement } from 'react';
import { fermes, sites } from '../api/client';
import type { Ferme, Site, SiteCorps } from '../api/types';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { gabarit, L } from '../ui/libelles';
import { CorpsSection } from './CorpsSection';

const colonnes: Colonne<Site>[] = [
  { entete: L.champs.nom, rendu: (s) => s.nom },
  { entete: L.champs.ferme, rendu: (s) => s.fermeNom },
  { entete: L.champs.latitude, rendu: (s) => s.latitude.toFixed(4) },
  { entete: L.champs.longitude, rendu: (s) => s.longitude.toFixed(4) },
];

const ouNull = (valeur: string): string | null => (valeur.trim() === '' ? null : valeur);

export function SitesVue(): ReactElement {
  const etat = useRessource<Site, SiteCorps>(sites);
  const [optionsFerme, setOptionsFerme] = useState<Option[]>([]);
  const [edition, setEdition] = useState<Site | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [nom, setNom] = useState('');
  const [fermeId, setFermeId] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [altitude, setAltitude] = useState('');
  const [miseEnOeuvre, setMiseEnOeuvre] = useState('');
  const [demenagement, setDemenagement] = useState('');
  const [cloture, setCloture] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    fermes
      .lister()
      .then((liste: Ferme[]) =>
        setOptionsFerme(liste.map((f) => ({ valeur: String(f.id), libelle: f.nom }))),
      )
      .catch(() => setOptionsFerme([]));
  }, [etat.elements]);

  const ouvrir = (s: Site | null) => {
    setEdition(s);
    setNom(s?.nom ?? '');
    setFermeId(s ? String(s.fermeId) : '');
    setLatitude(s ? String(s.latitude) : '');
    setLongitude(s ? String(s.longitude) : '');
    setAltitude(s?.altitude != null ? String(s.altitude) : '');
    setMiseEnOeuvre(s?.dateMiseEnOeuvre ?? '');
    setDemenagement(s?.dateDemenagement ?? '');
    setCloture(s?.dateCloture ?? '');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (fermeId === '') {
      setErreur(`${L.champs.ferme} ?`);
      return;
    }
    const corps: SiteCorps = {
      nom,
      fermeId: Number(fermeId),
      latitude: Number(latitude),
      longitude: Number(longitude),
      altitude: altitude.trim() === '' ? null : Number(altitude),
      dateMiseEnOeuvre: miseEnOeuvre,
      dateDemenagement: ouNull(demenagement),
      dateCloture: ouNull(cloture),
    };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : L.etats.erreur);
    }
  };

  const supprimer = (s: Site) => {
    if (window.confirm(gabarit(L.etats.confirmerSuppression, { nom: s.nom }))) {
      void etat.supprimer(s.id);
    }
  };

  return (
    <CorpsSection titre={L.onglets.sites} etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={supprimer} />
      )}
      {ouvert && (
        <Modale titre={L.onglets.sites} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={L.champs.nom} valeur={nom} onChange={setNom} requis />
            <ChampSelect
              libelle={L.champs.ferme}
              valeur={fermeId}
              options={optionsFerme}
              onChange={setFermeId}
              requis
            />
            <div className="z-form__grille">
              <ChampNombre libelle={L.champs.latitude} valeur={latitude} onChange={setLatitude} requis />
              <ChampNombre libelle={L.champs.longitude} valeur={longitude} onChange={setLongitude} requis />
              <ChampNombre libelle={L.champs.altitude} valeur={altitude} onChange={setAltitude} />
            </div>
            <div className="z-form__grille">
              <ChampDate
                libelle={L.champs.dateMiseEnOeuvre}
                valeur={miseEnOeuvre}
                onChange={setMiseEnOeuvre}
                requis
              />
              <ChampDate libelle={L.champs.dateDemenagement} valeur={demenagement} onChange={setDemenagement} />
              <ChampDate libelle={L.champs.dateCloture} valeur={cloture} onChange={setCloture} />
            </div>
            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>
                {L.actions.annuler}
              </Bouton>
              <Bouton variante="primaire" type="submit">
                {L.actions.enregistrer}
              </Bouton>
            </div>
          </form>
        </Modale>
      )}
    </CorpsSection>
  );
}
