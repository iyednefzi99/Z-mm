import { useState, type ReactElement } from 'react';
import { fermiers } from '../api/client';
import type { Fermier, FermierCorps } from '../api/types';
import { useRessource } from '../hooks';
import { Bouton, ChampTexte, Colonne, Modale, Table } from '../ui/composants';
import { gabarit, L } from '../ui/libelles';
import { CorpsSection } from './CorpsSection';

const colonnes: Colonne<Fermier>[] = [
  { entete: L.champs.nom, rendu: (f) => f.nom },
  { entete: L.champs.contact, rendu: (f) => f.contact ?? '—' },
];

export function FermiersVue(): ReactElement {
  const etat = useRessource<Fermier, FermierCorps>(fermiers);
  const [edition, setEdition] = useState<Fermier | null>(null);
  const [ouvert, setOuvert] = useState(false);
  const [nom, setNom] = useState('');
  const [contact, setContact] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);

  const ouvrir = (f: Fermier | null) => {
    setEdition(f);
    setNom(f?.nom ?? '');
    setContact(f?.contact ?? '');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    const corps: FermierCorps = { nom, contact: contact.trim() === '' ? null : contact };
    try {
      await (edition ? etat.mettreAJour(edition.id, corps) : etat.creer(corps));
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : L.etats.erreur);
    }
  };

  const supprimer = (f: Fermier) => {
    if (window.confirm(gabarit(L.etats.confirmerSuppression, { nom: f.nom }))) {
      void etat.supprimer(f.id);
    }
  };

  return (
    <CorpsSection titre={L.onglets.fermiers} etat={etat} onNouveau={() => ouvrir(null)}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={ouvrir} onSupprimer={supprimer} />
      )}
      {ouvert && (
        <Modale titre={L.onglets.fermiers} onFermer={() => setOuvert(false)}>
          <form
            className="z-form"
            onSubmit={(e) => {
              e.preventDefault();
              void enregistrer();
            }}
          >
            <ChampTexte libelle={L.champs.nom} valeur={nom} onChange={setNom} requis />
            <ChampTexte libelle={L.champs.contact} valeur={contact} onChange={setContact} />
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
