package com.zumm.service;

import com.zumm.domain.Alerte;
import com.zumm.domain.Tache;
import com.zumm.repository.AlerteRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.Briefing;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Briefing du jour (SPRINT-30, lot G).
 *
 * <p>Ferme la ligne « assistant / mentor IA, briefing quotidien » du §7. Le
 * reproche etait juste : l'IA de Zumm surveille des series de capteurs, elle ne
 * lit pas l'historique d'une colonie et ne propose rien.
 *
 * <p><strong>Aucun modele de langue n'intervient, et c'est la decision.</strong>
 * Ce service lit quatre registres qui existent deja et dit ce qui merite
 * l'attention aujourd'hui. Chaque ligne cite ce qui la fonde — un compte, une
 * date, un nom de ruche — et se verifie d'un clic.
 *
 * <p>Un modele qui redigerait « votre colonie 12 semble affaiblie » produirait
 * une phrase plus agreable et moins verifiable ; le jour ou elle serait fausse,
 * personne ne saurait dire d'ou elle vient. Et il faudrait lui envoyer
 * l'historique de l'exploitation, ce que la meme journee de travail vient
 * justement d'interdire ({@code PolitiqueReseau}). Les deux moities du lot G
 * doivent tenir ensemble.
 *
 * <p><strong>Rien n'est stocke.</strong> Le briefing d'hier n'a aucun interet :
 * il se recalcule, comme les indices de colonie du SPRINT-22.
 */
@Service
public class BriefingService {

    /**
     * Au-dela, une colonie est signalee comme non vue.
     *
     * <p>Trois semaines : c'est l'intervalle au-dela duquel une colonie peut
     * avoir essaime sans que personne ne l'ait su. Le rendre configurable
     * ajouterait un reglage que personne n'irait chercher.
     */
    private static final int JOURS_SANS_VISITE = 21;

    /** Fenetre pendant laquelle une fin de carence merite d'etre annoncee. */
    private static final int JOURS_AVANT_RETRAIT = 7;

    private final AlerteRepository alertes;
    private final TacheRepository taches;
    private final TraitementRepository traitements;
    private final VisiteRepository visites;

    public BriefingService(AlerteRepository alertes, TacheRepository taches,
            TraitementRepository traitements, VisiteRepository visites) {
        this.alertes = alertes;
        this.taches = taches;
        this.traitements = traitements;
        this.visites = visites;
    }

    /**
     * Ce qui merite l'attention aujourd'hui.
     *
     * <p>Les lignes sortent triees par urgence puis par categorie : un briefing
     * ou l'ordre change d'un jour a l'autre se relit en entier a chaque fois.
     */
    @Transactional(readOnly = true)
    public Briefing duJour(LocalDate jour) {
        List<Briefing.Ligne> lignes = new ArrayList<>();
        lignes.addAll(alertesOuvertes());
        lignes.addAll(tachesEchues(jour));
        lignes.addAll(carencesQuiSeTerminent(jour));
        lignes.addAll(coloniesNonVues(jour));

        lignes.sort(Comparator.comparingInt(Briefing.Ligne::urgence)
                .thenComparing(Briefing.Ligne::categorie));
        return new Briefing(jour, lignes);
    }

    /** Une ligne par alerte ouverte : ce sont elles qu'on regarde en premier. */
    private List<Briefing.Ligne> alertesOuvertes() {
        List<Alerte> ouvertes = alertes.findByOuverteTrueOrderByOuverteLeDesc();
        return ouvertes.stream()
                .map(a -> new Briefing.Ligne("alerte", 1,
                        "Alerte %s sur la ruche #%d"
                                .formatted(a.getTypeIndicateur(), a.getRuche().getId()),
                        "Declenchee a %s".formatted(a.getValeurDeclenchement()),
                        a.getRuche().getId()))
                .toList();
    }

    /**
     * Les taches echues, groupees en UNE ligne.
     *
     * <p>Une ligne par tache noierait le briefing : une exploitation qui a
     * quarante taches en retard n'a pas quarante choses a savoir, elle en a une.
     * Le detail se lit dans l'ecran des taches, qui existe pour cela.
     */
    private List<Briefing.Ligne> tachesEchues(LocalDate jour) {
        List<Tache> echues = taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(jour);
        if (echues.isEmpty()) {
            return List.of();
        }
        Tache plusAncienne = echues.get(0);
        return List.of(new Briefing.Ligne("tache", 1,
                "%d tache(s) a faire".formatted(echues.size()),
                "La plus ancienne : « %s », echue le %s"
                        .formatted(plusAncienne.getLibelle(), plusAncienne.getEcheance()),
                plusAncienne.getRuche() == null ? null : plusAncienne.getRuche().getId()));
    }

    /**
     * Les carences qui se terminent dans la semaine.
     *
     * <p>C'est l'information que le registre ne donne pas de lui-meme : savoir
     * qu'une ruche est SOUS carence est facile, savoir qu'elle en SORT jeudi
     * demande de calculer. C'est pourtant ce qui decide d'une tournee de
     * recolte.
     */
    private List<Briefing.Ligne> carencesQuiSeTerminent(LocalDate jour) {
        LocalDate horizon = jour.plusDays(JOURS_AVANT_RETRAIT);
        return traitements.sousCarenceAu(jour).stream()
                .filter(t -> t.getDateRetrait() != null && !t.getDateRetrait().isAfter(horizon))
                .map(t -> new Briefing.Ligne("carence", 2,
                        "Fin de carence le %s sur la ruche #%d"
                                .formatted(t.getDateRetrait(), t.getRuche().getId()),
                        "%s, applique le %s".formatted(t.getProduit(), t.getDateDebut()),
                        t.getRuche().getId()))
                .toList();
    }

    /**
     * Les colonies qu'on n'a pas ouvertes depuis trois semaines.
     *
     * <p>Une ruche JAMAIS visitee n'y figure pas, et c'est deliberе : elle vient
     * peut-etre d'etre enregistree, et la signaler le jour de sa creation ferait
     * passer le briefing pour un reproche.
     */
    private List<Briefing.Ligne> coloniesNonVues(LocalDate jour) {
        LocalDate limite = jour.minusDays(JOURS_SANS_VISITE);
        return visites.dernieresVisitesParRuche().stream()
                .filter(v -> v.getDateVisite().isBefore(limite))
                .map(v -> new Briefing.Ligne("visite", 3,
                        "Ruche #%d non visitee depuis le %s"
                                .formatted(v.getRuche().getId(), v.getDateVisite()),
                        "%d jours".formatted(ChronoUnit.DAYS.between(v.getDateVisite(), jour)),
                        v.getRuche().getId()))
                .toList();
    }
}
