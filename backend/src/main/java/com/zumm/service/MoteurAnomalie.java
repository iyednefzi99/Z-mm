package com.zumm.service;

import com.zumm.domain.TypeIndicateur;
import com.zumm.web.dto.AnomalieReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Moteur de detection d'anomalie, vu comme un PORT (US-035).
 *
 * <p>Ce que cette interface corrige : {@code AnomalieService} dependait
 * directement du client HTTP du microservice Python, et lui passait des entites
 * {@code Mesure} — c'est-a-dire des objets JPA. Le domaine traversait donc la
 * frontiere, et l'adaptateur connaissait le modele de persistance. Deux couches
 * qui n'ont aucune raison de se connaitre etaient soudees.
 *
 * <p>Consequences pratiques de la separation :
 * <ul>
 *   <li>changer de moteur — scikit-learn, un service tiers, un calcul local —
 *       ne demande qu'une implementation de plus ;
 *   <li>l'adaptateur HTTP ne peut plus dependre d'Hibernate, donc une evolution
 *       du mapping ne peut plus casser l'appel au microservice ;
 *   <li>le test du service n'a plus besoin d'un serveur : un double de ce port
 *       suffit.
 * </ul>
 *
 * <p>Le type d'entree est volontairement NEUTRE : un instant, une valeur. Ni
 * entite, ni identifiant technique, ni rien qui trahisse la maniere dont Zumm
 * range ses mesures.
 */
public interface MoteurAnomalie {

    /**
     * Point d'une serie temporelle, reduit a ce que le calcul exige.
     *
     * @param instant horodatage de la mesure
     * @param valeur  valeur relevee
     */
    record PointSerie(Instant instant, BigDecimal valeur) {
    }

    /** Le moteur est-il disponible ? Faux invite l'appelant a son propre repli. */
    boolean actif();

    /**
     * Score une serie.
     *
     * @return {@link Optional#empty()} si le moteur est absent ou indisponible —
     *         jamais d'exception : l'indisponibilite d'un service d'analyse ne
     *         doit pas faire echouer une consultation.
     */
    Optional<AnomalieReponse> scorer(Long rucheId, TypeIndicateur type, List<PointSerie> serie);
}
