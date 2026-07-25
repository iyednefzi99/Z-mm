package com.zumm.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Grappe de sites geographiquement proches (US-045, SPRINT-10).
 *
 * <p>Produite par {@code ST_ClusterDBSCAN} sur la colonne generee {@code site.geog} :
 * l'apiculteur raisonne par zone d'exploitation plutot que site par site. Un site
 * isole n'est pas perdu, il forme une grappe d'un seul membre.
 *
 * @param numero          numero de grappe, attribue par taille decroissante (1 = la plus grande)
 * @param latitudeCentre  latitude du centroide de la grappe
 * @param longitudeCentre longitude du centroide de la grappe
 * @param nombreSites     nombre de sites membres
 * @param nombreRuches    nombre de ruches cumule sur ces sites
 * @param sites           sites membres, tries par identifiant
 */
public record GrappeSites(
        int numero,
        BigDecimal latitudeCentre,
        BigDecimal longitudeCentre,
        int nombreSites,
        long nombreRuches,
        List<SiteReponse> sites) {

    /** Vrai lorsque la grappe ne compte qu'un site (bruit DBSCAN). */
    public boolean isolee() {
        return nombreSites == 1;
    }
}
