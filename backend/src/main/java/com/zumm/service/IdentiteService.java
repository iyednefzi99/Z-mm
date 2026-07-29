package com.zumm.service;

import com.zumm.repository.InvitationRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Dialogue avec Keycloak pour le compte du navigateur (ADR-009).
 *
 * <p>Zumm presente ses propres ecrans de connexion et d'inscription ; le
 * fournisseur d'identite est appele ICI, en arriere-plan. Ce service est donc le
 * seul endroit du code ou transitent des mots de passe, et le seul qui detienne
 * le secret d'administration.
 *
 * <p><strong>Ce que ce choix coute.</strong> L'echange direct
 * ({@code grant_type=password}) est deconseille par OAuth 2.1 pour de bonnes
 * raisons : il place l'application sur le chemin du mot de passe, et il court-
 * circuite tout ce que le fournisseur sait faire ensuite — second facteur,
 * federation, consentement, ecran de mot de passe expire. La redirection reste
 * donc offerte a cote, et c'est le SEUL chemin pour la federation Google.
 *
 * <p><strong>Ce qu'il preserve.</strong> Le navigateur ne recoit toujours aucun
 * jeton : les jetons obtenus ici restent cote serveur, dans la session, et le
 * navigateur ne repart qu'avec un cookie {@code HttpOnly} (ADR-006).
 */
@Service
public class IdentiteService {

    /** Roles qu'une invitation peut attribuer. Jamais {@code admin}. */
    private static final List<String> ROLES_INVITABLES =
            List.of("apiculteur", "superviseur", "responsable");

    /** Attribut Keycloak portant l'exploitation ; le mapper le recopie en claim. */
    private static final String ATTRIBUT_TENANT = "tenant_id";

    private final RestClient client;
    private final InvitationRepository invitations;
    private final String emetteur;
    private final String clientId;
    private final String secret;

    public IdentiteService(
            RestClient.Builder constructeur,
            InvitationRepository invitations,
            @Value("${ZUMM_OIDC_ISSUER_URI:http://localhost:8081/realms/zumm}") String emetteur,
            @Value("${ZUMM_BFF_CLIENT:zumm-bff}") String clientId,
            @Value("${ZUMM_BFF_SECRET:secret-bff-dev}") String secret) {
        this.client = constructeur.build();
        this.invitations = invitations;
        this.emetteur = emetteur;
        this.clientId = clientId;
        this.secret = secret;
    }

    /** Motifs d'echec, traduits par la PWA. Le libelle n'est jamais renvoye. */
    public enum Echec {
        IDENTIFIANTS_INVALIDES("identifiants-invalides"),
        COMPTE_SUSPENDU("compte-suspendu"),
        CODE_INCONNU("code-inconnu"),
        COURRIEL_DEJA_PRIS("courriel-deja-pris"),
        MOT_DE_PASSE_REFUSE("mot-de-passe-refuse"),
        INDISPONIBLE("indisponible");

        private final String code;

        Echec(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Echec d'identite portant un motif exploitable par l'interface. */
    public static class EchecIdentite extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Echec motif;

        public EchecIdentite(Echec motif) {
            super(motif.code());
            this.motif = motif;
        }

        public Echec motif() {
            return motif;
        }
    }

    /** Jetons obtenus pour un utilisateur ; ils ne quittent pas le serveur. */
    public record Jetons(String jetonAcces, String jetonIdentite, String jetonRafraichissement,
            long dureeSecondes) {
    }

    /**
     * Echange un identifiant et un mot de passe contre des jetons.
     *
     * <p>Toutes les erreurs du fournisseur sont ramenees a un motif unique :
     * un compte inexistant et un mot de passe faux doivent etre indiscernables,
     * faute de quoi le formulaire devient un annuaire des comptes existants.
     * Seul le compte desactive fait exception — l'utilisateur doit savoir qu'il
     * lui faut demander la reouverture, et l'information n'apprend rien a qui ne
     * connait deja le mot de passe.
     */
    public Jetons connexion(String identifiant, String motDePasse) {
        MultiValueMap<String, String> corps = new LinkedMultiValueMap<>();
        corps.add("grant_type", "password");
        corps.add("client_id", clientId);
        corps.add("client_secret", secret);
        corps.add("username", identifiant);
        corps.add("password", motDePasse);
        corps.add("scope", "openid profile email");

        Map<?, ?> reponse;
        try {
            reponse = client.post()
                    .uri(emetteur + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(corps)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (requete, erreur) -> {
                        throw new EchecIdentite(motifDeRefus(erreur.getStatusCode()));
                    })
                    .body(Map.class);
        } catch (EchecIdentite echec) {
            throw echec;
        } catch (RuntimeException erreur) {
            // Fournisseur injoignable : distinct d'un refus, et l'utilisateur
            // doit le savoir — retaper son mot de passe n'y changerait rien.
            throw new EchecIdentite(Echec.INDISPONIBLE);
        }
        if (reponse == null || reponse.get("access_token") == null) {
            throw new EchecIdentite(Echec.INDISPONIBLE);
        }
        return new Jetons(
                String.valueOf(reponse.get("access_token")),
                reponse.get("id_token") == null ? null : String.valueOf(reponse.get("id_token")),
                reponse.get("refresh_token") == null ? null
                        : String.valueOf(reponse.get("refresh_token")),
                reponse.get("expires_in") instanceof Number duree ? duree.longValue()
                        : Duration.ofMinutes(5).toSeconds());
    }

