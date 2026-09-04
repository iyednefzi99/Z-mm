package com.zumm.web.dto;

import com.zumm.domain.EtatRuche;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corps de requete pour creer ou mettre a jour une ruche et sa composition (US-004).
 *
 * @param modele              modele de la ruche, obligatoire
 * @param siteId              site d'emplacement, obligatoire (meme tenant)
 * @param fermeId             ferme proprietaire, obligatoire (meme tenant)
 * @param agentResponsableId  agent responsable, facultatif (meme tenant)
 * @param etat                etat du cycle de vie ; par defaut « creee » si absent
 * @param compartiments       composition : exactement un corps, 0 a 5 hausses
 * @param typeRuche           referentiel (langstroth, dadant, warre...) ; `modele`
 *                            reste le texte libre au-dessous
 * @param couleur             reperage visuel au rucher, avant tout scan
 * @param origine             d'ou vient la colonie : essaim, division, nucleus...
 * @param causeCloture        pourquoi la ruche est fermee ; la base la refuse sur
 *                            une ruche encore active
 */
public record RucheCorps(
        @NotBlank @Size(max = 120) String modele,
        @NotNull Long siteId,
        @NotNull Long fermeId,
        Long agentResponsableId,
        EtatRuche etat,
        @NotEmpty @Valid List<CompartimentCorps> compartiments,
        @Pattern(regexp = "langstroth|dadant|warre|voirnot|top_bar|kenyane|autre") String typeRuche,
        @Pattern(regexp = "blanc|jaune|orange|rouge|vert|bleu|violet|gris|bois") String couleur,
        @Pattern(regexp = "essaim_capture|essaim_achete|division|nucleus|paquet|achat|autre")
        String origine,
        @Pattern(regexp = "morte|fusionnee|vendue|volee|reformee|essaimee|autre")
        String causeCloture,
        /**
         * basse | normale | haute (SPRINT-23). Une ruche souche se traite avant
         * les autres, et la tournee comme les agregats la remontent.
         */
        @Pattern(regexp = "basse|normale|haute") String priorite) {
}
