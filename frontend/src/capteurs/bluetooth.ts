/**
 * Lecture directe d'un capteur en Bluetooth (SPRINT-31, lot F₂, [ADR-014]).
 *
 * <p>Ferme la ligne « connexion Bluetooth directe aux capteurs du commerce » du
 * §5 : « Zümm suppose une passerelle qui pousse vers l'API ; HiveSense supprime
 * la passerelle ».
 *
 * <p><strong>Le profil standard, et rien d'inventé.</strong> Ce module parle le
 * profil Bluetooth SIG — *Environmental Sensing* et *Battery Service* —, dont
 * les identifiants sont **normalisés** et non devinés. Tout capteur qui les
 * implémente fonctionne.
 *
 * <p>Ce qu'il ne fait pas : décoder la trame propriétaire d'un fabricant dont
 * personne n'a l'appareil ici. Ce serait deviner une structure de données, et le
 * résultat aurait l'apparence du support sans en avoir la fiabilité — le même
 * refus qu'au SPRINT-27 pour le réfractomètre.
 *
 * <p><strong>Web Bluetooth est absent d'iOS Safari</strong>, donc de la moitié
 * du parc. L'écran l'écrit au lieu d'afficher un bouton inerte : la passerelle
 * reste le chemin de tout le monde, le Bluetooth direct est un raccourci pour
 * ceux qui l'ont.
 */

/** Service *Environmental Sensing* (SIG 0x181A). */
const SERVICE_ENVIRONNEMENT = 0x181a;
/** Caractéristique Temperature (0x2A6E), en centièmes de degré, entier signé. */
const CARAC_TEMPERATURE = 0x2a6e;
/** Caractéristique Humidity (0x2A6F), en centièmes de pourcent, entier non signé. */
const CARAC_HUMIDITE = 0x2a6f;
/** Service *Battery* (SIG 0x180F) et sa caractéristique Battery Level (0x2A19). */
const SERVICE_BATTERIE = 0x180f;
const CARAC_BATTERIE = 0x2a19;

/** Une lecture faite sur l'appareil, prête à être poussée vers l'API. */
export interface LectureCapteur {
  temperature: number | null;
  humidite: number | null;
  batterie: number | null;
  /** Nom annoncé par le capteur, pour que l'utilisateur sache ce qu'il a lu. */
  nom: string;
}

/** Ce que ce navigateur permet. */
export type EtatBluetooth = 'absent' | 'disponible';

/**
 * Le navigateur expose-t-il Web Bluetooth ?
 *
 * <p>Absent sur iOS Safari, et sur Firefox. Le dire vaut mieux que proposer un
 * bouton qui ne réagit pas — c'est la même règle que pour la dictée.
 */
export function etatBluetooth(): EtatBluetooth {
  return typeof navigator !== 'undefined' && 'bluetooth' in navigator
    ? 'disponible'
    : 'absent';
}

/**
 * Décode une valeur entière petit-boutiste sur deux octets.
 *
 * <p>Le profil SIG impose l'ordre des octets ; le lire à l'envers donnerait des
 * températures de plusieurs milliers de degrés, ce qui se voit — mais une
 * humidité inversée passerait pour plausible.
 */
function entier16(vue: DataView, signe: boolean): number {
  return signe ? vue.getInt16(0, true) : vue.getUint16(0, true);
}

interface CaracteristiqueBle {
  readValue: () => Promise<DataView>;
}

interface ServiceBle {
  getCharacteristic: (uuid: number) => Promise<CaracteristiqueBle>;
}

interface ServeurBle {
  getPrimaryService: (uuid: number) => Promise<ServiceBle>;
}

interface AppareilBle {
  name?: string;
  gatt?: { connect: () => Promise<ServeurBle>; disconnect: () => void };
}

interface NavigateurBluetooth {
  bluetooth: {
    requestDevice: (options: {
      filters?: { services: number[] }[];
      optionalServices?: number[];
    }) => Promise<AppareilBle>;
  };
}

/** Lit une caractéristique, ou rend `null` si le capteur ne l'expose pas. */
async function lire(
  serveur: ServeurBle,
  service: number,
  caracteristique: number,
  decoder: (vue: DataView) => number,
): Promise<number | null> {
  try {
    const s = await serveur.getPrimaryService(service);
    const c = await s.getCharacteristic(caracteristique);
    return decoder(await c.readValue());
  } catch {
    // Caractéristique absente : ce n'est pas une panne. Un capteur de poids
    // n'expose pas d'humidité, et le lui reprocher n'aurait aucun sens.
    return null;
  }
}

/**
 * Demande un capteur à l'utilisateur, s'y connecte, et lit ce qu'il expose.
 *
 * <p>Le choix de l'appareil passe **obligatoirement** par la boîte de dialogue
 * du navigateur : une page ne peut pas balayer les alentours toute seule, et
 * c'est très bien ainsi — la liste des capteurs à portée dirait où l'on est.
 *
 * <p>La connexion est refermée dans tous les cas : un GATT laissé ouvert
 * empêche tout autre logiciel de lire le même capteur.
 */
export async function lireCapteur(): Promise<LectureCapteur | null> {
  if (etatBluetooth() === 'absent') {
    return null;
  }
  const nav = navigator as unknown as NavigateurBluetooth;
  const appareil = await nav.bluetooth.requestDevice({
    filters: [{ services: [SERVICE_ENVIRONNEMENT] }],
    optionalServices: [SERVICE_BATTERIE],
  });
  if (appareil.gatt === undefined) {
    return null;
  }
  const serveur = await appareil.gatt.connect();
  try {
    const temperature = await lire(
      serveur,
      SERVICE_ENVIRONNEMENT,
      CARAC_TEMPERATURE,
      (vue) => entier16(vue, true) / 100,
    );
    const humidite = await lire(
      serveur,
      SERVICE_ENVIRONNEMENT,
      CARAC_HUMIDITE,
      (vue) => entier16(vue, false) / 100,
    );
    const batterie = await lire(
      serveur,
      SERVICE_BATTERIE,
      CARAC_BATTERIE,
      (vue) => vue.getUint8(0),
    );
    return { temperature, humidite, batterie, nom: appareil.name ?? '—' };
  } finally {
    appareil.gatt.disconnect();
  }
}
