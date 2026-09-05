import { afterEach, describe, expect, it, vi } from 'vitest';
import { etatBluetooth, lireCapteur } from './bluetooth';

/**
 * Lecture directe d'un capteur (SPRINT-31, lot F₂, ADR-014).
 *
 * <p>Ce qui se teste ici sans matériel, c'est exactement ce qui peut se tromper
 * sans se voir : le **décodage**. Une température lue à l'envers donne des
 * milliers de degrés — cela se remarque ; une humidité inversée passerait pour
 * plausible et fausserait une série entière.
 */

interface FenetreBluetooth {
  bluetooth?: unknown;
}

/** Construit une valeur SIG sur deux octets, petit-boutiste. */
const deuxOctets = (valeur: number): DataView => {
  const tampon = new DataView(new ArrayBuffer(2));
  tampon.setInt16(0, valeur, true);
  return tampon;
};

const unOctet = (valeur: number): DataView => {
  const tampon = new DataView(new ArrayBuffer(1));
  tampon.setUint8(0, valeur);
  return tampon;
};

/**
 * Un capteur simulé.
 *
 * @param valeurs par UUID de caractéristique ; une absente lève, comme le fait
 *                un vrai GATT quand la caractéristique n'existe pas
 */
function capteur(valeurs: Record<number, DataView>, nom = 'Ruche-12') {
  const deconnecter = vi.fn();
  const serveur = {
    getPrimaryService: (service: number) =>
      Promise.resolve({
        getCharacteristic: (carac: number) =>
          valeurs[carac] === undefined
            ? Promise.reject(new Error('caractéristique absente'))
            : Promise.resolve({ readValue: () => Promise.resolve(valeurs[carac]) }),
        service,
      }),
  };
  return {
    deconnecter,
    appareil: {
      name: nom,
      gatt: { connect: () => Promise.resolve(serveur), disconnect: deconnecter },
    },
  };
}

const poser = (appareil: unknown) => {
  (window.navigator as unknown as FenetreBluetooth).bluetooth = {
    requestDevice: vi.fn(() => Promise.resolve(appareil)),
  };
};

afterEach(() => {
  delete (window.navigator as unknown as FenetreBluetooth).bluetooth;
});

describe('disponibilité', () => {
  it('dit « absent » là où le navigateur n’expose pas Bluetooth', () => {
    // Safari sur iPhone, Firefox. L'écran l'écrit au lieu d'afficher un bouton
    // inerte : la passerelle reste le chemin de tout le monde.
    expect(etatBluetooth()).toBe('absent');
  });

  it('ne tente rien quand Bluetooth est absent', async () => {
    expect(await lireCapteur()).toBeNull();
  });
});

describe('décodage du profil standard', () => {
  it('lit température, humidité et batterie aux unités du profil SIG', async () => {
    const { appareil } = capteur({
      0x2a6e: deuxOctets(3425), // centièmes de degré
      0x2a6f: deuxOctets(6250), // centièmes de pourcent
      0x2a19: unOctet(78), // pourcent entier
    });
    poser(appareil);

    const lecture = await lireCapteur();

    // Les diviseurs sont ceux de la norme, pas des réglages : 34,25 °C et
    // 62,50 % d'humidité. Se tromper d'un facteur cent donnerait des séries
    // entières fausses sans qu'aucune alerte ne se déclenche.
    expect(lecture).toEqual({
      temperature: 34.25,
      humidite: 62.5,
      batterie: 78,
      nom: 'Ruche-12',
    });
  });

  it('lit une température négative, que l’octet de signe seul permettrait de rater', async () => {
    const { appareil } = capteur({ 0x2a6e: deuxOctets(-450) });
    poser(appareil);

    // −4,50 °C : la caractéristique SIG est un entier SIGNÉ. La lire comme non
    // signée rendrait 651 °C en plein hiver.
    expect((await lireCapteur())?.temperature).toBe(-4.5);
  });

  it('rend null pour une caractéristique absente, sans échouer', async () => {
    const { appareil } = capteur({ 0x2a6e: deuxOctets(3400) });
    poser(appareil);

    const lecture = await lireCapteur();

    // Un capteur de température n'expose pas d'humidité, et le lui reprocher
    // n'aurait aucun sens : l'absence n'est pas une panne.
    expect(lecture?.temperature).toBe(34);
    expect(lecture?.humidite).toBeNull();
    expect(lecture?.batterie).toBeNull();
  });

  it('referme la connexion GATT dans tous les cas', async () => {
    const { appareil, deconnecter } = capteur({ 0x2a6e: deuxOctets(3400) });
    poser(appareil);

    await lireCapteur();

    // Un GATT laissé ouvert empêche tout autre logiciel de lire le même
    // capteur — y compris l'application du fabricant, le lendemain.
    expect(deconnecter).toHaveBeenCalledOnce();
  });
});
