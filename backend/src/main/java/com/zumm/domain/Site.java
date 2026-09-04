package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Site — emplacement d'apiculture geolocalise (US-003).
 *
 * <p>La geolocalisation est stockee en degres decimaux ; la colonne PostGIS
 * derivee {@code geog} (migration V2) n'est pas mappee ici : elle est calculee et
 * indexee par la base pour les requetes spatiales, et n'a pas a transiter par JPA.
 *
 * <p>Les bornes de coordonnees et l'ordre des dates de cycle de vie (US-006) sont
 * verifies a la fois par Bean Validation (rejet precoce, message clair) et par des
 * contraintes {@code CHECK} en base (garantie ultime).
 */
@Entity
@Table(name = "site")
public class Site extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "nom", nullable = false, length = 120)
    private String nom;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ferme_id", nullable = false)
    private Ferme ferme;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @DecimalMin("-500.0")
    @DecimalMax("9000.0")
    @Column(name = "altitude", precision = 7, scale = 2)
    private BigDecimal altitude;

    @NotNull
    @Column(name = "date_mise_en_oeuvre", nullable = false)
    private LocalDate dateMiseEnOeuvre;

    @Column(name = "date_demenagement")
    private LocalDate dateDemenagement;

    @Column(name = "date_cloture")
    private LocalDate dateCloture;

    // ─── Identite postale et caractere du rucher (SPRINT-21) ────────────────

    /**
     * Adresse postale du rucher.
     *
     * <p><strong>Aussi sensible que la position, et meme davantage</strong> : un
     * couple de coordonnees arrondi a deux decimales situe un rucher au
     * kilometre, une rue le situe au portail. Elle sort donc filtree par
     * {@code PolitiquePositions}, au meme titre que la latitude — un DTO qui la
     * porterait sans passer par la politique rouvrirait le trou que l'arrondi
     * ferme.
     */
    @Size(max = 160)
    @Column(name = "adresse_rue", length = 160)
    private String adresseRue;

    @Size(max = 12)
    @Column(name = "code_postal", length = 12)
    private String codePostal;

    @Size(max = 80)
    @Column(name = "ville", length = 80)
    private String ville;

    /**
     * Pays au format ISO 3166-1 alpha-2 (FR, TN, MA...).
     *
     * <p>Un code et non un libelle : « Tunisie / Tunisia / تونس » n'est pas une
     * donnee mais un affichage, et le stocker en toutes lettres aurait rendu le
     * champ intraduisible dans un produit trilingue.
     */
    @Pattern(regexp = "[A-Z]{2}")
    @Column(name = "pays", length = 2)
    private String pays;

    /**
     * Type de rucher : ce qui commande la lecture de tout le reste. Un rucher de
     * fecondation ne se juge pas au poids, un rucher de transhumance n'a pas
     * vocation a rester en place.
     */
    @Pattern(regexp = "sedentaire|transhumance|fecondation|elevage|conservatoire|autre")
    @Column(name = "type_site", length = 15)
    private String typeSite;

    /** Exposition dominante — elle explique un ecart de developpement entre deux ruchers voisins. */
    @Pattern(regexp = "nord|nord_est|est|sud_est|sud|sud_ouest|ouest|nord_ouest")
    @Column(name = "exposition", length = 10)
    private String exposition;

    /**
     * Couverture mobile constatee sur place (SPRINT-24).
     *
     * <p>Deduite d'un conseil qu'Onibi adresse a ses utilisateurs — « verifiez la
     * couverture reseau du site avant d'installer ». C'est un attribut du LIEU au
     * meme titre que l'exposition : il ne se mesure pas, il se constate, et il
     * conditionne ce qu'on peut y deployer.
     *
     * <p>Nullable a dessein : {@code inconnue} est la reponse honnete tant que
     * personne n'y est alle avec un telephone, et un defaut a « correcte »
     * ferait partir un apiculteur sans emport sur un rucher en zone blanche.
     */
    @Pattern(regexp = "aucune|faible|correcte|bonne")
    @Column(name = "couverture_reseau", length = 10)
    private String couvertureReseau;


    /**
     * Rucher strategique ou secondaire (SPRINT-23).
     *
     * <p>Independante de celle des ruches qu'il porte : un rucher strategique peut contenir une
     * ruche ordinaire, et une ruche souche vivre dans un rucher secondaire. Les
     * deduire l'une de l'autre demanderait une regle d'heritage que personne n'a
     * demandee, et qui serait fausse dans les deux sens.
     *
     * <p>Trois niveaux et non quatre, contrairement a {@code Tache} : une tache
     * se classe dans une journee, un rucher dans une saison — et au-dela de trois
     * rangs, personne ne fait la difference.
     */
    @NotBlank
    @Pattern(regexp = "basse|normale|haute")
    @Column(name = "priorite", nullable = false, length = 10)
    private String priorite = "normale";

    protected Site() {
        // Requis par JPA.
    }

    public Site(String nom, Ferme ferme, BigDecimal latitude, BigDecimal longitude,
                LocalDate dateMiseEnOeuvre) {
        this.nom = nom;
        this.ferme = ferme;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dateMiseEnOeuvre = dateMiseEnOeuvre;
    }

public String getPriorite() {
        return priorite;
    }

    public void setPriorite(String priorite) {
        this.priorite = priorite;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Ferme getFerme() {
        return ferme;
    }

    public void setFerme(Ferme ferme) {
        this.ferme = ferme;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getAltitude() {
        return altitude;
    }

    public void setAltitude(BigDecimal altitude) {
        this.altitude = altitude;
    }

    public LocalDate getDateMiseEnOeuvre() {
        return dateMiseEnOeuvre;
    }

    public void setDateMiseEnOeuvre(LocalDate dateMiseEnOeuvre) {
        this.dateMiseEnOeuvre = dateMiseEnOeuvre;
    }

    public LocalDate getDateDemenagement() {
        return dateDemenagement;
    }

    public void setDateDemenagement(LocalDate dateDemenagement) {
        this.dateDemenagement = dateDemenagement;
    }

    public LocalDate getDateCloture() {
        return dateCloture;
    }

    public void setDateCloture(LocalDate dateCloture) {
        this.dateCloture = dateCloture;
    }

    public String getAdresseRue() {
        return adresseRue;
    }

    public void setAdresseRue(String adresseRue) {
        this.adresseRue = adresseRue;
    }

    public String getCodePostal() {
        return codePostal;
    }

    public void setCodePostal(String codePostal) {
        this.codePostal = codePostal;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getPays() {
        return pays;
    }

    public void setPays(String pays) {
        this.pays = pays;
    }

    public String getTypeSite() {
        return typeSite;
    }

    public void setTypeSite(String typeSite) {
        this.typeSite = typeSite;
    }

    public String getExposition() {
        return exposition;
    }

    public String getCouvertureReseau() {
        return couvertureReseau;
    }

    public void setCouvertureReseau(String couvertureReseau) {
        this.couvertureReseau = couvertureReseau;
    }

    public void setExposition(String exposition) {
        this.exposition = exposition;
    }
}
