import { useEffect, useState, type ReactElement } from 'react';
import { fermes, fermiers } from '../api/client';
import type { Ferme, FermeCorps, Fermier } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import { Bouton, ChampSelect, ChampTexte, Colonne, Modale, Option, Table } from '../ui/composants';
import { CorpsSection } from './CorpsSection';

export function FermesVue(): ReactElement {
  const t = useT();
  const etat = useRessource<Ferme, FermeCorps>(fermes);
  const [optionsFermier, setOptionsFermier] = useState<Option[]>([]);
  const [edition, setEdition] = useState<Ferme | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [nom, setNom] = useState('');
  const [fermierId, setFermierId] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);

  const colonnes: Colonne<Ferme>[] = [
    { entete: t.champs.nom, rendu: (f) => f.nom },
    { entete: t.champs.fermier, rendu: (f) => f.fermierNom },
  ];

  useEffect(() => {
    fermiers
      .lister()
      .then((liste: Fermier[]) =>
        setOptionsFermier(liste.map((f) => ({ valeur: String(f.id), libelle: f.nom }))),
      )
      .catch(() => setOptionsFermier([]));
  }, [etat.elements]);

  const ouvrir = (f: Ferme | null) => {
    setEdition(f);
    setNom(f?.nom ?? '');
    setFermierId(f ? String(f.fermierId) : '');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (fermierId === '') {
      setErreur(`${t.champs.fermier} ?`);
      return;
    }
    const corps: FermeCorps = { nom, fermierId: Number(fermierId) };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const supprimer = (f: Ferme) => {
    if (window.confirm(gabarit(t.etats.confirmerSuppression, { nom: f.nom }))) {
      void etat.supprimer(f.id);
    }
  };

  return (
    <CorpsSection titre={t.onglets.fermes} etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={supprimer} />
      )}
      {ouvert && (
        <Modale titre={t.onglets.fermes} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={t.champs.nom} valeur={nom} onChange={setNom} requis />
            <ChampSelect
              libelle={t.champs.fermier}
              valeur={fermierId}
              options={optionsFermier}
              onChange={setFermierId}
              requis
            />
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
