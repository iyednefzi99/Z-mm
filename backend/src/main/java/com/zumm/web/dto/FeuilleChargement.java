package com.zumm.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Ce qu'il faut charger dans le vehicule avant de partir (SPRINT-33, lot K).
 *
 * <p>Ferme la moitie « logistique et chaine d'approvisionnement » de la ligne
 * « coordination d'equipes terrain, logistique multi-sites » (§12), restee 🟡
 * depuis le SPRINT-23. Le manque etait concret et connu de tout apiculteur : on
 * arrive au troisieme rucher et il manque le candi.
 *
 * <p>Les trois pieces existaient separement — la tournee depuis le SPRINT-10, le
 * stock a seuils depuis le SPRINT-27, les taches depuis le SPRINT-05. Ce qui
 * manquait etait le LIEN : ce que telle tache consomme.
 *
 * <p><strong>Deux lectures, et il faut les deux.</strong> Par etape, pour savoir
 * quoi deposer ou ; consolidee, pour savoir quoi charger. Un apiculteur qui n'a
 * que la premiere additionne de tete quinze lignes sur le pas de la porte ; un
 * apiculteur qui n'a que la seconde arrive au rucher sans savoir ce qui y va.
 *
 * <p><strong>Le manque est NOMME, il n'est pas corrige.</strong> Quand le stock
 * ne couvre pas le besoin, la feuille le dit et s'arrete la : decider a la place
 * de l'apiculteur quelle ruche sauter serait une decision d'exploitation, et
 * elle ne se prend pas dans un calcul.
 *
 * @param etapes    sites dans l'ordre de la tournee, avec ce qui y est attendu
 * @param besoins   totaux par consommable, avec l'etat du stock
 * @param manquants nombre de consommables dont le stock ne couvre pas le besoin
 */
public record FeuilleChargement(
        Long agentId,
        String agentNom,
        LocalDate date,
        int nombreSites,
        List<EtapeChargement> etapes,
        List<BesoinConsommable> besoins,
        int manquants) {

    /**
     * Une etape de la tournee, et ce qu'il y a a y faire.
     *
     * @param taches taches ouvertes echues au plus tard ce jour-la sur les ruches
     *               de ce rucher. Les taches SANS consommable y figurent aussi :
     *               une feuille de chargement qui n'afficherait que le materiel
     *               laisserait croire qu'il n'y a rien d'autre a faire sur place
     */
    public record EtapeChargement(
            int ordre,
            Long siteId,
            String siteNom,
            int nombreVisites,
            List<String> taches,
            List<BesoinConsommable> besoins) {
    }

    /**
     * Le besoin d'un consommable, et ce que le stock en dit.
     *
     * @param requis    somme des quantites prevues par les taches retenues.
     *                  {@code null} quand aucune tache n'a chiffre sa
     *                  consommation — « prendre du candi » sans quantite reste
     *                  une consigne utile, et l'afficher a zero serait faux
     * @param enStock   quantite au moment de la lecture
     * @param suffisant faux uniquement quand un requis CHIFFRE depasse le stock.
     *                  Un besoin non chiffre ne declare jamais un manque : on ne
     *                  peut pas manquer d'une quantite qu'on n'a pas exprimee
     */
    public record BesoinConsommable(
            Long consommableId,
            String libelle,
            String unite,
            java.math.BigDecimal requis,
            java.math.BigDecimal enStock,
            boolean suffisant) {
    }
}
