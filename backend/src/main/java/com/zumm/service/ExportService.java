package com.zumm.service;

import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Export tabulaire des donnees maitrisees par l'utilisateur (US-027).
 *
 * <p>Deux formats delimites : {@code csv} (separateur virgule, echappement RFC 4180)
 * et {@code txt} (separateur tabulation). Les donnees restent celles du tenant
 * courant (@TenantId + RLS). L'export est genere en memoire : volumes maitrises a
 * ce stade du produit.
 */
@Service
@Transactional(readOnly = true)
public class ExportService {

    /** Format d'export demande (US-027). */
    public enum Format {
        CSV(",", "text/csv"),
        TXT("\t", "text/plain");

        private final String separateur;
        private final String typeMime;

        Format(String separateur, String typeMime) {
            this.separateur = separateur;
            this.typeMime = typeMime;
        }

        public String typeMime() {
            return typeMime;
        }

        public static Format depuis(String valeur) {
            return valeur != null && valeur.equalsIgnoreCase("txt") ? TXT : CSV;
        }
    }

    private final VisiteRepository visites;
    private final RucheRepository ruches;

    public ExportService(VisiteRepository visites, RucheRepository ruches) {
        this.visites = visites;
        this.ruches = ruches;
    }

    /** Export des visites et de leur rapport (US-009). */
    public String exporterVisites(Format format) {
        StringBuilder sb = new StringBuilder();
        ligne(sb, format, "id", "date", "heure", "ruche", "agent", "raison",
                "etat_sante", "effectif", "productivite", "constatations");
        for (Visite v : visites.findAllByOrderByDateVisiteAsc()) {
            ligne(sb, format,
                    texte(v.getId()),
                    texte(v.getDateVisite()),
                    texte(v.getHeureVisite()),
                    v.getRuche().getModele(),
                    v.getAgent().getNom(),
                    texte(v.getRaison() == null ? null : v.getRaison().enBase()),
                    texte(v.getEtatSante() == null ? null : v.getEtatSante().enBase()),
                    texte(v.getEffectifQualitatif() == null ? null : v.getEffectifQualitatif().enBase()),
                    texte(v.getProductivite()),
                    texte(v.getConstatations()));
        }
        return sb.toString();
    }

    /** Export du parc de ruches et de leur composition (US-004). */
    public String exporterRuches(Format format) {
        StringBuilder sb = new StringBuilder();
        ligne(sb, format, "id", "modele", "site", "ferme", "etat", "nb_compartiments");
        for (Ruche r : ruches.findAll()) {
            ligne(sb, format,
                    texte(r.getId()),
                    r.getModele(),
                    r.getSite().getNom(),
                    r.getFerme().getNom(),
                    texte(r.getEtat().enBase()),
                    texte(r.getCompartiments().size()));
        }
        return sb.toString();
    }

    private static void ligne(StringBuilder sb, Format format, String... champs) {
        for (int i = 0; i < champs.length; i++) {
            if (i > 0) {
                sb.append(format.separateur);
            }
            sb.append(echapper(champs[i], format));
        }
        sb.append("\r\n");
    }

    /**
     * Caracteres qui, en tete de cellule, font interpreter le contenu comme une
     * FORMULE par Excel, LibreOffice et Google Sheets (CWE-1236, « CSV injection »).
     */
    private static final String AMORCES_DE_FORMULE = "=+-@\t\r";

    /**
     * Neutralise l'interpretation d'une cellule comme formule.
     *
     * <p>L'echappement RFC 4180 protege l'ANALYSE du fichier, pas son OUVERTURE :
     * une constatation de visite saisie {@code =cmd|'/c calc'!A1} reste une formule
     * valide une fois le champ correctement guillemete. Le tableur execute alors du
     * contenu venu d'un utilisateur — et les exports de Zumm circulent par courriel
     * entre exploitations. La parade retenue est le prefixe apostrophe : la cellule
     * s'affiche telle quelle et n'est plus evaluee.
     */
    private static String neutraliserFormule(String champ) {
        if (champ.isEmpty() || AMORCES_DE_FORMULE.indexOf(champ.charAt(0)) < 0) {
            return champ;
        }
        // Un nombre negatif commence lui aussi par `-` : le prefixer le
        // transformerait en texte et fausserait tout calcul en aval. Seules les
        // amorces qui ne sont PAS un nombre sont neutralisees.
        if (estNombre(champ)) {
            return champ;
        }
        return "'" + champ;
    }

    private static boolean estNombre(String valeur) {
        try {
            new java.math.BigDecimal(valeur);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Echappement RFC 4180 pour le CSV ; en TXT on neutralise tabulations et retours. */
    private static String echapper(String champ, Format format) {
        if (champ == null) {
            return "";
        }
        String sur = neutraliserFormule(champ);
        if (format == Format.TXT) {
            return sur.replace("\t", " ").replace("\r", " ").replace("\n", " ");
        }
        if (sur.contains(",") || sur.contains("\"") || sur.contains("\n") || sur.contains("\r")) {
            return '"' + sur.replace("\"", "\"\"") + '"';
        }
        return sur;
    }

    private static String texte(Object valeur) {
        return valeur == null ? null : String.valueOf(valeur);
    }
}
