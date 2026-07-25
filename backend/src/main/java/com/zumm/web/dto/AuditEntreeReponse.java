package com.zumm.web.dto;

import com.zumm.domain.AuditEntree;
import java.time.Instant;

/**
 * Vue exposée d'une entrée d'audit (US-043).
 */
public record AuditEntreeReponse(
        Long id,
        Instant instant,
        String acteur,
        String action,
        String entite,
        Long entiteId,
        String resume) {

    public static AuditEntreeReponse de(AuditEntree e) {
        return new AuditEntreeReponse(e.getId(), e.getInstant(), e.getActeur(),
                e.getAction(), e.getEntite(), e.getEntiteId(), e.getResume());
    }
}
