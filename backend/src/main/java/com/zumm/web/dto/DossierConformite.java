package com.zumm.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Dossier de conformite, pour un controle (SPRINT-29, lot D).
 *
 * <p>Ferme la ligne « rapports de conformité / certification bio » du §7, que
 * HiveBook et APIGO produisent.
 *
 * <p><strong>Zumm ne certifie rien, et ce document le dit.</strong> Une
 * application qui declarerait une exploitation « conforme bio » emettrait une
 * affirmation reglementaire qu'elle n'a aucun moyen de tenir : la certification
 * est prononcee par un organisme agree, sur piece et sur place. Ce dossier
 * RASSEMBLE ce qu'un controleur demande, et nomme ce qu'il ne peut pas verifier.
 *
 * <p>Trois etats seulement, et le troisieme est le plus important :
 * <ul>
 *   <li>{@code verifie} — la donnee est dans le systeme et satisfait la regle ;
 *   <li>{@code signale} — la donnee est dans le systeme et l'enfreint ;
 *   <li>{@code a_justifier} — le systeme ne sait pas. C'est le cas de tout ce
 *       qui depend d'une facture ou d'une attestation : l'origine biologique du
 *       sucre, celle de la cire gaufree, le statut du foncier. Les compter comme
 *       conformes serait un mensonge par omission.
 * </ul>
 *
 * @param avertissement phrase a afficher et a imprimer, sans exception
 */
public record DossierConformite(
        LocalDate debut,
        LocalDate fin,
        String avertissement,
        List<PointControle> points) {

    /**
     * Un point de controle.
     *
     * @param statut  verifie | signale | a_justifier
     * @param detail  ce qui fonde le statut, en clair — jamais un code
     * @param nombre  nombre d'elements concernes, pour que le controleur sache
     *                l'ampleur avant d'ouvrir le registre
     */
    public record PointControle(String code, String statut, String detail, int nombre) {
    }
}
