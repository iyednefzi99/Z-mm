package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Tache ou rappel de l'apiculteur (US-031).
 *
 * <p>Optionnellement rattachee a une ruche et assignee a un agent, avec une
 * echeance qui alimente le calendrier des rappels. Multi-tenant par
 * {@code tenant_id} + RLS, comme les autres entites (cf. migration V7).
 */
@Entity
@Table(name = "tache")
public class Tache extends EntiteTenant {

    @NotBlank
    @Size(max = 200)
    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_id")
    private Ruche ruche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    /**
     * Equipement concerne (SPRINT-27, lot E).
     *
     * <p>Le §13 demandait des « taches recurrentes attachees a un equipement,
     * pas a une ruche ». Exclusif de {@code ruche} dans les faits, sans
     * contrainte pour autant : « reviser l'extracteur avant la recolte de la
     * ruche 12 » est une phrase qui a un sens.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materiel_id")
    private Materiel materiel;

    /**
     * Ce que la tache consomme (SPRINT-33, lot K).
     *
     * <p>La feuille de chargement n'existe que par cette colonne : la tournee
     * etait calculee depuis le SPRINT-10 et le stock avait ses seuils depuis le
     * SPRINT-27, mais rien ne disait ce que « nourrir la 12 » allait prelever.
     * Une table « besoin de chargement » aurait duplique ce que la tache dit
     * deja, et aurait pu en diverger.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consommable_id")
    private Consommable consommable;

    /**
     * Combien, dans l'unite du consommable.
     *
     * <p>Nullable, et le rester importe : « prendre du candi » sans savoir encore
     * combien est une consigne utile, et l'exiger ferait renoncer a la saisie. La
     * feuille de chargement distingue alors « besoin non chiffre » de « zero ».
     */
    @Column(name = "quantite_prevue", precision = 10, scale = 2)
    private java.math.BigDecimal quantitePrevue;

    @Column(name = "echeance")
    private LocalDate echeance;

    @Column(name = "faite", nullable = false)
    private boolean faite;

    // ─── Priorite, categorie, origine (SPRINT-22) ───────────────────────────

    /**
     * Ce qui distingue « commander des cadres gaufres » de « retirer un
     * traitement dont la carence expire demain ». Sans elle, la liste se lit
     * dans l'ordre de saisie, c'est-a-dire dans aucun ordre.
     */
    @NotBlank
    @Pattern(regexp = "basse|normale|haute|critique")
    @Column(name = "priorite", nullable = false, length = 10)
    private String priorite = "normale";

    @Pattern(regexp = "controle|traitement|nourrissement|recolte|materiel"
            + "|elevage|administratif|autre")
    @Column(name = "categorie", length = 20)
    private String categorie;

    /**
     * {@code manuelle} ou {@code regle}.
     *
     * <p>La distinction compte a l'ecran : une tache engendree doit pouvoir se
     * justifier — « proposee par la regle des delais de carence » —, une tache
     * saisie n'a rien a expliquer. Une liste ou les deux se confondent finit par
     * n'etre plus lue.
     */
    @NotBlank
    @Pattern(regexp = "manuelle|regle")
    @Column(name = "origine", nullable = false, length = 10)
    private String origine = "manuelle";

    @Size(max = 40)
    @Column(name = "regle_code", length = 40)
    private String regleCode;

    /**
     * Identite de la tache engendree — {@code carence-retrait:42}.
     *
     * <p>Une regle s'execute a chaque passage : sans cette cle, la meme tache
     * serait recreee a chaque tour jusqu'a noyer la liste. Un index unique
     * partiel ({@code uq_tache_declencheur}) la rend unique par exploitation, et
     * c'est la base qui le garantit — pas la vigilance du moteur.
     */
    @Size(max = 80)
    @Column(name = "cle_declencheur", length = 80)
    private String cleDeclencheur;

    protected Tache() {
        // Requis par JPA.
    }

    public Tache(String libelle) {
        this.libelle = libelle;
    }

    /** Marque la tache comme engendree par une regle, avec de quoi la justifier. */
    public void engendreePar(String regleCode, String cleDeclencheur) {
        this.origine = "regle";
        this.regleCode = regleCode;
        this.cleDeclencheur = cleDeclencheur;
    }

    /** Une tache engendree se justifie a l'ecran ; une tache saisie, non. */
    public boolean engendree() {
        return "regle".equals(origine);
    }

    public String getPriorite() {
        return priorite;
    }

    public void setPriorite(String priorite) {
        this.priorite = priorite;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public String getOrigine() {
        return origine;
    }

    public String getRegleCode() {
        return regleCode;
    }

    public String getCleDeclencheur() {
        return cleDeclencheur;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public void setRuche(Ruche ruche) {
        this.ruche = ruche;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public Materiel getMateriel() {
        return materiel;
    }

    public void setMateriel(Materiel materiel) {
        this.materiel = materiel;
    }

    public Consommable getConsommable() {
        return consommable;
    }

    public void setConsommable(Consommable consommable) {
        this.consommable = consommable;
    }

    public java.math.BigDecimal getQuantitePrevue() {
        return quantitePrevue;
    }

    public void setQuantitePrevue(java.math.BigDecimal quantitePrevue) {
        this.quantitePrevue = quantitePrevue;
    }

    public LocalDate getEcheance() {
        return echeance;
    }

    public void setEcheance(LocalDate echeance) {
        this.echeance = echeance;
    }

    public boolean isFaite() {
        return faite;
    }

    public void setFaite(boolean faite) {
        this.faite = faite;
    }
}
