/**
 * Paquet de fonctionnalités Motion, isolé dans son propre module pour que Rollup
 * puisse le sortir du bundle initial (cf. `MouvementLeger`).
 *
 * <p>`domMax` et non `domAnimation` : `domAnimation` ne sait pas glisser
 * (`drag`) ni animer une transition de disposition (`layout`), qui sont les deux
 * raisons d'avoir pris Motion plutôt que du CSS. Il pèse en revanche ~13 ko de
 * plus — d'où le chargement paresseux, qui reporte ce poids au premier écran
 * réellement animé au lieu de le facturer à l'ouverture de la PWA.
 */
export { domMax as default } from 'motion/react';
