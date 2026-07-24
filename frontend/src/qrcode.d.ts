/**
 * Déclaration de types minimale pour `qrcode` (US-033) : seule `toDataURL` est
 * utilisée (rendu d'un QR code en image data-URL). @types/qrcode n'étant pas
 * disponible hors-ligne, on déclare ici la surface strictement nécessaire.
 */
declare module 'qrcode' {
  export function toDataURL(text: string, options?: Record<string, unknown>): Promise<string>;
  const _default: { toDataURL: typeof toDataURL };
  export default _default;
}
