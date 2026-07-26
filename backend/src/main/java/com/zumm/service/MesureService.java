package com.zumm.service;

import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.AlerteReponse;
import com.zumm.web.dto.MesureCorps;
import com.zumm.web.dto.MesureReponse;
import com.zumm.web.dto.PointJournalier;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingestion des mesures de capteurs (US-017) et evaluation des seuils (US-018).
 *
 * <p>Point d'entree commun aux deux canaux du cahier : l'API REST
 * ({@code POST /api/mesures}) et le pont MQTT (qui appelle {@link #ingerer}). Chaque
 * mesure ingeree est confrontee aux seuils, ce qui peut ouvrir ou fermer une alerte.
 */
@Service
@Transactional
public class MesureService {

    private final MesureRepository mesures;
    private final RucheRepository ruches;
    private final SeuilAlerteService alertes;

    public MesureService(MesureRepository mesures, RucheRepository ruches, SeuilAlerteService alertes) {
        this.mesures = mesures;
        this.ruches = ruches;
        this.alertes = alertes;
    }

    /** Ingere une mesure et renvoie la mesure enregistree avec les alertes declenchees. */
    public MesureReponse ingerer(MesureCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        Instant instant = corps.instant() == null ? Instant.now() : corps.instant();

        MesureId id = new MesureId(ruche.getId(), corps.typeIndicateur(), instant);
        Mesure mesure = mesures.save(new Mesure(id, corps.valeur()));

        List<AlerteReponse> declenchees =
                alertes.evaluer(ruche, corps.typeIndicateur(), corps.valeur());
        return MesureReponse.de(mesure, declenchees);
    }

    /**
     * Serie BRUTE d'un indicateur pour une ruche, de la plus ancienne a la plus
     * recente.
     *
     * <p>Reste utile a ce qui a besoin du detail — la detection d'anomalie. Pour
     * AFFICHER une courbe, preferer {@link #serieJournaliere} : le brut represente
     * des dizaines de milliers de points qu'aucun graphique ne montrera.
     */
    @Transactional(readOnly = true)
    public List<MesureReponse> serie(Long rucheId, TypeIndicateur type) {
        return mesures.findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(rucheId, type).stream()
                .map(m -> MesureReponse.de(m, List.of()))
                .toList();
    }

    /**
     * Serie JOURNALIERE, agregee en base (SPRINT-18).
     *
     * <p>C'est ce que consomme la courbe de l'interface : un point par jour au lieu
     * d'un point par quart d'heure, soit environ cent fois moins de donnees
     * transportees pour un graphique strictement identique a l'oeil.
     */
    @Transactional(readOnly = true)
    public List<PointJournalier> serieJournaliere(Long rucheId, TypeIndicateur type) {
        return mesures.serieJournaliere(rucheId, type.enBase()).stream()
                .map(c -> new PointJournalier(c.getJour(), c.getMoyenne(),
                        c.getMinimum(), c.getMaximum(), c.getNombre()))
                .toList();
    }
}
