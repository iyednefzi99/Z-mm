import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { attacherPhoto, detacherPhoto, listerPhotosDe } from '../api/client';
import type { CiblePhoto, Photo } from '../api/types';
import { useT } from '../i18n/langue';
import { messageErreur } from '../hooks';
import { useToasts } from '../ui/toasts';
import { Bouton, ChampTexte } from '../ui/composants';

/**
 * Photos attachées à un objet du parc (SPRINT-21, six cibles au SPRINT-28).
 *
 * <p><strong>Pourquoi un panneau et non un écran.</strong> `/api/photos` est
 * générique — une ligne, une cible, `ck_photo_cible_unique` — mais une photo ne
 * se consulte jamais pour elle-même : on regarde les photos D'UNE ruche, D'UN
 * rucher, D'UNE ordonnance. Un onglet « Photos » aurait obligé à choisir la
 * cible avant de voir quoi que ce soit, c'est-à-dire à connaître la réponse
 * avant de poser la question. Le panneau se monte dans la fiche de l'objet, et
 * la cible est alors déjà décidée.
 *
 * <p><strong>Ce qui est stocké, et ce qui ne l'est pas.</strong> Le dépôt n'a
 * aucun stockage binaire, et c'est un invariant : encoder une image en base64
 * dans un champ texte fabriquerait un stockage de fichiers clandestin, invisible
 * en revue et hors de toute politique de rétention. La ligne ne porte donc
 * qu'une ADRESSE, et l'écran le dit — sans quoi « détacher » se lirait comme
 * « supprimer le fichier », ce qu'il n'est pas.
 *
 * <p>Le détachement suit la règle de la maison : pas de boîte de confirmation,
 * une fenêtre d'annulation. La ligne quitte l'écran tout de suite, l'appel part
 * à l'expiration du délai.
 */
export function PanneauPhotos({
  cible,
  cibleId,
  ecriture = true,
}: {
  cible: CiblePhoto;
  cibleId: number;
  /** Faux quand le rôle ne peut pas écrire sur l'objet porteur. */
  ecriture?: boolean;
}): ReactElement {
  const t = useT();
  const toasts = useToasts();
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [url, setUrl] = useState('');
  const [legende, setLegende] = useState('');

  const recharger = useCallback(() => {
    void listerPhotosDe(cible, cibleId)
      .then(setPhotos)
      .catch(() => setPhotos([]));
  }, [cible, cibleId]);

  useEffect(recharger, [recharger]);

  const attacher = async () => {
    if (url.trim() === '') {
      return;
    }
    try {
      await attacherPhoto({
        cible,
        cibleId,
        url: url.trim(),
        legende: legende.trim() === '' ? null : legende.trim(),
      });
      setUrl('');
      setLegende('');
      recharger();
    } catch (cause) {
      toasts.erreur(messageErreur(cause, t.etats.serviceIndisponible));
    }
  };

  /** Retire la ligne tout de suite, envoie le détachement à l'expiration. */
  const detacher = (photo: Photo) => {
    setPhotos((restantes) => restantes.filter((p) => p.id !== photo.id));
    toasts.annulable(
      t.retours.supprime,
      () => {
        detacherPhoto(photo.id)
          .then(recharger)
          .catch((cause: unknown) => {
            toasts.erreur(messageErreur(cause, t.retours.echecSuppression));
            recharger();
          });
      },
      recharger,
    );
  };

  return (
    <fieldset className="z-composition">
      <legend className="z-champ__libelle">{t.photos.titre}</legend>
      <p className="z-info">{t.photos.aide}</p>

      {photos.length === 0 ? (
        <p className="z-info">{t.photos.aucune}</p>
      ) : (
        <ul className="z-liste-simple">
          {photos.map((photo) => (
            <li key={photo.id}>
              {/* `noopener` sur une adresse que l'exploitation a saisie : elle
                  pointe hors de l'application, et rien ne dit vers quoi. */}
              <a
                className="z-lien"
                href={photo.url}
                target="_blank"
                rel="noopener noreferrer"
              >
                {photo.legende ?? t.photos.ouvrir}
              </a>
              {ecriture && (
                <>
                  {' '}
                  <button
                    type="button"
                    className="z-lien z-lien--danger"
                    onClick={() => detacher(photo)}
                  >
                    {t.actions.detacher}
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      {ecriture && (
        <div className="z-form__grille">
          <ChampTexte libelle={t.photos.url} valeur={url} onChange={setUrl} />
          <ChampTexte libelle={t.photos.legende} valeur={legende} onChange={setLegende} />
          <div className="z-champ z-champ--aligne-bas">
            <Bouton
              variante="secondaire"
              disabled={url.trim() === ''}
              onClick={() => void attacher()}
            >
              {t.actions.attacher}
            </Bouton>
          </div>
        </div>
      )}
    </fieldset>
  );
}
