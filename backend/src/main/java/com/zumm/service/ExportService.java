package com.zumm.service;

import com.zumm.domain.Consommable;
import com.zumm.domain.Depense;
import com.zumm.domain.LotConditionnement;
import com.zumm.domain.Materiel;
import com.zumm.domain.Mesure;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.Tache;
import com.zumm.domain.Traitement;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.ConsommableRepository;
import com.zumm.repository.DepenseRepository;
import com.zumm.repository.LotConditionnementRepository;
import com.zumm.repository.MaterielRepository;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.NourrissementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Export tabulaire des données de l'exploitation (US-027, étendu au SPRINT-27).
 *
 * <p>Au SPRINT-09, deux entités seulement s'exportaient — visites et ruches. Le
 * §7 le relevait sans ménagement : « deux entités exportables sur dix-neuf ; les
 * douze catalogues promettent tous l'export intégral ». Le lot E porte le compte
 * à <strong>quatorze</strong>, et ajoute le XLSX au CSV et au texte tabulé.
 *
 * <p><strong>Le refactor qui rend cela tenable.</strong> Chaque ressource produit
 * désormais une <em>grille</em> — une liste de lignes de chaînes, en-tête
 * compris — et le rendu est fait ensuite, une seule fois, par format. Sans cela,
 * ajouter le XLSX aurait doublé les quatorze méthodes ; et surtout, la
 * neutralisation des formules ({@code CWE-1236}) serait à refaire dans chaque
 * branche, ce qui est exactement la manière dont ce genre de garde disparaît.
 *
 * <p><strong>Les mesures sont bornées à quatre-vingt-dix jours</strong>, et
 * c'est la seule ressource qui l'est. Une hypertable de capteurs compte des
 * millions de lignes ; un export intégral en mémoire ferait tomber le serveur,
 * et l'utilisateur n'aurait de toute façon rien à faire d'un fichier de cette
 * taille. La borne est dite dans l'en-tête du fichier plutôt que devinée.
 */
@Service
@Transactional(readOnly = true)
public class ExportService {

    /** Format d'export demandé (US-027, XLSX au SPRINT-27). */
    public enum Format {
        CSV(",", "text/csv", "csv"),
        TXT("\t", "text/plain", "txt"),
        XLSX(null, ClasseurXlsx.TYPE_MIME, "xlsx");

        private final String separateur;
        private final String typeMime;
        private final String extension;

        Format(String separateur, String typeMime, String extension) {
            this.separateur = separateur;
            this.typeMime = typeMime;
            this.extension = extension;
        }

        public String typeMime() {
            return typeMime;
        }

        public String extension() {
            return extension;
        }

        /** Vrai pour les formats délimités, qui se rendent en texte. */
        public boolean estTexte() {
            return separateur != null;
        }

        public static Format depuis(String valeur) {
            if (valeur == null) {
                return CSV;
            }
            return switch (valeur.toLowerCase()) {
                case "txt" -> TXT;
                case "xlsx" -> XLSX;
                default -> CSV;
            };
        }
    }

    /** Fenêtre des mesures exportées : au-delà, le fichier n'est plus lisible. */
    private static final int JOURS_MESURES = 90;

    private final VisiteRepository visites;
    private final RucheRepository ruches;
    private final SiteRepository sites;
    private final AgentRepository agents;
    private final RecolteRepository recoltes;
    private final TacheRepository taches;
    private final TraitementRepository traitements;
    private final NourrissementRepository nourrissements;
    private final LotConditionnementRepository lots;
    private final MesureRepository mesures;
    private final DepenseRepository depenses;
    private final MaterielRepository materiels;
    private final ConsommableRepository consommables;
    private final com.zumm.repository.ComptageVarroaRepository comptages;

    public ExportService(VisiteRepository visites, RucheRepository ruches, SiteRepository sites,
            AgentRepository agents, RecolteRepository recoltes, TacheRepository taches,
            TraitementRepository traitements, NourrissementRepository nourrissements,
            LotConditionnementRepository lots, MesureRepository mesures,
            DepenseRepository depenses, MaterielRepository materiels,
            ConsommableRepository consommables,
            com.zumm.repository.ComptageVarroaRepository comptages) {
        this.visites = visites;
        this.ruches = ruches;
        this.sites = sites;
        this.agents = agents;
        this.recoltes = recoltes;
        this.taches = taches;
        this.traitements = traitements;
        this.nourrissements = nourrissements;
        this.lots = lots;
        this.mesures = mesures;
        this.depenses = depenses;
        this.materiels = materiels;
        this.consommables = consommables;
        this.comptages = comptages;
    }

