package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Corps de requete pour enregistrer ou modifier une reine (SPRINT-29, lot D).
 *
 * <p>Aucun champ n'est obligatoire hormis l'origine, et c'est deliberе : on
 * enregistre une reine achetee dont on ignore le greffage, comme une reine
 * d'essaimage dont on ne sait rien du tout. Exiger davantage ferait renoncer a
 * l'enregistrer — et une reine absente du fichier ne figure dans aucun arbre.
 *
 * @param mereId       reine mere, si elle est enregistree. Le service refuse un
 *                     cycle, que la base ne sait verifier que sur un pas
 * @param rucheMereId  colonie dont on a greffe les larves, distincte de la mere
 * @param fournisseur  n'a de sens que pour une reine achetee ; la base le refuse
 *                     sur les autres origines
 * @param ailesClippees {@code null} = on ne sait pas, ce qui n'est pas « non »
 * @param dateFin      fin de regne. Elle borne l'index genetique : sans elle,
 *                     une reine introduite en juillet heriterait de la recolte
 *                     de printemps de la precedente
 */
public record ReineElevageCorps(
        @Size(max = 40) String code,
        Long mereId,
        Long rucheMereId,
        Long serieId,
        Long rucheId,
        @Pattern(regexp = "elevage|achat|essaimage|supersedure|inconnue") String origine,
        @Size(max = 120) String fournisseur,
        @Size(max = 60) String race,
        @Min(2000) @Max(2100) Integer anneeNaissance,
        @Pattern(regexp = "blanc|jaune|rouge|vert|bleu") String couleurMarquage,
        Boolean ailesClippees,
        LocalDate dateGreffage,
        LocalDate dateNaissance,
        LocalDate dateFecondation,
        LocalDate dateIntroduction,
        LocalDate dateFin,
        @Pattern(regexp = "en_service|reserve|remplacee|disparue|morte|vendue") String statut,
        String note) {

    /** L'origine par defaut est l'ignorance, pas l'elevage maison. */
    public String origineOuInconnue() {
        return origine == null ? "inconnue" : origine;
    }

    /** Une reine enregistree est en service, sauf mention contraire. */
    public String statutOuEnService() {
        return statut == null ? "en_service" : statut;
    }
}
