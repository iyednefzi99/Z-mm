import { useEffect, useRef, useState, type ReactElement } from 'react';
import { useLangue, useT } from '../i18n/langue';
import { Bouton } from '../ui/composants';
import {
  demarrerDictee,
  etatDictee,
  langueReconnaissance,
  type DicteeEnCours,
} from './dictee';

/**
 * Bouton de dictée, à poser à côté d'un champ de texte (SPRINT-30, lot G).
 *
 * <p>Le geste que le §4 réclamait : « le seul qui fonctionne avec des gants ».
 *
 * <p><strong>Le texte s'AJOUTE au champ, il ne le remplace pas.</strong> Une
 * dictée qui écrase ce qui est déjà écrit fait perdre la saisie au premier
 * appui malheureux — et l'appui malheureux est la règle avec des gants.
 *
 * <p>Quand le navigateur ne peut pas transcrire localement, le bouton
 * **disparaît** et la raison s'affiche. Un bouton présent qui refuse de
 * fonctionner se lit comme une panne ; une fonctionnalité absente qui se dit
 * laisse choisir le clavier en connaissance de cause.
 */
export function BoutonDictee({
  valeur,
  onChange,
  libelle,
}: {
  valeur: string;
  onChange: (valeur: string) => void;
  libelle: string;
}): ReactElement | null {
  const t = useT();
  const { langue } = useLangue();
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const session = useRef<DicteeEnCours | null>(null);
  const etat = etatDictee();

  // Une dictée laissée ouverte continue d'écouter le micro après la fermeture
  // de la modale : le nettoyage n'est pas une précaution, c'est une obligation.
  useEffect(() => () => session.current?.arreter(), []);

  if (etat === 'absente') {
    return null;
  }

  if (etat === 'distante') {
    // Rien n'est envoyé, et l'écran dit pourquoi (ADR-013).
    return (
      <p className="z-info z-dictee__refus">{t.dictee.refusDistante}</p>
    );
  }

  const basculer = () => {
    if (enCours) {
      session.current?.arreter();
      return;
    }
    setErreur(null);
    const ouverte = demarrerDictee(
      langueReconnaissance(langue),
      (segment) => onChange(valeur === '' ? segment : `${valeur} ${segment}`),
      () => {
        setEnCours(false);
        session.current = null;
      },
      (code) => {
        setErreur(code === 'not-allowed' ? t.dictee.microRefuse : t.dictee.erreur);
        setEnCours(false);
      },
    );
    if (ouverte === null) {
      setErreur(t.dictee.erreur);
      return;
    }
    session.current = ouverte;
    setEnCours(true);
  };

  return (
    <div className="z-dictee">
      <Bouton variante={enCours ? 'primaire' : 'secondaire'} onClick={basculer}>
        {enCours ? `⏹ ${t.dictee.arreter}` : `🎙 ${libelle}`}
      </Bouton>
      {enCours && (
        <span className="z-dictee__etat" role="status">
          {t.dictee.ecoute}
        </span>
      )}
      {erreur !== null && (
        <span className="z-dictee__erreur" role="alert">
          {erreur}
        </span>
      )}
      {/* La phrase qui compte : ce qui est dicté se relit avant d'être validé. */}
      <span className="z-dictee__aide">{t.dictee.relecture}</span>
    </div>
  );
}
