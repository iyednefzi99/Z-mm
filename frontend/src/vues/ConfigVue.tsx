import { useEffect, useState, type ReactElement } from 'react';
import { recupererSeuils } from '../api/client';
import type { Seuils } from '../api/types';
import { useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { Bouton } from '../ui/composants';

/** Affiche, en lecture seule, les seuils metier de ConfigZumm.ini (US-025). */
export function ConfigVue(): ReactElement {
  const t = useT();
  const [seuils, setSeuils] = useState<Seuils | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);

  const charger = () => {
    setChargement(true);
    setErreur(null);
    recupererSeuils()
      .then(setSeuils)
      .catch((cause: unknown) => setErreur(messageErreur(cause)))
      .finally(() => setChargement(false));
  };

  useEffect(charger, []);

  return (
    <section className="z-section">
      <header className="z-section__entete">
        <div>
          <h1 className="z-section__titre">{t.config.titre}</h1>
          <p className="z-section__soustitre">{t.config.sousTitre}</p>
        </div>
        <Bouton onClick={charger}>{t.actions.reessayer}</Bouton>
      </header>

      {chargement && (
        <p className="z-info" role="status">
          {t.etats.chargement}
        </p>
      )}
      {erreur && (
        <div className="z-erreur" role="alert">
          {erreur}
        </div>
      )}

      {seuils && !erreur && (
        <div className="z-seuils">
          <Seuil libelle={t.config.poids} valeur={seuils.poidsRucheAlerteKg} />
          <Seuil libelle={t.config.tempMin} valeur={seuils.temperatureMinCelsius} />
          <Seuil libelle={t.config.tempMax} valeur={seuils.temperatureMaxCelsius} />
          <Seuil libelle={t.config.humidite} valeur={seuils.humiditeMaxPourcent} />
          <Seuil libelle={t.config.delai} valeur={seuils.delaiAlerteJours} />
          <Seuil libelle={t.config.arrondi} valeur={seuils.arrondiDegresPublic} />
          <Seuil libelle={t.config.langues} valeur={seuils.languesActives.join(' · ')} />
        </div>
      )}
    </section>
  );
}

function Seuil({ libelle, valeur }: { libelle: string; valeur: number | string }): ReactElement {
  return (
    <div className="z-seuil">
      <span className="z-seuil__valeur">{valeur}</span>
      <span className="z-seuil__libelle">{libelle}</span>
    </div>
  );
}
