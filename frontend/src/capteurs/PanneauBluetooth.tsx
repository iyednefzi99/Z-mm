import { useState, type ReactElement } from 'react';
import { ingererMesures } from '../api/client';
import type { MesureCorps } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { Bouton, ChampSelect, type Option } from '../ui/composants';
import { etatBluetooth, lireCapteur, type LectureCapteur } from './bluetooth';

/**
 * Lecture directe d'un capteur, sans passerelle (SPRINT-31, lot F₂).
 *
 * <p>Ferme la ligne « connexion Bluetooth directe » du §5. Le profil lu est le
 * **standard SIG** ; un capteur qui s'en écarte demanderait son propre
 * adaptateur, que l'[ADR-014](../../../roadmap/operationnel/06_decisions/ADR-014-capteurs-du-commerce.md)
 * refuse d'écrire sans avoir l'appareil sous la main.
 *
 * <p><strong>Le panneau disparaît là où le navigateur ne sait pas faire</strong>
 * — Safari sur iPhone, Firefox — en disant pourquoi. Un bouton présent qui ne
 * réagit pas se lit comme une panne ; une absence expliquée laisse choisir la
 * passerelle en connaissance de cause. C'est la même règle que pour la dictée.
 *
 * <p>Les trois valeurs partent en **une** requête : un capteur rend température,
 * humidité et batterie d'un coup, et trois appels feraient trois occasions
 * d'échouer à moitié.
 */
export function PanneauBluetooth({
  optRuches,
  onIngere,
}: {
  optRuches: Option[];
  onIngere: () => void;
}): ReactElement | null {
  const t = useT();
  const [rucheId, setRucheId] = useState('');
  const [lecture, setLecture] = useState<LectureCapteur | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  if (etatBluetooth() === 'absent') {
    return (
      <fieldset className="z-composition">
        <legend className="z-champ__libelle">{t.bluetooth.titre}</legend>
        <p className="z-info">{t.bluetooth.absent}</p>
      </fieldset>
    );
  }

  const lire = async () => {
    setErreur(null);
    setMessage(null);
    setEnCours(true);
    try {
      const resultat = await lireCapteur();
      setLecture(resultat);
      if (resultat !== null && rucheId !== '') {
        await pousser(resultat);
      }
    } catch {
      // Refus de la boîte de dialogue, capteur hors de portée, GATT qui tombe :
      // trois causes, un seul message. Les distinguer demanderait de lire des
      // codes d'erreur que les navigateurs n'harmonisent pas.
      setErreur(t.bluetooth.erreur);
    } finally {
      setEnCours(false);
    }
  };

  const pousser = async (resultat: LectureCapteur) => {
    const mesures: MesureCorps[] = [];
    const ajouter = (typeIndicateur: MesureCorps['typeIndicateur'], valeur: number | null) => {
      if (valeur !== null) {
        mesures.push({ rucheId: Number(rucheId), typeIndicateur, valeur, instant: null });
      }
    };
    ajouter('temperature', resultat.temperature);
    ajouter('humidite', resultat.humidite);
    ajouter('alimentation', resultat.batterie);

    if (mesures.length === 0) {
      setMessage(t.bluetooth.rien);
      return;
    }
    await ingererMesures(mesures);
    setMessage(gabarit(t.bluetooth.enregistre, { nombre: String(mesures.length) }));
    onIngere();
  };

  return (
    <fieldset className="z-composition">
      <legend className="z-champ__libelle">{t.bluetooth.titre}</legend>
      <p className="z-info">{t.bluetooth.profil}</p>
      <div className="z-form__grille">
        <ChampSelect
          libelle={t.bluetooth.choisirRuche}
          valeur={rucheId}
          options={optRuches}
          onChange={setRucheId}
        />
        <div className="z-champ z-champ--aligne-bas">
          <Bouton
            variante="secondaire"
            disabled={enCours || rucheId === ''}
            onClick={() => void lire()}
          >
            📶 {enCours ? t.bluetooth.lecture : t.bluetooth.lire}
          </Bouton>
        </div>
      </div>
      {lecture !== null && (
        <p className="z-info" role="status">
          {lecture.nom} · {lecture.temperature ?? '—'} °C · {lecture.humidite ?? '—'} %
          {' · '}
          {lecture.batterie ?? '—'} %
        </p>
      )}
      {message !== null && (
        <p className="z-info" role="status">
          {message}
        </p>
      )}
      {erreur !== null && (
        <p className="z-erreur" role="alert">
          {erreur}
        </p>
      )}
    </fieldset>
  );
}
