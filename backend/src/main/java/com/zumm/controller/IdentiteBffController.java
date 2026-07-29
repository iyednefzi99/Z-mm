package com.zumm.controller;

import com.zumm.config.SecurityConfig;
import com.zumm.service.IdentiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion et inscription menees depuis l'application (ADR-009).
 *
 * <p>Ces deux endpoints sont la contrepartie serveur des ecrans de Zumm :
 * l'utilisateur ne voit plus les pages de Keycloak, que le BFF appelle en
 * arriere-plan. Ce qui SORT d'ici n'a pas change pour autant — un cookie de
 * session, et rien d'autre. Aucun jeton ne redescend au navigateur (ADR-006).
 *
 * <p>Ils sont ouverts, et ne peuvent pas ne pas l'etre : on ne demande pas
 * d'etre connecte pour se connecter. Trois garde-fous compensent :
 *
 * <ul>
 *   <li>CSRF reste EXIGE. Un endpoint ouvert reste une mutation sur une origine
 *       a cookie : sans jeton, un site tiers pourrait faire consommer une place
 *       d'invitation ou declencher des tentatives a l'insu de l'utilisateur ;
 *   <li>l'identifiant de session est REGENERE a l'entree. Sans cela, un jeton de
 *       session pose avant la connexion resterait valable apres — c'est la
 *       fixation de session, et c'est exactement ce que fait Spring Security sur
 *       le chemin par redirection ;
 *   <li>le motif d'echec renvoye est un code ferme, choisi par le serveur parmi
 *       une liste connue de l'interface. Le message affiche est ecrit par la PWA,
 *       traduit dans les trois langues, et le fournisseur ne peut rien injecter
 *       dans la page.
 * </ul>
 */
@RestController
@RequestMapping("/bff")
public class IdentiteBffController {

    /** Demande de connexion. Le mot de passe n'est ni journalise ni conserve. */
    public record DemandeConnexion(
            @NotBlank @Size(max = 120) String identifiant,
            @NotBlank @Size(max = 200) String motDePasse) {
    }

    /** Demande de creation de compte, rattachee par un code d'invitation. */
    public record DemandeInscription(
            @NotBlank @Size(max = 120) String nom,
            @NotBlank @Email @Size(max = 180) String courriel,
            // 12 caracteres : la politique du royaume la refusera de toute facon,
            // mais l'echec doit etre visible AVANT d'aller deranger Keycloak.
            @NotBlank @Size(min = 12, max = 200) String motDePasse,
            @NotBlank @Size(max = 32) String code) {
    }

    private final IdentiteService identite;
    private final JwtDecoder decodeur;
    private final SecurityContextRepository sessions = new HttpSessionSecurityContextRepository();

    public IdentiteBffController(IdentiteService identite, JwtDecoder decodeur) {
        this.identite = identite;
        this.decodeur = decodeur;
    }

    /** Ouvre une session applicative a partir d'identifiants. */
    @PostMapping("/connexion")
    public ResponseEntity<Map<String, String>> connexion(
            @Valid @RequestBody DemandeConnexion demande,
            HttpServletRequest requete,
            HttpServletResponse reponse) {
        try {
            IdentiteService.Jetons jetons =
                    identite.connexion(demande.identifiant(), demande.motDePasse());
            ouvrirSession(jetons, requete, reponse);
            return ResponseEntity.noContent().build();
        } catch (IdentiteService.EchecIdentite echec) {
            return refus(echec);
        }
    }

    /** Cree un compte, puis laisse la PWA enchainer la connexion. */
    @PostMapping("/inscription")
    public ResponseEntity<Map<String, String>> inscription(
            @Valid @RequestBody DemandeInscription demande) {
        try {
            identite.inscription(demande.nom(), demande.courriel(), demande.motDePasse(),
                    demande.code());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IdentiteService.EchecIdentite echec) {
            return refus(echec);
        }
    }

    /**
     * Pose la session a partir des jetons obtenus.
     *
     * <p>L'identite est batie sur le jeton d'ACCES, deja valide par le decodeur
     * de l'application — signature, emetteur ET audience. Le jeton d'identite
     * porterait les memes claims utiles, mais son audience est le client BFF, pas
     * l'API : le valider demanderait un second decodeur aux regles differentes,
     * c'est-a-dire un second endroit ou se tromper. Les claims dont
     * {@code /bff/session} et la matrice RBAC ont besoin — {@code sub},
     * {@code preferred_username}, {@code realm_access}, {@code tenant_id} — sont
     * tous portes par le jeton d'acces.
     */
    private void ouvrirSession(IdentiteService.Jetons jetons, HttpServletRequest requete,
            HttpServletResponse reponse) {
        Jwt jeton = decodeur.decode(jetons.jetonAcces());
        Collection<GrantedAuthority> autorites = SecurityConfig.autoritesDeJeton(jeton);

        DefaultOidcUser utilisateur = new DefaultOidcUser(
                autorites,
                new OidcIdToken(jeton.getTokenValue(), jeton.getIssuedAt(), jeton.getExpiresAt(),
                        jeton.getClaims()),
                "preferred_username");

        // Regeneration AVANT l'enregistrement : l'identifiant qu'un attaquant
        // aurait pu faire poser au navigateur ne vaut plus rien une fois la
        // session authentifiee.
        requete.getSession(true);
        requete.changeSessionId();

        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(
                new OAuth2AuthenticationToken(utilisateur, autorites, "keycloak"));
        SecurityContextHolder.setContext(contexte);
        sessions.saveContext(contexte, requete, reponse);
    }

    /**
     * Traduit un echec en reponse.
     *
     * <p>401 pour un refus d'identifiants, 422 pour une demande recevable mais
     * inexploitable (code inconnu, mot de passe refuse), 409 pour un conflit,
     * 503 pour un fournisseur injoignable. Le corps ne porte QUE le code : pas de
     * message du fournisseur, pas de trace technique.
     */
    private ResponseEntity<Map<String, String>> refus(IdentiteService.EchecIdentite echec) {
        HttpStatus statut = switch (echec.motif()) {
            case IDENTIFIANTS_INVALIDES -> HttpStatus.UNAUTHORIZED;
            case COMPTE_SUSPENDU -> HttpStatus.FORBIDDEN;
            case COURRIEL_DEJA_PRIS -> HttpStatus.CONFLICT;
            case CODE_INCONNU, MOT_DE_PASSE_REFUSE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INDISPONIBLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(statut).body(Map.of("code", echec.motif().code()));
    }
}
