package com.zumm.web.dto;

import com.zumm.domain.CaptureEssaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Vue exposee d'une capture d'essaim (SPRINT-21). */
public record CaptureEssaimReponse(
        Long id,
        Long agentId,
        String agentNom,
        Long rucheId,
        Long siteId,
        String siteNom,
        LocalDate dateCapture,
        String origine,
        String lieu,
        BigDecimal poidsKg,
        BigDecimal hauteurM,
        String note,
        boolean logee,
        Instant creeLe) {

    public static CaptureEssaimReponse de(CaptureEssaim capture) {
        return new CaptureEssaimReponse(
                capture.getId(),
                capture.getAgent().getId(),
                capture.getAgent().getNom(),
                capture.getRuche() == null ? null : capture.getRuche().getId(),
                capture.getSite() == null ? null : capture.getSite().getId(),
                capture.getSite() == null ? null : capture.getSite().getNom(),
                capture.getDateCapture(),
                capture.getOrigine(),
                capture.getLieu(),
                capture.getPoidsKg(),
                capture.getHauteurM(),
                capture.getNote(),
                capture.getRuche() != null,
                capture.getCreeLe());
    }
}
