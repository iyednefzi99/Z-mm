package com.zumm.web.dto;

import com.zumm.domain.Visite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Observations structurees d'une visite (SPRINT-20).
 *
 * <p><strong>Un objet imbrique plutot que quinze champs de plus sur
 * {@link VisiteCorps}.</strong> Le corps de visite en portait deja quatorze ;
 * les fondre aurait produit un enregistrement de trente champs ou plus rien ne
 * se lit, et une grille de saisie ou l'apiculteur ne distingue plus le
 * signalement du releve. Le regroupement dit aussi ce qu'il est : la grille
 * d'inspection, prise ou laissee d'un bloc.
 *
 * <p>Tous les champs sont facultatifs — voir la javadoc des colonnes
 * correspondantes sur {@link Visite} : une visite eclair ne remplit rien, et
 * exiger la grille complete ferait sauter la saisie plutot que la completer.
 *
 * @param couvainOeufs         oeufs vus : le signe le plus sur d'une ponte recente
 * @param motifPonte           compact / lacunaire / irregulier / absent
 * @param cellulesRoyales      nombre de cellules royales observees
 * @param cellulesRoyalesCause essaimage / supersedure / urgence — la donnee actionnable
 * @param temperament          doux / normal / agressif
 */
public record ObservationVisite(
        Boolean couvainOeufs,
        Boolean couvainLarves,
        Boolean couvainOpercule,
        @Pattern(regexp = "compact|lacunaire|irregulier|absent") String motifPonte,
        Boolean reineVue,
        @Min(0) Integer cellulesRoyales,
        @Pattern(regexp = "essaimage|supersedure|urgence") String cellulesRoyalesCause,
        @Min(0) @Max(40) Integer cadresCouvain,
        @Min(0) @Max(40) Integer cadresMiel,
        @Min(0) @Max(40) Integer cadresPollen,
        @Pattern(regexp = "doux|normal|agressif") String temperament) {

    /**
     * Extrait la grille d'une visite, ou {@code null} si rien n'a ete coche.
     *
     * <p>Renvoyer un objet entierement vide plutot que {@code null} ferait
     * croire a une grille remplie de « non » la ou personne n'a rien observe —
     * la difference compte pour toute statistique construite dessus.
     */
    public static ObservationVisite de(Visite v) {
        ObservationVisite o = new ObservationVisite(
                v.getCouvainOeufs(),
                v.getCouvainLarves(),
                v.getCouvainOpercule(),
                v.getMotifPonte(),
                v.getReineVue(),
                v.getCellulesRoyales(),
                v.getCellulesRoyalesCause(),
                v.getCadresCouvain(),
                v.getCadresMiel(),
                v.getCadresPollen(),
                v.getTemperament());
        return o.estVide() ? null : o;
    }

    /** Aucune case cochee : la grille n'a pas ete remplie. */
    public boolean estVide() {
        return couvainOeufs == null && couvainLarves == null && couvainOpercule == null
                && motifPonte == null && reineVue == null && cellulesRoyales == null
                && cellulesRoyalesCause == null && cadresCouvain == null && cadresMiel == null
                && cadresPollen == null && temperament == null;
    }

    /** Reporte la grille sur l'entite. */
    public void appliquerA(Visite v) {
        v.setCouvainOeufs(couvainOeufs);
        v.setCouvainLarves(couvainLarves);
        v.setCouvainOpercule(couvainOpercule);
        v.setMotifPonte(motifPonte);
        v.setReineVue(reineVue);
        v.setCellulesRoyales(cellulesRoyales);
        v.setCellulesRoyalesCause(cellulesRoyalesCause);
        v.setCadresCouvain(cadresCouvain);
        v.setCadresMiel(cadresMiel);
        v.setCadresPollen(cadresPollen);
        v.setTemperament(temperament);
    }
}
