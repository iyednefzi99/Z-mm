package com.zumm.service;

import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.RecolteCorps;
import com.zumm.web.dto.RecolteReponse;
import com.zumm.web.dto.TraceReponse;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recoltes et tracabilite par lot (US-033). */
@Service
@Transactional
public class RecolteService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RecolteRepository recoltes;
    private final RucheRepository ruches;

    public RecolteService(RecolteRepository recoltes, RucheRepository ruches) {
        this.recoltes = recoltes;
        this.ruches = ruches;
    }

    public RecolteReponse creer(RecolteCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        Recolte recolte = new Recolte(ruche, corps.dateRecolte(), corps.quantiteKg(),
                genererLot(ruche.getId(), corps.dateRecolte()));
        recolte.setTypeMiel(corps.typeMiel());
        recolte.setNote(corps.note());
        return RecolteReponse.de(recoltes.save(recolte));
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
