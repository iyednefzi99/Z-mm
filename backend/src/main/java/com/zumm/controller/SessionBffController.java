package com.zumm.controller;

import com.zumm.tenant.TenantFilter;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Etat de la session du navigateur (ADR-006).
 *
 * <p>Depuis le passage au BFF, la PWA ne detient plus de jeton : elle ne peut
 * donc plus lire elle-meme qui est connecte ni avec quels roles. Cet endpoint
 * est la seule fenetre qu'elle ait sur sa propre session — d'ou son role
 * structurant, malgre sa taille.
 *
 * <p>Ce qui en sort est deliberement MAIGRE : un nom d'affichage, des roles, une
 * exploitation. Aucun jeton, aucune date d'expiration, aucun identifiant interne
 * du fournisseur. Tout ce qui sort d'ici devient lisible par un script de la
 * page ; on n'y met donc que ce dont l'interface a besoin pour afficher un menu
 * et masquer des actions.
 *
 * <p>Le masquage cote client n'est PAS une mesure de securite : c'est du confort.
 * L'autorisation reste posee par la matrice RBAC du serveur, qui refuse
 * independamment de ce que l'interface affiche.
 */
@RestController
@RequestMapping("/bff")
public class SessionBffController {

    /** Vue de session exposee au navigateur. */
    public record SessionReponse(String utilisateur, List<String> roles, String exploitation) {
    }

    /**
     * Session courante, ou 401 si le navigateur n'en a pas.
     *
     * <p>401 et non un corps vide : la PWA distingue ainsi « pas connecte » de
     * « connecte sans role », deux situations qui appellent des ecrans differents.
     */
    @GetMapping("/session")
    public ResponseEntity<SessionReponse> session(Authentication authentification) {
        if (authentification == null || !authentification.isAuthenticated()
                || !(authentification.getPrincipal() instanceof OidcUser utilisateur)) {
            return ResponseEntity.status(401).build();
        }

        List<String> roles = authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autorite -> autorite.startsWith("ROLE_"))
                .map(autorite -> autorite.substring("ROLE_".length()))
                .sorted()
                .toList();

        return ResponseEntity.ok(new SessionReponse(
                utilisateur.getPreferredUsername() != null
                        ? utilisateur.getPreferredUsername()
                        : utilisateur.getSubject(),
                roles,
                utilisateur.getClaimAsString(TenantFilter.CLAIM_TENANT)));
    }
}
