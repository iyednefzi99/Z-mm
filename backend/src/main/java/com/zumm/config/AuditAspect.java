package com.zumm.config;

import com.zumm.domain.AuditEntree;
import com.zumm.repository.AuditEntreeRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Journalisation d'audit transversale (US-043, SPRINT-09).
 *
 * <p>Un aspect intercepte les méthodes d'écriture des services métier
 * ({@code creer}, {@code mettreAJour}, {@code supprimer}) et dépose une entrée dans
 * le journal d'audit APRÈS un retour normal — dans la même transaction que
 * l'opération, donc atomique avec elle. L'entité concernée est déduite du nom du
 * service, l'identifiant du résultat (création/màj) ou de l'argument (suppression),
 * et l'acteur du jeton JWT (US-020).
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditEntreeRepository audits;

    public AuditAspect(AuditEntreeRepository audits) {
        this.audits = audits;
    }

    @AfterReturning(pointcut = "execution(* com.zumm.service..*.creer(..))", returning = "resultat")
    public void surCreation(JoinPoint jp, Object resultat) {
        enregistrer(jp, AuditEntree.CREATION, idDuResultat(resultat));
    }

    @AfterReturning(pointcut = "execution(* com.zumm.service..*.mettreAJour(..))", returning = "resultat")
    public void surModification(JoinPoint jp, Object resultat) {
        enregistrer(jp, AuditEntree.MODIFICATION, idDuResultat(resultat));
    }

    @AfterReturning(pointcut = "execution(* com.zumm.service..*.supprimer(..))")
    public void surSuppression(JoinPoint jp) {
        enregistrer(jp, AuditEntree.SUPPRESSION, premierIdArgument(jp));
    }

    private void enregistrer(JoinPoint jp, String action, Long entiteId) {
        String entite = entiteDe(jp);
        String acteur = acteurCourant();
        String resume = "%s %s %s".formatted(action, entite, entiteId == null ? "" : "#" + entiteId).trim();
        audits.save(new AuditEntree(acteur, action, entite, entiteId, resume));
        log.debug("Audit : {} par {} sur {} {}", action, acteur, entite, entiteId);
    }

    /** Nom de l'entité, déduit du service intercepté ({@code RucheService} → {@code Ruche}). */
    private String entiteDe(JoinPoint jp) {
        String service = jp.getTarget().getClass().getSimpleName();
        return service.endsWith("Service") ? service.substring(0, service.length() - "Service".length()) : service;
    }

    /** Identifiant porté par un DTO de réponse (accesseur {@code id()}), ou null. */
    private Long idDuResultat(Object resultat) {
        if (resultat == null) {
            return null;
        }
        try {
            Object valeur = resultat.getClass().getMethod("id").invoke(resultat);
            return (valeur instanceof Long l) ? l : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Premier argument de type {@code Long} (cas de {@code supprimer(Long id)}). */
    private Long premierIdArgument(JoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg instanceof Long l) {
                return l;
            }
        }
        return null;
    }

    /** Nom d'utilisateur du jeton JWT courant, ou « systeme » hors contexte authentifié. */
    private String acteurCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "systeme";
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String nom = jwt.getClaimAsString("preferred_username");
            if (nom != null && !nom.isBlank()) {
                return nom;
            }
        }
        String nom = auth.getName();
        return (nom == null || nom.isBlank()) ? "systeme" : nom;
    }
}
