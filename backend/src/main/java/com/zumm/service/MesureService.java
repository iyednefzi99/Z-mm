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
 * <p>Depuis le SPRINT-31, l'ingestion d'un poids passe aussi par l'alarme
 * anti-vol : une chute brutale que AUCUNE recolte n'explique ouvre une alerte
 * d'une categorie distincte.
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
    private final AlerteAntivolService antivol;
    private final com.zumm.repository.AlerteRepository alertesRepo;

    public MesureService(MesureRepository mesures, RucheRepository ruches,
            SeuilAlerteService alertes, AlerteAntivolService antivol,
            com.zumm.repository.AlerteRepository alertesRepo) {
        this.mesures = mesures;
        this.ruches = ruches;
        this.alertes = alertes;
        this.antivol = antivol;
        this.alertesRepo = alertesRepo;
    }

    /** Ingere une mesure et renvoie la mesure enregistree avec les alertes declenchees. */
    public MesureReponse ingerer(MesureCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        Instant instant = corps.instant() == null ? Instant.now() : corps.instant();

        MesureId id = new MesureId(ruche.getId(), corps.typeIndicateur(), instant);
        Mesure mesure = mesures.save(new Mesure(id, corps.valeur()));

        // Deux familles d'alertes, evaluees l'une apres l'autre : le
        // depassement de seuil (US-018) et la chute brutale sans recolte
        // (SPRINT-31). Elles coexistent — une ruche peut etre legere ET volee.
        List<AlerteReponse> declenchees = new java.util.ArrayList<>(
                alertes.evaluer(ruche, corps.typeIndicateur(), corps.valeur()));
        declenchees.addAll(
                antivol.examiner(ruche, corps.typeIndicateur(), corps.valeur(), instant));
        return MesureReponse.de(mesure, declenchees);
    }

    /**
     * Alertes ouvertes, les plus recentes d'abord.
     *
     * <p>DEFAUT TROUVE AU SPRINT-31, et anterieur a lui : cette lecture vivait
     * dans le controleur, hors de toute transaction. `Alerte.ruche` etant LAZY,
     * `AlerteReponse.de` levait une `LazyInitializationException` des qu'une
     * alerte existait — la route ne pouvait donc reussir QUE sur une liste vide,
     * et aucun test ne l'avait jamais appelee autrement.
     */
    @Transactional(readOnly = true)
    public List<AlerteReponse> alertesOuvertes() {
        return alertesRepo.findByOuverteTrueOrderByOuverteLeDesc().stream()
                .map(AlerteReponse::de).toList();
    }

    /**
     * Ingere plusieurs mesures en une requete (SPRINT-31, lot F2).
     *
     * <p>Ce que toute passerelle demande, et que le point d'entree unitaire
     * rendait couteux : quarante ruches et quatre indicateurs releves tous les
     * quarts d'heure font cent soixante requetes, chacune avec sa poignee de
     * main TLS et sa cle d'idempotence.
     *
     * <p><strong>Une transaction, et un refus global.</strong> Contrairement aux
     * operations de lot au rucher (SPRINT-23), ou chaque ruche a sa propre
     * transaction, un lot de mesures qui echoue a moitie laisserait la
     * passerelle sans moyen de savoir ce qui est passe : elle rejouerait tout,
     * et l'idempotence porte sur la REQUETE, pas sur chaque ligne. Ici, tout
     * passe ou rien ne passe.
     */
    public List<MesureReponse> ingererLot(List<MesureCorps> corps) {
        return corps.stream().map(this::ingerer).toList();
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
