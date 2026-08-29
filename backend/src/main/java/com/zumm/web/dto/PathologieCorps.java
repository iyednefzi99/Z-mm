package com.zumm.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Une pathologie constatee pendant une visite (SPRINT-20).
 *
 * <p>{@code gravite} vaut « suspectee » par defaut, et c'est une position
 * honnete : au rucher on constate un symptome, on ne pose pas un diagnostic de
 * laboratoire. Presenter la suspicion comme une certitude fausserait toute
 * statistique sanitaire construite dessus.
 */
public record PathologieCorps(
        @NotNull
        @Pattern(regexp = "varroose|loque_americaine|loque_europeenne|nosemose"
                + "|petit_coleoptere|fausse_teigne|frelon_asiatique"
                + "|couvain_sacciforme|mycose|pesticide|autre")
        String pathologie,
        @Pattern(regexp = "suspectee|legere|moderee|severe") String gravite,
        String note) {

    /** Gravite retenue quand l'appelant n'en donne pas : la plus prudente. */
    public String graviteOuDefaut() {
        return gravite == null ? "suspectee" : gravite;
    }
}
