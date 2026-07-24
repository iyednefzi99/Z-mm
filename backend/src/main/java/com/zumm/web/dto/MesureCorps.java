package com.zumm.web.dto;

import com.zumm.domain.TypeIndicateur;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Corps d'ingestion d'une mesure de capteur (US-017), commun aux entrees REST et
 * au pont MQTT. {@code instant} est optionnel : par defaut l'instant de reception.
 *
 * @param rucheId        ruche mesuree, obligatoire ; doit exister dans le tenant
 * @param typeIndicateur poids / temperature / humidite / activite
 * @param valeur         valeur mesuree (unite de reference de l'indicateur)
 * @param instant        horodatage de la mesure ; defaut = maintenant
 */
public record MesureCorps(
        @NotNull Long rucheId,
        @NotNull TypeIndicateur typeIndicateur,
        @NotNull BigDecimal valeur,
        Instant instant) {
}
