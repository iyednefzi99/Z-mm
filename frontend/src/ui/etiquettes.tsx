import { useEffect, useState, type ReactElement } from 'react';
import QRCode from 'qrcode';

/**
 * Étiquetage durable des ruches (SPRINT-25, lot J).
 *
 * <p>Trois éditeurs — APiLOG, ApiManager, APIGO — conseillent à leurs
 * utilisateurs de coller des QR codes ou de poser des puces NFC « plutôt que des
 * autocollants » (§13 de `docs/ECART-CONCURRENTS.md`). Un conseil que trois
 * éditeurs répètent n'est pas une astuce : c'est une fonction qui manque.
 *
 * <p><strong>Le code court n'est pas un second identifiant.</strong> C'est
 * l'identifiant de la ruche, préfixé — `R-42`. La tentation était d'encoder un
 * code opaque et joli ; elle aurait créé deux façons de nommer la même colonie,
 * et le jour où elles divergent, personne ne sait laquelle fait foi. Il se lit à
 * l'œil nu quand le scan échoue, ce qui est tout ce qu'on lui demande : au
 * rucher, un QR sale, mouillé ou propolisé ne se scanne pas.
 */

/** Charge utile du QR d'une ruche. Même forme que celle du lot de récolte. */
export const chargeQrRuche = (rucheId: number): string => `zumm:ruche:${rucheId}`;

/** Code court lisible à l'œil nu, imprimé sous le QR. */
export const codeCourt = (rucheId: number): string => `R-${rucheId}`;

/**
 * Image QR d'une charge utile.
 *
 * <p>Rendue en composant plutôt qu'en `<img src>` direct : la bibliothèque
 * produit une image de façon asynchrone, et un rendu synchrone afficherait une
 * case vide le temps du calcul.
 */
export function QrImage({
  payload,
  taille = 220,
}: {
  payload: string;
  taille?: number;
}): ReactElement {
  const [url, setUrl] = useState('');
  useEffect(() => {
    void QRCode.toDataURL(payload, { margin: 1, width: taille })
      .then(setUrl)
      .catch(() => setUrl(''));
  }, [payload, taille]);
  return url ? (
    <img
      src={url}
      alt={payload}
      width={taille}
      height={taille}
      style={{ display: 'block', margin: '0 auto' }}
    />
  ) : (
    // Le texte de la charge utile en repli : mieux vaut un identifiant lisible
    // qu'un carré vide, y compris à l'impression.
    <span>{payload}</span>
  );
}

/**
 * Planche d'étiquettes d'un rucher, prête à imprimer.
 *
 * <p>Rendue dans le navigateur et non par le serveur, contrairement à la fiche
 * d'inspection du SPRINT-24 : la bibliothèque QR vit déjà dans le front
 * (`RecoltesVue` s'en sert depuis l'US-033), et l'ajouter au back-end aurait
 * introduit une dépendance Java pour produire ce que le navigateur sait faire.
 *
 * <p>La mise en page est en `@media print` (`App.css`) : l'écran garde la
 * console autour de la planche, l'impression ne sort que les étiquettes.
 */
export function PlancheEtiquettes({
  rucherNom,
  ruches,
}: {
  rucherNom: string;
  ruches: { id: number; modele: string }[];
}): ReactElement {
  return (
    <div className="z-planche">
      {ruches.map((ruche) => (
        <div className="z-etiquette" key={ruche.id}>
          <QrImage payload={chargeQrRuche(ruche.id)} taille={120} />
          <strong className="z-etiquette__code">{codeCourt(ruche.id)}</strong>
          <span className="z-etiquette__detail">{ruche.modele}</span>
          {/* Le rucher figure sur l'étiquette : une ruche transhume, et
              l'étiquette dit alors d'où elle vient — pas où elle est. */}
          <span className="z-etiquette__detail">{rucherNom}</span>
        </div>
      ))}
    </div>
  );
}
