/**
 * Écriture NFC d'une étiquette de ruche (SPRINT-25, lot J).
 *
 * <p><strong>Le NFC n'est pas le jumeau du QR, et le plan le disait déjà.</strong>
 * `NDEFReader` n'existe ni sur iOS, ni sur Firefox, ni sur Safari macOS : il est
 * disponible sur Chrome Android, et là seulement. Il se livre donc en
 * <em>complément</em> du QR, jamais en remplacement — un produit qui ne
 * s'identifierait qu'en NFC serait inutilisable sur la moitié du parc.
 *
 * <p>Conséquence sur l'interface : le bouton n'apparaît que si l'API existe.
 * Un bouton visible partout qui échoue une fois sur deux apprend à
 * l'utilisateur que la fonction ne marche pas, et il cesse de l'essayer là où
 * elle marche.
 */

/** Ce que le navigateur expose, quand il l'expose. Absent des types standard. */
interface LecteurNdef {
  write: (message: { records: { recordType: string; data: string }[] }) => Promise<void>;
}

type ConstructeurNdef = new () => LecteurNdef;

/** Le NFC est-il utilisable sur cet appareil ? */
export function nfcDisponible(): boolean {
  return typeof window !== 'undefined' && 'NDEFReader' in window;
}

/**
 * Écrit la charge utile sur l'étiquette approchée.
 *
 * <p>La promesse ne se résout qu'au contact du tag : c'est l'API qui attend, pas
 * nous. L'appelant doit donc afficher un état « approchez l'étiquette » plutôt
 * qu'un sablier muet.
 *
 * <p>Le contenu écrit est <strong>le même que celui du QR</strong>. Deux charges
 * utiles différentes pour le même objet auraient produit deux façons de
 * l'identifier, et un jour deux réponses.
 */
export async function ecrireEtiquette(payload: string): Promise<void> {
  if (!nfcDisponible()) {
    throw new Error('NFC indisponible');
  }
  const Constructeur = (window as unknown as { NDEFReader: ConstructeurNdef }).NDEFReader;
  const lecteur = new Constructeur();
  await lecteur.write({ records: [{ recordType: 'text', data: payload }] });
}
