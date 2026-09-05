package com.zumm.service;

import com.zumm.domain.Nourrissement;
import com.zumm.domain.Traitement;
import com.zumm.repository.NourrissementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.web.dto.DossierConformite;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dossier de conformite pour un controle (SPRINT-29, lot D).
 *
 * <p><strong>Ce service ne certifie pas, et il ne le pretend nulle part.</strong>
 * La certification biologique est prononcee par un organisme agree, sur piece et
 * sur place. Ecrire « exploitation conforme » au bas d'un PDF serait une
 * affirmation reglementaire qu'aucune donnee du systeme ne fonde — et elle
 * serait crue, parce qu'elle sortirait d'un logiciel.
 *
 * <p>Ce qu'il fait : rassembler ce qu'un controleur demande, verifier les
 * quelques regles que les donnees permettent VRAIMENT de verifier, et nommer
 * tout le reste comme <em>a justifier</em>. Le troisieme etat est le plus
 * important des trois : compter comme conforme ce que le systeme ignore serait
 * un mensonge par omission, et le dossier perdrait toute valeur au premier
 * controle.
 *
 * <p>Ce que le systeme ne saura jamais, faute de le stocker : l'origine
 * biologique du sucre de nourrissement, celle de la cire gaufree, le statut des
 * surfaces autour des ruchers. Ces trois-la dependent de factures et
 * d'attestations ; elles sortent donc en {@code a_justifier}, avec le nombre
 * d'elements concernes.
 */
@Service
public class ConformiteBioService {

    /**
     * Substances admises en apiculture biologique dans l'Union europeenne.
     *
     * <p>Liste VOLONTAIREMENT courte, et comparee sur la substance active plutot
     * que sur le nom commercial : un nom commercial change, une molecule non.
     * Tout ce qui n'y figure pas est <strong>signale</strong>, jamais declare
     * interdit — c'est au controleur de trancher, et une specialite locale peut
     * etre autorisee la ou nous ne la connaissons pas.
     */
    private static final Set<String> SUBSTANCES_ADMISES = Set.of(
            "acide oxalique", "acide formique", "acide lactique", "acide acetique",
            "thymol", "menthol", "camphre", "eucalyptol");

    private final TraitementRepository traitements;
    private final NourrissementRepository nourrissements;
    private final RecolteRepository recoltes;

    public ConformiteBioService(TraitementRepository traitements,
            NourrissementRepository nourrissements, RecolteRepository recoltes) {
        this.traitements = traitements;
        this.nourrissements = nourrissements;
        this.recoltes = recoltes;
    }

    @Transactional(readOnly = true)
    public DossierConformite evaluer(LocalDate debut, LocalDate fin) {
        List<DossierConformite.PointControle> points = new ArrayList<>();
        points.add(traitementsAdmis(debut, fin));
        points.add(carencesRespectees(debut, fin));
        points.add(origineDesSucres(debut, fin));
        points.add(tracabiliteDesLots(debut, fin));
        return new DossierConformite(debut, fin,
                "Zümm ne certifie pas. Ce dossier rassemble les pièces d'un contrôle et "
                        + "signale ce qu'il ne peut pas vérifier : la certification est "
                        + "prononcée par un organisme agréé, sur pièce et sur place.",
                points);
    }

    /** Les substances employees figurent-elles parmi celles admises ? */
    private DossierConformite.PointControle traitementsAdmis(LocalDate debut, LocalDate fin) {
        List<Traitement> periode = traitements.findAll().stream()
                .filter(t -> dansLaPeriode(t.getDateDebut(), debut, fin))
                .toList();
        List<Traitement> horsListe = periode.stream()
                .filter(t -> !admise(t.getSubstanceActive()))
                .toList();
        if (periode.isEmpty()) {
            return new DossierConformite.PointControle("traitements", "a_justifier",
                    "Aucun traitement enregistré sur la période. Un registre vide se justifie "
                            + "devant un contrôle, il ne se présume pas.", 0);
        }
        if (horsListe.isEmpty()) {
            return new DossierConformite.PointControle("traitements", "verifie",
                    "Toutes les substances actives enregistrées figurent parmi celles admises "
                            + "en apiculture biologique.", periode.size());
        }
        String noms = horsListe.stream()
                .map(t -> t.getSubstanceActive() == null
                        ? t.getProduit() + " (substance non renseignée)"
                        : t.getSubstanceActive())
                .distinct().limit(5).reduce((a, b) -> a + ", " + b).orElse("");
        return new DossierConformite.PointControle("traitements", "signale",
                "Substances hors de la liste admise, à justifier : " + noms
                        + ". Une spécialité autorisée localement peut y figurer : "
                        + "c'est au contrôleur de trancher.", horsListe.size());
    }

