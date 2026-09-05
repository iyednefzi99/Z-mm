import { useEffect, useState, type ReactElement } from 'react';
import {
  chargerDemonstration,
  etatDemonstration,
  purgerDemonstration,
  recupererSeuils,
} from '../api/client';
import type { EtatDemonstration, Seuils } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { messageErreur, useRoles } from '../hooks';
import { Bouton } from '../ui/composants';
import { EditeurCarnet } from '../carnet/EditeurCarnet';
import { peutEcrire } from '../routage/routes';
import { useDialogues } from '../ui/dialogues';

/**
 * Seuils metier de ConfigZumm.ini (US-025), jeu de demonstration (SPRINT-25) et
 * carnet parametrable (SPRINT-28).
 *
 * <p>Les trois cohabitent parce qu'ils repondent a la meme question : comment
 * cette exploitation-ci est reglee. Le carnet y a sa place plutot que dans
 * l'ecran des visites — quelles cases figurent a la saisie engage toutes les
 * inspections a venir, et se decide une fois.
 */
export function ConfigVue(): ReactElement {
  const t = useT();
  const ecritureCarnet = peutEcrire('config', useRoles());
  const indisponible = t.etats.serviceIndisponible;
  const [seuils, setSeuils] = useState<Seuils | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  // Jeu de démonstration (SPRINT-25, lot J). Il vit ici parce qu'il ÉCRIT dans
  // l'exploitation : c'est un geste d'administration, pas d'apiculture.
  const [demo, setDemo] = useState<EtatDemonstration | null>(null);
  const [demoEnCours, setDemoEnCours] = useState(false);
  const { confirmer } = useDialogues();

  const charger = () => {
    setChargement(true);
    setErreur(null);
    recupererSeuils()
      .then(setSeuils)
      .catch((cause: unknown) => setErreur(messageErreur(cause, indisponible)))
      .finally(() => setChargement(false));
  };

  // `charger` lit le message d'indisponibilite : le recharger si la langue change.
  useEffect(charger, [indisponible]);

  useEffect(() => {
    // Un 403 est la réponse NORMALE pour un rôle autre qu'`admin` : la section
    // disparaît alors, plutôt que d'afficher une erreur pour une fonction qui
    // ne le concerne pas.
    void etatDemonstration()
      .then(setDemo)
      .catch(() => setDemo(null));
  }, []);

  const agirDemonstration = async (charger: boolean) => {
    if (!charger) {
      const suite = await confirmer(
        gabarit(t.demonstration.confirmation, { objets: String(demo?.objets ?? 0) }),
      );
      if (!suite) {
        return;
      }
    }
    setDemoEnCours(true);
    try {
      setDemo(charger ? await chargerDemonstration() : await purgerDemonstration());
    } catch (cause) {
      setErreur(messageErreur(cause, indisponible));
    } finally {
      setDemoEnCours(false);
    }
  };

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
          {/* Les deux hypothèses qui déterminent le ROI de l'écran de synthèse.
              Elles étaient des constantes compilées : les afficher rend visible
              ce qui, sinon, se lit comme un chiffre tombé du ciel. */}
          <Seuil libelle={t.config.prixMiel} valeur={seuils.prixMielKgEur} />
          <Seuil libelle={t.config.coutVisite} valeur={seuils.coutVisiteEur} />
          <Seuil libelle={t.config.langues} valeur={seuils.languesActives.join(' · ')} />
        </div>
      )}

      <EditeurCarnet ecriture={ecritureCarnet} />

      {demo && (
        <section className="z-legal__section">
          <h2 className="z-legal__soustitre">{t.demonstration.titre}</h2>
          <p>{t.demonstration.aide}</p>
          {!demo.disponible ? (
            // Éteinte par défaut : une production ne doit pas seulement refuser
            // d'écrire vingt lignes fictives, elle ne doit pas proposer le bouton.
            <p className="z-info">{t.demonstration.indisponible}</p>
          ) : (
            <>
              <p className="z-info" role="status">
                {demo.charge
                  ? gabarit(t.demonstration.charge, { objets: String(demo.objets) })
                  : t.demonstration.absent}
              </p>
              <div className="z-form__actions">
                {demo.charge ? (
                  <Bouton
                    variante="secondaire"
                    disabled={demoEnCours}
                    onClick={() => void agirDemonstration(false)}
                  >
                    {t.demonstration.retirer}
                  </Bouton>
                ) : (
                  <Bouton
                    variante="primaire"
                    disabled={demoEnCours}
                    onClick={() => void agirDemonstration(true)}
                  >
                    {t.demonstration.charger}
                  </Bouton>
                )}
              </div>
            </>
          )}
        </section>
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
