package com.zumm.controller;

import com.zumm.repository.AuditEntreeRepository;
import com.zumm.web.dto.AuditEntreeReponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation du journal d'audit (US-043, SPRINT-09). Lecture seule ; l'accès est
 * réservé aux profils responsable / admin par {@code SecurityConfig}. Les entrées
 * sont restreintes au tenant courant (@TenantId + RLS).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEntreeRepository audits;

    public AuditController(AuditEntreeRepository audits) {
        this.audits = audits;
    }

    /** Les 200 entrées les plus récentes, les plus récentes d'abord. */
    @GetMapping
    public List<AuditEntreeReponse> lister() {
        return audits.findTop200ByOrderByInstantDesc().stream().map(AuditEntreeReponse::de).toList();
    }
}
