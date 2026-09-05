import { useEffect, useState, type ReactElement } from 'react';
import { chargerInfo } from '../api/client';
import { useLangue, useT } from '../i18n/langue';
import { definirModeLocal, modeLocal } from './modeLocal';

/**
 * Mode local : ce qui sort, et ce qui ne sort pas (SPRINT-30, lot G).
 *
 * <p>Ferme la ligne « traitement local, sans aucun trafic sortant » du §8, dont
 * le reproche était précis : il n'existait aucune **bascule explicite**.
 *
 * <p><strong>Deux moitiés, et l'écran refuse de les confondre.</strong> Le
 * serveur coupe la météo et le microservice d'anomalie ; cet appareil coupe les
 * fonds de carte, qui partent du navigateur et dont la seule séquence révèle où
 * sont les ruchers. Afficher un seul interrupteur pour les deux ferait croire à
 * l'exploitant que son poste est muet alors qu'il ne l'est qu'à moitié —
 * promettre plus qu'on ne tient est pire que ne rien promettre.
 *
 * <p>Le réglage du serveur est en LECTURE seule ici : il vit dans la
 * configuration de déploiement (`zumm.reseau.sortant`), pas dans un écran. Une
 * politique d'exploitation que n'importe quel utilisateur peut basculer depuis
 * son navigateur n'est pas une politique.
 */
export function PanneauModeLocal(): ReactElement {
  const t = useT();
  const { langue } = useLangue();
  const [local, setLocal] = useState(modeLocal());
  const [serveurSortant, setServeurSortant] = useState<boolean | null>(null);

  useEffect(() => {
    void chargerInfo(langue)
      .then((info) => setServeurSortant(info.reseauSortant))
      .catch(() => setServeurSortant(null));
  }, [langue]);

  const basculer = () => setLocal(definirModeLocal(!local));

  return (
    <section className="z-legal__section">
      <h2 className="z-legal__soustitre">{t.modeLocal.titre}</h2>
      <p>{t.modeLocal.aide}</p>

      <ul className="z-liste">
        <li className="z-liste__ligne">
          <span>
            <strong>{t.modeLocal.appareil}</strong>
            <br />
            <small>{local ? t.modeLocal.actif : t.modeLocal.inactif}</small>
          </span>
          <label className="z-champ z-champ--case">
            <input type="checkbox" checked={local} onChange={basculer} />
            <span className="z-champ__libelle">{t.modeLocal.titre}</span>
          </label>
        </li>
        <li className="z-liste__ligne">
          <span>
            <strong>{t.modeLocal.serveur}</strong>
            <br />
            <small>
              {serveurSortant === null
                ? '—'
                : serveurSortant
                  ? t.modeLocal.inactif
                  : t.modeLocal.actif}
            </small>
          </span>
        </li>
      </ul>

      {local && <p className="z-info">{t.modeLocal.carteCoupee}</p>}
    </section>
  );
}
