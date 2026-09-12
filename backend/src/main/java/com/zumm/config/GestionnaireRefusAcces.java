package com.zumm.config;

import com.zumm.securite.DetecteurAnomalieAcces;
import com.zumm.securite.IdentiteAppelant;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

/**
 * Journalise un refus RBAC avant de repondre (SPRINT-34).
 *
 * <p>Avant cette classe, un 403 ne laissait AUCUNE trace — ni dans le journal
 * d'audit, ni ailleurs : {@code REVUE-CONSOLIDEE.md} §5 relevait depuis le
 * SPRINT-09 que le journal enregistre, mais que personne ne le lit en continu.
 * {@link DetecteurAnomalieAcces} comble ce trou et alerte au-dela d'un seuil de
 * refus repetes ; cette classe est le seul point ou Spring Security appelle
 * l'application au moment du refus — donc le seul endroit possible pour le
 * brancher.
 *
 * <p>La reponse HTTP elle-meme n'est pas reimplementee : {@link AccessDeniedHandlerImpl}
 * est reutilise tel quel, pour que le contrat cote client (statut, corps) ne
 * change pas d'un octet par rapport a avant cette classe.
 */
public class GestionnaireRefusAcces implements AccessDeniedHandler {

    private final DetecteurAnomalieAcces detecteur;
    private final AccessDeniedHandler delegue = new AccessDeniedHandlerImpl();

    public GestionnaireRefusAcces(DetecteurAnomalieAcces detecteur) {
        this.detecteur = detecteur;
    }

    @Override
    public void handle(HttpServletRequest requete, HttpServletResponse reponse, AccessDeniedException exception)
            throws IOException, ServletException {
        String acteur = IdentiteAppelant.de(SecurityContextHolder.getContext().getAuthentication()).nomPourAudit();
        detecteur.surRefus(acteur, requete.getMethod() + " " + requete.getRequestURI());
        delegue.handle(requete, reponse, exception);
    }
}
