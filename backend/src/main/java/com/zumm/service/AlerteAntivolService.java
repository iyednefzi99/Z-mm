package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.Alerte;
import com.zumm.domain.Mesure;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.AlerteRepository;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.web.dto.AlerteReponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alarme anti-vol : la chute brutale que rien n'explique (SPRINT-31, lot F2).
 *
 * <p>Ferme la ligne « alarme anti-vol / detection de basculement » du §5 de
 * {@code docs/ECART-CONCURRENTS.md}, dont le constat etait le plus cinglant du
 * document : « le vol de ruches est la menace qui justifie
 * {@code PolitiquePositions} — Zumm CACHE la position pour proteger du vol,
 * Onibi ALERTE quand il survient. Les deux reponses sont complementaires, Zumm
 * n'a que la premiere. »
 *
 * <p><strong>Aucun capteur nouveau n'est requis</strong>, et c'est le point : une
 * ruche emportee ou renversee se voit dans la serie de POIDS que l'API ingere
 * deja. Ce qui manquait n'etait pas une donnee, c'etait une regle.
 *
 * <p><strong>Et la regle tient en une phrase.</strong> Une chute de vingt
 * kilogrammes est une RECOLTE si une recolte a ete enregistree ce jour-la sur
 * cette ruche, et un VOL sinon. Sans cette verification, la premiere miellee de
 * l'annee reveillerait l'alarme sur tout le rucher — et une alarme qui sonne
 * pour rien se coupe, apres quoi elle ne protege plus de rien.
 *
 * <p><strong>Ce que ce service ne fait pas.</strong> Il ne ferme jamais l'alerte
 * qu'il ouvre. {@code SeuilAlerteService} referme les siennes quand la valeur
 * revient dans la bande ; une ruche volee ne revient pas toute seule, et une
 * alerte de vol qui se fermerait parce que la balance repose sur le sol serait
 * pire que pas d'alerte du tout. C'est l'apiculteur qui la clot, en connaissance
 * de cause.
 */
@Service
public class AlerteAntivolService {

    /**
     * Ecart maximal entre deux mesures pour que la comparaison ait un sens.
     *
     * <p>Deux heures : au-dela, la chute a pu s'etaler sur une journee de
     * travail, et on ne sait plus si elle est brutale. Une passerelle qui pousse
     * toutes les quinze minutes tient largement dans cette fenetre ; une qui
     * pousse une fois par jour ne declenchera jamais cette alarme, et c'est
     * honnete — elle ne peut pas voir un vol.
     */
    private static final Duration FENETRE = Duration.ofHours(2);

    private final MesureRepository mesures;
    private final RecolteRepository recoltes;
    private final AlerteRepository alertes;
    private final NotificationAlerteService notifications;
    private final ConfigurationMetier configuration;

    public AlerteAntivolService(MesureRepository mesures, RecolteRepository recoltes,
            AlerteRepository alertes, NotificationAlerteService notifications,
            ConfigurationMetier configuration) {
        this.mesures = mesures;
        this.recoltes = recoltes;
        this.alertes = alertes;
        this.notifications = notifications;
        this.configuration = configuration;
    }

    /**
     * Examine la derniere mesure de poids et ouvre une alerte si rien ne
     * l'explique.
     *
     * <p>Appele a l'ingestion, apres l'enregistrement : la mesure courante est
     * donc deja en base, et {@code precedente} est l'avant-derniere.
     */
    @Transactional
    public List<AlerteReponse> examiner(Ruche ruche, TypeIndicateur type, BigDecimal valeur,
            Instant instant) {
        if (type != TypeIndicateur.POIDS) {
            return List.of();
        }
        Optional<Mesure> precedente = mesures
                .findFirstByIdRucheIdAndIdTypeIndicateurAndIdInstantLessThanOrderByIdInstantDesc(
                        ruche.getId(), type, instant);
        if (precedente.isEmpty()) {
            return List.of();
        }
        Instant avant = precedente.get().getId().getInstant();
        if (Duration.between(avant, instant).compareTo(FENETRE) > 0) {
            return List.of();
        }

        BigDecimal chute = precedente.get().getValeur().subtract(valeur);
        int seuil = configuration.seuils().chuteVolKg();
        if (chute.compareTo(BigDecimal.valueOf(seuil)) < 0) {
            return List.of();
        }

        // LA verification qui rend l'alarme utilisable : une recolte enregistree
        // ce jour-la explique la chute, et l'alarme se tait.
        LocalDate jour = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        if (!recoltes.findByRuche_IdAndDateRecolteBetweenOrderByDateRecolteAsc(
                ruche.getId(), jour, jour).isEmpty()) {
            return List.of();
        }

        // Une seule alerte de vol ouverte a la fois : la ruche n'est pas volee
        // deux fois, et repeter l'alerte a chaque mesure noierait la liste au
        // moment ou elle doit se lire d'un coup d'oeil.
        if (alertes.findByRuche_IdAndTypeIndicateurAndCategorieAndOuverteTrue(
                ruche.getId(), type, Alerte.ANTIVOL).isPresent()) {
            return List.of();
        }

        Alerte alerte = alertes.save(new Alerte(ruche, type, Alerte.CRITIQUE,
                "Chute de %.1f kg en moins de %d h, sans recolte enregistree ce jour"
                        .formatted(chute.doubleValue(), FENETRE.toHours()),
                valeur, Alerte.ANTIVOL));
        notifications.notifierOuverture(alerte);
        return List.of(AlerteReponse.de(alerte));
    }
}
