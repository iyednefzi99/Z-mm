package com.zumm.service;

import com.zumm.domain.AuditEntree;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Traitement;
import com.zumm.repository.AuditEntreeRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.securite.IdentiteAppelant;
import com.zumm.web.RegleMetierViolee;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.RapportLot;
import com.zumm.web.dto.RecolteCorps;
import com.zumm.web.dto.RecolteLotCorps;
import com.zumm.web.dto.RecolteReponse;
import com.zumm.web.dto.TraceReponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Recoltes et tracabilite par lot (US-033). */
@Service
@Transactional
public class RecolteService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RecolteRepository recoltes;
    private final RucheRepository ruches;
    private final TraitementRepository traitements;
    private final AuditEntreeRepository audits;
    private final OperationsLotService lots;

    public RecolteService(RecolteRepository recoltes, RucheRepository ruches,
            TraitementRepository traitements, AuditEntreeRepository audits,
            OperationsLotService lots) {
        this.recoltes = recoltes;
        this.ruches = ruches;
        this.traitements = traitements;
        this.audits = audits;
        this.lots = lots;
    }

    /**
     * Enregistre une recolte, apres verification de la carence (SPRINT-22).
     *
     * <p>C'est le troisieme des sept ecarts du §11, et le seul qui transforme une
     * donnee consultative en refus. Le SPRINT-20 avait consigne le delai de
     * carence sans jamais l'opposer : la liste des ruches sous carence se lisait,
     * rien n'empechait d'enregistrer la recolte.
     */
    public RecolteReponse creer(RecolteCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        Traitement bloquant = carenceEnCours(ruche.getId(), corps.dateRecolte());

        Recolte recolte = new Recolte(ruche, corps.dateRecolte(), corps.quantiteKg(),
                genererLot(ruche.getId(), corps.dateRecolte()));
        recolte.setTypeMiel(corps.typeMiel());
        // Absents, ce sont les defauts de l'entite — du miel, en kilogrammes.
        // C'est ce que le modele presupposait avant le SPRINT-27, et le laisser
        // ainsi preserve les appelants qui ignorent ces deux champs.
        if (corps.typeProduit() != null) {
            recolte.setTypeProduit(corps.typeProduit());
        }
        if (corps.unite() != null) {
            recolte.setUnite(corps.unite());
        }
        recolte.setNote(corps.note());

        if (bloquant != null) {
            if (!corps.forcerCarence()) {
                throw new RegleMetierViolee(
                        "Ruche sous carence jusqu'au " + bloquant.getDateRetrait() + " ("
                                + bloquant.getProduit() + "). Le miel ne peut pas partir en "
                                + "recolte avant cette date. Pour passer outre, indiquez un "
                                + "motif : la decision sera consignee au journal d'audit.");
            }
            if (corps.motifForcage() == null || corps.motifForcage().isBlank()) {
                throw new RequeteInvalide(
                        "Forcer une carence demande un motif : une case cochee sans "
                                + "explication n'a aucune valeur devant un controle.");
            }
            recolte.forcerCarence(corps.motifForcage().trim());
        }

        Recolte enregistree = recoltes.save(recolte);
        if (bloquant != null) {
            tracerForcage(enregistree, bloquant);
        }
        return RecolteReponse.de(enregistree);
    }

    /**
     * Recolte tout un rucher en une saisie (SPRINT-23, lot B).
     *
     * <p>La quantite est celle de CHAQUE ruche, jamais un total a repartir :
     * diviser une masse par le nombre de colonies fabriquerait une donnee fausse
     * pour chacune, et c'est cette donnee qui alimente la production par ruche,
     * la correlation meteo et la comparaison d'emplacements.
     *
     * <p>Les ruches sous carence ressortent en ECHEC motive, avec leur date de
     * fin — sauf forcage, qui exige alors un motif comme pour l'acte unitaire.
     * C'est le meme chemin de code : la regle du lot A ne peut pas etre
     * contournee en passant par le lot.
     */
    public RapportLot creerLot(RecolteLotCorps corps) {
        return lots.executer(corps.cible(), ruche -> creerPour(ruche, corps));
    }

    /** Une recolte pour UNE ruche, dans sa propre transaction (echec partiel). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long creerPour(Ruche ruche, RecolteLotCorps corps) {
        // Un lot recolte du MIEL : c'est le geste que la route de lot decrit —
        // « la meme recolte sur tout le rucher ». Une cire ou un essaim se
        // saisissent a l'unite, sur la ruche concernee.
        return creer(new RecolteCorps(ruche.getId(), corps.dateRecolte(),
                corps.quantiteKgParRuche(), corps.typeMiel(), "miel", "kg", corps.note(),
                corps.forcerCarence(), corps.motifForcage())).id();
    }

    /**
     * Traitement dont la carence couvre encore le jour de recolte, ou {@code null}.
     *
     * <p>La date comparee est celle de la RECOLTE, pas celle du jour : antidater
     * une recolte au milieu d'une carence resterait un contournement, et il doit
     * etre refuse de la meme facon.
     */
    private Traitement carenceEnCours(Long rucheId, LocalDate dateRecolte) {
        return traitements.sousCarenceAu(dateRecolte).stream()
                .filter(t -> t.getRuche().getId().equals(rucheId))
                .filter(t -> t.sousCarence(dateRecolte))
                .findFirst()
                .orElse(null);
    }

    /**
     * Consigne le passage outre.
     *
     * <p>L'aspect d'audit enregistre deja la CREATION de la recolte ; ce qu'il ne
     * sait pas dire, c'est qu'une regle a ete ecartee et pourquoi. Cette entree-la
     * est celle qu'un controle vient chercher, et elle porte l'action `forcage`
     * pour ne pas se noyer parmi les creations ordinaires.
     */
    private void tracerForcage(Recolte recolte, Traitement bloquant) {
        String acteur = IdentiteAppelant.de(
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication()).nomPourAudit();
        audits.save(new AuditEntree(acteur, AuditEntree.FORCAGE, "Recolte", recolte.getId(),
                "Recolte sur ruche " + recolte.getRuche().getId() + " sous carence jusqu'au "
                        + bloquant.getDateRetrait() + " (" + bloquant.getProduit() + ") — motif : "
                        + recolte.getMotifForcage()));
    }

    @Transactional(readOnly = true)
    public List<RecolteReponse> lister() {
        return recoltes.findByOrderByDateRecolteDescIdDesc().stream().map(RecolteReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public RecolteReponse obtenir(Long id) {
        return RecolteReponse.de(entite(id));
    }

    public void supprimer(Long id) {
        recoltes.delete(entite(id));
    }

    /** Fiche de tracabilite d'un lot (cible du QR code). */
    @Transactional(readOnly = true)
    public TraceReponse tracer(String lot) {
        return recoltes.findByLot(lot)
                .map(TraceReponse::de)
                .orElseThrow(() -> new RessourceIntrouvable("Lot introuvable : " + lot));
    }

    private Recolte entite(Long id) {
        return recoltes.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Récolte", id));
    }

    /** Lot deterministe et lisible : {@code ZUMM-<ruche>-<jour>-<séquence>}. */
    private String genererLot(Long rucheId, java.time.LocalDate date) {
        long rang = recoltes.countByRuche_IdAndDateRecolte(rucheId, date) + 1;
        return "ZUMM-%d-%s-%02d".formatted(rucheId, date.format(JOUR), rang);
    }
}