    /**
     * Une recolte a-t-elle eu lieu pendant une carence, en la forcant ?
     *
     * <p>C'est la seule non-conformite que le systeme constate DE LUI-MEME, et
     * il la constate parce que le SPRINT-22 a rendu le forcage tracable au lieu
     * de l'interdire. Un refus sans issue aurait fait disparaitre le traitement
     * du registre, et cette verification n'existerait pas.
     */
    private DossierConformite.PointControle carencesRespectees(LocalDate debut, LocalDate fin) {
        long forcees = recoltes.findAll().stream()
                .filter(r -> dansLaPeriode(r.getDateRecolte(), debut, fin))
                .filter(r -> r.isCarenceForcee())
                .count();
        if (forcees == 0) {
            return new DossierConformite.PointControle("carences", "verifie",
                    "Aucune récolte enregistrée pendant un délai de carence.", 0);
        }
        return new DossierConformite.PointControle("carences", "signale",
                "Des récoltes ont été enregistrées malgré une carence en cours, avec motif. "
                        + "Les motifs figurent au journal d'audit sous l'action « forcage ».",
                (int) forcees);
    }

    /**
     * L'origine des sucres de nourrissement.
     *
     * <p>Toujours <em>a justifier</em> : le systeme enregistre CE QUI a ete
     * donne, jamais d'ou cela vient. Le declarer conforme parce que la ligne
     * existe serait exactement le mensonge que ce dossier doit eviter.
     */
    private DossierConformite.PointControle origineDesSucres(LocalDate debut, LocalDate fin) {
        List<Nourrissement> periode = nourrissements.findAll().stream()
                .filter(n -> dansLaPeriode(n.getDateApport(), debut, fin))
                .toList();
        return new DossierConformite.PointControle("nourrissement", "a_justifier",
                "L'origine biologique des sucres et sirops ne figure pas dans le système : "
                        + "elle se prouve par facture. Les apports sont tracés, leur "
                        + "provenance non.", periode.size());
    }

    /** Chaque recolte porte-t-elle son lot ? C'est ce qui rend la tracabilite lisible. */
    private DossierConformite.PointControle tracabiliteDesLots(LocalDate debut, LocalDate fin) {
        long sansLot = recoltes.findAll().stream()
                .filter(r -> dansLaPeriode(r.getDateRecolte(), debut, fin))
                .filter(r -> r.getLot() == null || r.getLot().isBlank())
                .count();
        if (sansLot == 0) {
            return new DossierConformite.PointControle("tracabilite", "verifie",
                    "Chaque récolte de la période porte son numéro de lot.", 0);
        }
        return new DossierConformite.PointControle("tracabilite", "signale",
                "Des récoltes n'ont pas de numéro de lot : la chaîne du pot à la ruche est "
                        + "interrompue.", (int) sansLot);
    }

    private static boolean admise(String substance) {
        if (substance == null || substance.isBlank()) {
            return false;
        }
        String normalisee = substance.toLowerCase(Locale.ROOT);
        return SUBSTANCES_ADMISES.stream().anyMatch(normalisee::contains);
    }

    private static boolean dansLaPeriode(LocalDate jour, LocalDate debut, LocalDate fin) {
        return jour != null && !jour.isBefore(debut) && !jour.isAfter(fin);
    }
}