    /** Les ressources exportables, dans l'ordre où l'écran les propose. */
    public static List<String> ressources() {
        return List.of("visites", "ruches", "sites", "agents", "recoltes", "taches",
                "traitements", "nourrissements", "lots", "mesures", "depenses", "materiels",
                "consommables", "varroa");
    }

    /** Rend l'export au format demandé, texte ou binaire. */
    public byte[] exporter(String ressource, Format format) {
        List<List<String>> grille = grille(ressource);
        if (format == Format.XLSX) {
            return ClasseurXlsx.generer(ressource, grille);
        }
        return rendreTexte(grille, format).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── Les grilles, une par ressource ──────────────────────────────────────

    private List<List<String>> grille(String ressource) {
        return switch (ressource) {
            case "visites" -> grilleVisites();
            case "ruches" -> grilleRuches();
            case "sites" -> grilleSites();
            case "agents" -> grilleAgents();
            case "recoltes" -> grilleRecoltes();
            case "taches" -> grilleTaches();
            case "traitements" -> grilleTraitements();
            case "nourrissements" -> grilleNourrissements();
            case "varroa" -> grilleVarroa();
            case "lots" -> grilleLots();
            case "mesures" -> grilleMesures();
            case "depenses" -> grilleDepenses();
            case "materiels" -> grilleMateriels();
            case "consommables" -> grilleConsommables();
            default -> throw new RequeteInvalide("Ressource inconnue a l'export : " + ressource);
        };
    }

    private List<List<String>> grilleVisites() {
        List<List<String>> grille = entete("id", "date", "heure", "ruche", "agent", "raison",
                "etat_sante", "effectif", "productivite", "constatations");
        for (Visite v : visites.findAllByOrderByDateVisiteAsc()) {
            grille.add(List.of(texte(v.getId()), texte(v.getDateVisite()), texte(v.getHeureVisite()),
                    texte(v.getRuche().getModele()), texte(v.getAgent().getNom()),
                    texte(v.getRaison() == null ? null : v.getRaison().enBase()),
                    texte(v.getEtatSante() == null ? null : v.getEtatSante().enBase()),
                    texte(v.getEffectifQualitatif() == null ? null
                            : v.getEffectifQualitatif().enBase()),
                    texte(v.getProductivite()), texte(v.getConstatations())));
        }
        return grille;
    }

    private List<List<String>> grilleRuches() {
        List<List<String>> grille = entete("id", "modele", "site", "ferme", "etat", "priorite",
                "nb_compartiments");
        for (Ruche r : ruches.findAll()) {
            grille.add(List.of(texte(r.getId()), texte(r.getModele()),
                    texte(r.getSite().getNom()), texte(r.getFerme().getNom()),
                    texte(r.getEtat().enBase()), texte(r.getPriorite()),
                    texte(r.getCompartiments().size())));
        }
        return grille;
    }

    private List<List<String>> grilleSites() {
        List<List<String>> grille = entete("id", "nom", "ferme", "ville", "type", "exposition",
                "priorite", "couverture_reseau");
        for (Site s : sites.findAll()) {
            // Ni latitude ni longitude : l'export est un fichier qui circule, et
            // la position exacte d'un rucher est ce que `PolitiquePositions`
            // protège partout ailleurs. Un CSV n'a pas de rôle porteur.
            grille.add(List.of(texte(s.getId()), texte(s.getNom()), texte(s.getFerme().getNom()),
                    texte(s.getVille()), texte(s.getTypeSite()), texte(s.getExposition()),
                    texte(s.getPriorite()), texte(s.getCouvertureReseau())));
        }
        return grille;
    }

    private List<List<String>> grilleAgents() {
        List<List<String>> grille = entete("id", "nom", "role", "ferme", "notifications_email");
        agents.findAll().forEach(a -> grille.add(List.of(texte(a.getId()), texte(a.getNom()),
                texte(a.getRole() == null ? null : a.getRole().enBase()),
                texte(a.getFerme() == null ? null : a.getFerme().getNom()),
                // L'adresse e-mail n'est pas exportée : c'est une donnée
                // personnelle, et la leçon n°1 du §10 porte précisément sur des
                // adresses qui ont circulé.
                texte(a.isNotificationsEmail()))));
        return grille;
    }

    private List<List<String>> grilleRecoltes() {
        List<List<String>> grille = entete("id", "date", "ruche", "produit", "quantite", "unite",
                "type_miel", "lot", "carence_forcee");
        for (Recolte r : recoltes.findByOrderByDateRecolteDescIdDesc()) {
            grille.add(List.of(texte(r.getId()), texte(r.getDateRecolte()),
                    texte(r.getRuche().getModele()), texte(r.getTypeProduit()),
                    texte(r.getQuantiteKg()), texte(r.getUnite()), texte(r.getTypeMiel()),
                    texte(r.getLot()), texte(r.isCarenceForcee())));
        }
        return grille;
    }

    private List<List<String>> grilleTaches() {
        List<List<String>> grille = entete("id", "libelle", "ruche", "materiel", "agent",
                "echeance", "faite", "priorite", "categorie", "origine", "regle");
        for (Tache t : taches.findAll()) {
            grille.add(List.of(texte(t.getId()), texte(t.getLibelle()),
                    texte(t.getRuche() == null ? null : t.getRuche().getModele()),
                    texte(t.getMateriel() == null ? null : t.getMateriel().getLibelle()),
                    texte(t.getAgent() == null ? null : t.getAgent().getNom()),
                    texte(t.getEcheance()), texte(t.isFaite()), texte(t.getPriorite()),
                    texte(t.getCategorie()), texte(t.getOrigine()), texte(t.getRegleCode())));
        }
        return grille;
    }

    private List<List<String>> grilleTraitements() {
        List<List<String>> grille = entete("id", "ruche", "produit", "substance", "cible",
                "date_debut", "date_fin", "carence_jours", "date_retrait");
        for (Traitement t : traitements.findAll()) {
            grille.add(List.of(texte(t.getId()), texte(t.getRuche().getModele()),
                    texte(t.getProduit()), texte(t.getSubstanceActive()), texte(t.getCible()),
                    texte(t.getDateDebut()), texte(t.getDateFin()),
                    texte(t.getDelaiCarenceJours()), texte(t.getDateRetrait())));
        }
        return grille;
    }

    private List<List<String>> grilleNourrissements() {
        List<List<String>> grille = entete("id", "ruche", "date", "aliment", "quantite", "unite",
                "motif");
        nourrissements.findAll().forEach(n -> grille.add(List.of(texte(n.getId()),
                texte(n.getRuche().getModele()), texte(n.getDateApport()),
                texte(n.getTypeAliment()), texte(n.getQuantite()), texte(n.getQuantiteUnite()),
                texte(n.getMotif()))));
        return grille;
    }

    private List<List<String>> grilleVarroa() {
        List<List<String>> grille = entete("id", "ruche", "date", "methode", "varroas",
                "abeilles_echantillon", "jours_exposition");
        // Le TAUX n'est pas exporté, et la méthode l'est : le taux n'a pas la
        // même unité selon la méthode de comptage (varroas pour cent abeilles,
        // ou chutes par jour). Une colonne « taux » sans sa méthode rendrait
        // facile une comparaison fausse — voir `ComptageVarroaService`.
        comptages.findAll().forEach(c -> grille.add(List.of(texte(c.getId()),
                texte(c.getRuche().getModele()), texte(c.getDateComptage()),
                texte(c.getMethode()), texte(c.getVarroasComptes()),
                texte(c.getAbeillesEchantillon()), texte(c.getJoursExposition()))));
        return grille;
    }

    private List<List<String>> grilleLots() {
        List<List<String>> grille = entete("id", "reference", "date_conditionnement",
                "date_maturation", "date_durabilite", "quantite_kg", "type_miel");
        for (LotConditionnement l : lots.findAll()) {
            grille.add(List.of(texte(l.getId()), texte(l.getReference()),
                    texte(l.getDateConditionnement()), texte(l.getDateMaturation()),
                    texte(l.getDateDurabilite()), texte(l.getQuantiteKg()),
                    texte(l.getTypeMiel())));
        }
        return grille;
    }

    private List<List<String>> grilleMesures() {
        Instant depuis = Instant.now().minus(JOURS_MESURES, ChronoUnit.DAYS);
        List<List<String>> grille = entete("ruche_id", "indicateur", "instant", "valeur");
        for (Mesure m : mesures.findAll()) {
            if (m.getId().getInstant().isBefore(depuis)) {
                continue;
            }
            grille.add(List.of(texte(m.getId().getRucheId()),
                    texte(m.getId().getTypeIndicateur().enBase()),
                    texte(m.getId().getInstant()), texte(m.getValeur())));
        }
        return grille;
    }

    private List<List<String>> grilleDepenses() {
        List<List<String>> grille = entete("id", "date", "libelle", "categorie", "montant_eur",
                "ruche", "site");
        for (Depense d : depenses.findAllByOrderByDateDepenseDescIdDesc()) {
            grille.add(List.of(texte(d.getId()), texte(d.getDateDepense()), texte(d.getLibelle()),
                    texte(d.getCategorie()), texte(d.getMontantEur()),
                    texte(d.getRuche() == null ? null : d.getRuche().getModele()),
                    texte(d.getSite() == null ? null : d.getSite().getNom())));
        }
        return grille;
    }

    private List<List<String>> grilleMateriels() {
        List<List<String>> grille = entete("id", "libelle", "categorie", "quantite", "site",
                "etat", "periodicite_jours", "derniere_maintenance");
        for (Materiel m : materiels.findAllByOrderByCategorieAscLibelleAsc()) {
            grille.add(List.of(texte(m.getId()), texte(m.getLibelle()), texte(m.getCategorie()),
                    texte(m.getQuantite()), texte(m.getSite() == null ? null : m.getSite().getNom()),
                    texte(m.getEtat()), texte(m.getPeriodiciteJours()),
                    texte(m.getDerniereMaintenance())));
        }
        return grille;
    }

    private List<List<String>> grilleConsommables() {
        List<List<String>> grille = entete("id", "libelle", "categorie", "quantite", "unite",
                "seuil_alerte", "sous_seuil");
        for (Consommable c : consommables.findAllByOrderByCategorieAscLibelleAsc()) {
            grille.add(List.of(texte(c.getId()), texte(c.getLibelle()), texte(c.getCategorie()),
                    texte(c.getQuantite()), texte(c.getUnite()), texte(c.getSeuilAlerte()),
                    texte(c.sousSeuil())));
        }
        return grille;
    }

    // ─── Rendu ───────────────────────────────────────────────────────────────

    private static List<List<String>> entete(String... colonnes) {
        List<List<String>> grille = new ArrayList<>();
        grille.add(List.of(colonnes));
        return grille;
    }

    private static String rendreTexte(List<List<String>> grille, Format format) {
        StringBuilder sb = new StringBuilder();
        for (List<String> ligne : grille) {
            for (int i = 0; i < ligne.size(); i++) {
                if (i > 0) {
                    sb.append(format.separateur);
                }
                sb.append(echapper(ligne.get(i), format));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Caracteres qui, en tete de cellule, font interpreter le contenu comme une
     * FORMULE par Excel, LibreOffice et Google Sheets (CWE-1236, « CSV injection »).
     */
    private static final String AMORCES_DE_FORMULE = "=+-@\t\r";

    /**
     * Neutralise une amorce de formule en prefixant une apostrophe.
     *
     * <p>Un champ de note commencant par {@code =cmd|...} devient une commande
     * executee a l'ouverture du fichier. La garde vit ICI, en un seul endroit, et
     * le refactor du SPRINT-27 avait entre autres pour but de l'y maintenir :
     * repartie dans quatorze methodes, elle aurait disparu de l'une d'elles.
     */
    private static String neutraliserFormule(String champ) {
        if (champ.isEmpty() || AMORCES_DE_FORMULE.indexOf(champ.charAt(0)) < 0) {
            return champ;
        }
        // Un nombre negatif reste un nombre : le prefixer casserait tout export
        // numerique, alors qu'il ne peut rien executer.
        return estNombre(champ) ? champ : "'" + champ;
    }

    private static boolean estNombre(String valeur) {
        try {
            Double.parseDouble(valeur);
            return true;
        } catch (NumberFormatException pasUnNombre) {
            return false;
        }
    }

    private static String echapper(String champ, Format format) {
        String valeur = neutraliserFormule(champ);
        if (format == Format.TXT) {
            // Le tabulateur est le separateur : le laisser passer decalerait
            // toutes les colonnes suivantes.
            return valeur.replace("\t", " ").replace("\r", " ").replace("\n", " ");
        }
        boolean doitEtreCite = valeur.contains(",") || valeur.contains("\"")
                || valeur.contains("\n") || valeur.contains("\r");
        return doitEtreCite ? "\"" + valeur.replace("\"", "\"\"") + "\"" : valeur;
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : String.valueOf(valeur);
    }
}
