package com.zumm.service;

import com.zumm.domain.Ruche;
import com.zumm.domain.SuiviReine;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SuiviReineRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.ReineCorps;
import com.zumm.web.dto.ReineReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Suivi de la reine : journal d'evenements par ruche (US-032). */
@Service
@Transactional
public class SuiviReineService {

    private final SuiviReineRepository reines;
    private final RucheRepository ruches;

    public SuiviReineService(SuiviReineRepository reines, RucheRepository ruches) {
        this.reines = reines;
        this.ruches = ruches;
    }

    public ReineReponse enregistrer(ReineCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        SuiviReine evenement = new SuiviReine(ruche, corps.dateEvenement(), corps.statut());
        evenement.setCouleurMarquage(corps.couleurMarquage());
        evenement.setAnneeNaissance(corps.anneeNaissance());
        evenement.setRace(corps.race());
        evenement.setNote(corps.note());
        return ReineReponse.de(reines.save(evenement));
    }

    /** Historique de la reine d'une ruche, du plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<ReineReponse> historique(Long rucheId) {
        if (ruches.findById(rucheId).isEmpty()) {
            throw RessourceIntrouvable.de("Ruche", rucheId);
        }
        return reines.findByRuche_IdOrderByDateEvenementDescIdDesc(rucheId).stream()
                .map(ReineReponse::de).toList();
    }

    public void supprimer(Long id) {
        SuiviReine evenement = reines.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Événement de reine", id));
        reines.delete(evenement);
    }
}