    /**
     * Cree un compte rattache a l'exploitation portee par le code d'invitation.
     *
     * <p>L'ORDRE des deux operations est structurant : la place est reservee
     * AVANT la creation du compte. L'inverse laisserait, en cas d'echec de la
     * reservation, un compte sans {@code tenant_id} — capable de se connecter et
     * refuse par {@code TenantFilter} sur chaque ecran. Si la creation echoue
     * ensuite, la place est rendue.
     */
    public void inscription(String nom, String courriel, String motDePasse, String code) {
        var invitation = invitations.reserver(code)
                .orElseThrow(() -> new EchecIdentite(Echec.CODE_INCONNU));
        if (!ROLES_INVITABLES.contains(invitation.role())) {
            invitations.relacher(code);
            throw new EchecIdentite(Echec.CODE_INCONNU);
        }
        try {
            creerCompte(nom, courriel, motDePasse, invitation.tenantId(), invitation.role());
        } catch (RuntimeException erreur) {
            invitations.relacher(code);
            throw erreur;
        }
    }

    /** Creation effective chez le fournisseur, via son API d'administration. */
    private void creerCompte(String nom, String courriel, String motDePasse, String tenant,
            String role) {
        String jetonAdmin = jetonDeService();
        String royaume = emetteur.substring(emetteur.lastIndexOf('/') + 1);
        String base = emetteur.substring(0, emetteur.indexOf("/realms/"));

        int espace = nom.trim().indexOf(' ');
        String prenom = espace > 0 ? nom.trim().substring(0, espace) : nom.trim();
        String patronyme = espace > 0 ? nom.trim().substring(espace + 1) : "";

        Map<String, Object> utilisateur = Map.of(
                "username", courriel,
                "email", courriel,
                "firstName", prenom,
                "lastName", patronyme,
                "enabled", true,
                // Le courriel n'est PAS marque verifie : rien ne prouve encore
                // que l'adresse appartient a celui qui s'inscrit.
                "emailVerified", false,
                "attributes", Map.of(ATTRIBUT_TENANT, List.of(tenant)),
                "credentials", List.of(Map.of(
                        "type", "password", "value", motDePasse, "temporary", false)),
                "realmRoles", List.of(role));

        client.post()
                .uri(base + "/admin/realms/" + royaume + "/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jetonAdmin)
                .body(utilisateur)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (requete, erreur) -> {
                    throw new EchecIdentite(motifDeCreation(erreur.getStatusCode()));
                })
                .toBodilessEntity();
    }

    /** Jeton du compte de service, pour l'API d'administration du royaume. */
    private String jetonDeService() {
        MultiValueMap<String, String> corps = new LinkedMultiValueMap<>();
        corps.add("grant_type", "client_credentials");
        corps.add("client_id", clientId);
        corps.add("client_secret", secret);
        try {
            Map<?, ?> reponse = client.post()
                    .uri(emetteur + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(corps)
                    .retrieve()
                    .body(Map.class);
            if (reponse == null || reponse.get("access_token") == null) {
                throw new EchecIdentite(Echec.INDISPONIBLE);
            }
            return String.valueOf(reponse.get("access_token"));
        } catch (EchecIdentite echec) {
            throw echec;
        } catch (RuntimeException erreur) {
            throw new EchecIdentite(Echec.INDISPONIBLE);
        }
    }

    /** 401/400 du point de jeton : toujours le meme motif, sauf compte ferme. */
    private static Echec motifDeRefus(HttpStatusCode statut) {
        if (statut.value() == 403) {
            return Echec.COMPTE_SUSPENDU;
        }
        return statut.is4xxClientError() ? Echec.IDENTIFIANTS_INVALIDES : Echec.INDISPONIBLE;
    }

    /** 409 : l'adresse est deja prise. 400 : la politique de mot de passe refuse. */
    private static Echec motifDeCreation(HttpStatusCode statut) {
        return switch (statut.value()) {
            case 409 -> Echec.COURRIEL_DEJA_PRIS;
            case 400 -> Echec.MOT_DE_PASSE_REFUSE;
            default -> Echec.INDISPONIBLE;
        };
    }
}
