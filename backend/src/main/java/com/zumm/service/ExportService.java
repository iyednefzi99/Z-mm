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

    /** Echappement RFC 4180 pour le CSV ; en TXT on neutralise tabulations et retours. */
    private static String echapper(String champ, Format format) {
        if (champ == null) {
            return "";
        }
        if (format == Format.TXT) {
            return champ.replace("\t", " ").replace("\r", " ").replace("\n", " ");
        }
        if (champ.contains(",") || champ.contains("\"") || champ.contains("\n") || champ.contains("\r")) {
            return '"' + champ.replace("\"", "\"\"") + '"';
        }
        return champ;
    }

    private static String texte(Object valeur) {
        return valeur == null ? null : String.valueOf(valeur);
    }
}
