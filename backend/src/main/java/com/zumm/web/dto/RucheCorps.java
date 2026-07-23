package com.zumm.web.dto;

import com.zumm.domain.EtatRuche;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
 */
public record RucheCorps(
        @NotBlank @Size(max = 120) String modele,
        @NotNull Long siteId,
        @NotNull Long fermeId,
        Long agentResponsableId,
        EtatRuche etat,
        @NotEmpty @Valid List<CompartimentCorps> compartiments) {
}
