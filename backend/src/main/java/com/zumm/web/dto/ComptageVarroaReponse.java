package com.zumm.web.dto;

import com.zumm.domain.ComptageVarroa;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'un comptage de varroa (SPRINT-20).
 *
 * <p>Le taux n'est pas stocke — il n'a pas la meme unite selon la methode. Il est
 * calcule a la lecture, et l'unite VOYAGE AVEC LUI : un client qui recevrait
 * « 3.5 » sans savoir s'il s'agit de varroas par jour ou pour cent abeilles
 * afficherait un chiffre qui ne veut rien dire.
 *
 * @param taux      valeur calculee, ou {@code null} si le denominateur manque
 * @param tauxUnite {@code varroas_par_jour} ou {@code pour_cent_abeilles}
 * @param verdict   {@code faible}, {@code surveiller} ou {@code traiter} — lu
 *                  contre le seuil de la methode
 */
public record ComptageVarroaReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        Long agentId,
        String agentNom,
        Long visiteId,
        LocalDate dateComptage,
        String methode,
        Integer varroasComptes,
        Integer abeillesEchantillon,
        Integer joursExposition,
        BigDecimal taux,
        String tauxUnite,
        String verdict,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static ComptageVarroaReponse de(ComptageVarroa c, BigDecimal taux, String verdict) {
        return new ComptageVarroaReponse(
                c.getId(),
                c.getRuche().getId(),
                c.getRuche().getModele(),
                c.getAgent().getId(),
                c.getAgent().getNom(),
                c.getVisite() == null ? null : c.getVisite().getId(),
                c.getDateComptage(),
                c.getMethode(),
                c.getVarroasComptes(),
                c.getAbeillesEchantillon(),
                c.getJoursExposition(),
                taux,
                c.parLange() ? "varroas_par_jour" : "pour_cent_abeilles",
                verdict,
                c.getNote(),
                c.getCreeLe(),
                c.getMajLe());
    }
}
