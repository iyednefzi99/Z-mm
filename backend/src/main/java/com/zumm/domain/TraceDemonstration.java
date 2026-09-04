package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ce que le chargement de demonstration a cree (SPRINT-25, lot J).
 *
 * <p>Une ligne par objet cree. La purge ne supprime que ces lignes-la : jamais
 * une donnee que l'utilisateur aurait saisie entre-temps, meme si elle porte le
 * meme nom. Deviner par le nom — « tout ce qui commence par Demo » — aurait
 * detruit le rucher d'un apiculteur qui aurait eu le tort d'appeler le sien
 * « Demonstration ».
 *
 * <p>{@code ordre} porte la sequence de purge, du plus dependant au moins
 * dependant : {@code ruche} casse en cascade ses visites, taches, recoltes et
 * mesures, mais {@code site} et {@code ferme} sont en {@code ON DELETE
 * RESTRICT}. Laisser l'ordre au hasard d'un tri par identifiant ferait echouer
 * la purge une fois sur deux.
 */
@Entity
@Table(name = "jeu_demonstration")
public class TraceDemonstration extends EntiteTenant {

    /** Ordre de purge. Plus le rang est bas, plus tot la ligne est supprimee. */
    public enum Entite {
        RUCHE("ruche", 1),
        SITE("site", 2),
        AGENT("agent", 3),
        FERME("ferme", 4),
        FERMIER("fermier", 5);

        private final String enBase;
        private final int ordre;

        Entite(String enBase, int ordre) {
            this.enBase = enBase;
            this.ordre = ordre;
        }

        public String enBase() {
            return enBase;
        }

        public int ordre() {
            return ordre;
        }

        public static Entite de(String valeur) {
            for (Entite entite : values()) {
                if (entite.enBase.equals(valeur)) {
                    return entite;
                }
            }
            // Une valeur inconnue ne doit pas faire echouer la purge : elle se
            // voit dans les donnees, ce qui vaut mieux qu'une exception au
            // moment precis ou l'utilisateur essaie de nettoyer.
            return null;
        }
    }

    @NotBlank
    @Size(max = 40)
    @Column(name = "entite", nullable = false, length = 40)
    private String entite;

    @NotNull
    @Column(name = "entite_id", nullable = false)
    private Long entiteId;

    @Column(name = "ordre", nullable = false)
    private int ordre;

    protected TraceDemonstration() {
        // JPA
    }

    public TraceDemonstration(Entite entite, Long entiteId) {
        this.entite = entite.enBase();
        this.entiteId = entiteId;
        this.ordre = entite.ordre();
    }

    public String getEntite() {
        return entite;
    }

    public Long getEntiteId() {
        return entiteId;
    }

    public int getOrdre() {
        return ordre;
    }
}
