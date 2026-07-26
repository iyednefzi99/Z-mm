package com.zumm.securite;

import com.zumm.web.dto.SiteReponse;
import java.math.BigDecimal;

/**
 * Politique d'exposition des positions de ruchers (US-003).
 *
 * <p>La localisation exacte d'un rucher est la donnee la plus sensible du produit :
 * le vol de ruches est le premier sinistre du metier, et une API qui rend
 * `latitude`/`longitude` au metre pres a tout porteur de jeton fabrique une carte
 * au tresor. Le cahier prevoit un arrondi pour les profils non proprietaires
 * (seuil {@code arrondi_degres_public} de {@code ConfigZumm.ini}).
 *
 * <p>Abstraction volontaire (inversion des dependances) : le service metier ne sait
 * pas COMMENT la decision est prise — role, affectation d'agent, consentement du
 * fermier — il sait seulement qu'une position sort filtree. Faire evoluer la regle
 * n'oblige a toucher aucun service.
 *
 * <p><strong>Perimetre.</strong> Le masque s'applique aux vues d'ENSEMBLE — liste
 * des sites, grappes, voisins, meteo — c'est-a-dire a ce qui, exfiltre d'un coup,
 * constitue une carte des ruchers. Il ne s'applique PAS a la tournee du jour
 * ({@code EtapeTournee}) : un agent doit pouvoir se rendre sur les sites qui lui
 * sont assignes, et cette vue est bornee a un agent, une date et quelques sites.
 * C'est la difference entre « en savoir assez pour travailler » et « pouvoir tout
 * moissonner ».

 * <p>Depuis l'US-057, ce masque n'est plus seul : la portee par affectation
 * (migration V16) empeche un agent d'ENUMERER les sites qui ne portent aucune de
 * ses ruches. Les deux se completent — l'une limite ce qu'on lit d'un site,
 * l'autre limite quels sites existent pour l'appelant.
 */
public interface PolitiquePositions {

    /** Renvoie la vue du site autorisee a l'appelant courant. */
    SiteReponse masquer(SiteReponse site);

    /** Applique la meme regle a un couple de coordonnees isole (centroide, trace). */
    BigDecimal[] masquer(BigDecimal latitude, BigDecimal longitude);

    /** L'appelant courant a-t-il droit a la position exacte ? */
    boolean positionExacteAutorisee();
}
