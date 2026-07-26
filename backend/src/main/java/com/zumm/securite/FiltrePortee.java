package com.zumm.securite;

import com.zumm.tenant.PorteeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pose la portee d'autorisation de l'appelant pour la duree de la requete
 * (US-057).
 *
 * <p>Vient necessairement APRES {@code TenantFilter} : resoudre l'agent suppose
 * une lecture en base, donc une exploitation deja fixee. L'ordre n'est pas un
 * detail — inverse, la resolution lirait la table `agent` sans tenant et ne
 * trouverait rien.
 *
 * <p>Le nettoyage est assure par {@code TenantFilter}, qui efface les deux
 * contextes dans son bloc {@code finally} : un {@code ThreadLocal} laisse en place
 * fuiterait sur la requete suivante servie par le meme fil — et fuiterait ici une
 * PORTEE, c'est-a-dire un droit de lecture.
 */
public class FiltrePortee extends OncePerRequestFilter {

    private final ResolveurPortee resolveur;

    public FiltrePortee(ResolveurPortee resolveur) {
        this.resolveur = resolveur;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
            FilterChain chaine) throws ServletException, IOException {
        PorteeContext.definir(
                resolveur.resoudre(SecurityContextHolder.getContext().getAuthentication()));
        chaine.doFilter(requete, reponse);
    }
}
