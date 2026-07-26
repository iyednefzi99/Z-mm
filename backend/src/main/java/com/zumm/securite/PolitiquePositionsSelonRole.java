package com.zumm.securite;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.web.dto.SiteReponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Implementation par role de {@link PolitiquePositions} (US-003, SPRINT-12).
 *
 * <p>Regle : les profils de pilotage — {@code responsable} et {@code admin} —
 * voient la position exacte, parce que c'est leur patrimoine ; les autres profils
 * la recoivent arrondie a {@code arrondi_degres_public} decimales. A 2 decimales,
 * l'incertitude est de l'ordre du kilometre : suffisant pour situer un rucher sur
 * une carte et planifier une tournee, insuffisant pour aller le voler.
 *
 * <p>Le seuil vient de {@code ConfigZumm.ini} et se modifie a chaud : une
 * exploitation peut durcir (3 → 100 m) ou relacher la regle sans redeploiement.
 * Une valeur negative desactive l'arrondi.
 */
@Component
public class PolitiquePositionsSelonRole implements PolitiquePositions {

    /** Profils autorises a la position exacte. */
    private static final Set<String> ROLES_PROPRIETAIRES =
            Set.of("ROLE_responsable", "ROLE_admin");

    private final ConfigurationMetier configuration;

    public PolitiquePositionsSelonRole(ConfigurationMetier configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean positionExacteAutorisee() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated()) {
            return false;
        }
        return authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_PROPRIETAIRES::contains);
    }

    @Override
    public SiteReponse masquer(SiteReponse site) {
        if (site == null || positionExacteAutorisee()) {
            return site;
        }
        int decimales = configuration.seuils().arrondiDegresPublic();
        if (decimales < 0) {
            return site;
        }
        return new SiteReponse(
                site.id(),
                site.nom(),
                site.fermeId(),
                site.fermeNom(),
                arrondir(site.latitude(), decimales),
                arrondir(site.longitude(), decimales),
                // L'altitude trahit elle aussi l'emplacement en terrain accidente :
                // elle disparait avec la position exacte.
                null,
                site.dateMiseEnOeuvre(),
                site.dateDemenagement(),
                site.dateCloture(),
                site.creeLe(),
                site.majLe());
    }

    @Override
    public BigDecimal[] masquer(BigDecimal latitude, BigDecimal longitude) {
        if (positionExacteAutorisee()) {
            return new BigDecimal[] {latitude, longitude};
        }
        int decimales = configuration.seuils().arrondiDegresPublic();
        if (decimales < 0) {
            return new BigDecimal[] {latitude, longitude};
        }
        return new BigDecimal[] {arrondir(latitude, decimales), arrondir(longitude, decimales)};
    }

    private static BigDecimal arrondir(BigDecimal valeur, int decimales) {
        return valeur == null ? null : valeur.setScale(decimales, RoundingMode.HALF_UP);
    }
}
