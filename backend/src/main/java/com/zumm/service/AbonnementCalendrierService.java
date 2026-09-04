package com.zumm.service;

import com.zumm.domain.AbonnementCalendrier;
import com.zumm.repository.AbonnementCalendrierRepository;
import com.zumm.repository.AgentRepository;
import com.zumm.tenant.TenantContext;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.AbonnementCorps;
import com.zumm.web.dto.AbonnementReponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abonnements iCalendar (SPRINT-21).
 *
 * <p>Ce service leve l'objection qui avait fait ecarter l'abonnement au profit
 * du seul telechargement — « un jeton permanent dans une URL est un secret de
 * plus, non revocable en pratique » — plutot que de la contourner :
 *
 * <ul>
 *   <li>le jeton fait <strong>256 bits</strong> tires d'un {@link SecureRandom},
 *       et n'est <strong>jamais stocke</strong> : la base n'en garde que
 *       l'empreinte SHA-256, si bien qu'une fuite ne rend aucune URL utilisable ;
 *   <li>il <strong>expire</strong>, obligatoirement, et la duree est bornee a un
 *       an par le DTO — il n'existe pas d'option « sans expiration » ;
 *   <li>il se <strong>revoque</strong>, et la ligne survit a sa revocation :
 *       savoir qu'un abonnement a ete coupe vaut mieux que de ne plus rien
 *       savoir ;
 *   <li>son usage est <strong>visible</strong> ({@code derniereUtilisation}).
 * </ul>
 *
 * <p><strong>Le cloisonnement de cette table est applicatif.</strong>
 * {@code abonnement_calendrier} n'a ni {@code @TenantId} ni politique RLS —
 * elle est lue avant que le tenant soit connu, pour le resoudre. C'est donc ici,
 * et nulle part ailleurs, que le tenant doit etre cite : chaque methode de
 * gestion le lit dans {@link TenantContext} et le passe explicitement au
 * repository. Ce service est le seul a toucher cette table, et c'est le seul
 * endroit a auditer.
 */
@Service
@Transactional
public class AbonnementCalendrierService {

    /** 32 octets : de quoi rendre une URL indevinable, meme sans limitation de debit. */
    private static final int OCTETS_JETON = 32;

    private final AbonnementCalendrierRepository abonnements;
    private final AgentRepository agents;
    private final SecureRandom alea = new SecureRandom();

    public AbonnementCalendrierService(AbonnementCalendrierRepository abonnements,
            AgentRepository agents) {
        this.abonnements = abonnements;
        this.agents = agents;
    }

    /**
     * Emet un abonnement et rend l'URL UNE seule fois.
     *
     * <p>L'agent est resolu par le repository ordinaire, donc sous le
     * discriminant de tenant : demander un abonnement sur l'agent d'une autre
     * exploitation echoue ici, avant toute ecriture.
     */
    public AbonnementReponse creer(AbonnementCorps corps, String baseUrl) {
        String tenant = tenantCourant();
        if (agents.findById(corps.agentId()).isEmpty()) {
            throw new RequeteInvalide("Agent inconnu dans ce tenant : " + corps.agentId());
        }
        String jeton = nouveauJeton();
        AbonnementCalendrier abonnement = new AbonnementCalendrier(
                tenant, corps.agentId(), corps.libelle().trim(), empreinte(jeton),
                Instant.now().plus(Duration.ofDays(corps.dureeJours())));
        AbonnementCalendrier enregistre = abonnements.saveAndFlush(abonnement);
        return AbonnementReponse.de(enregistre, Instant.now())
                .avecUrl(baseUrl + "/api/calendrier/" + jeton + ".ics");
    }

    /** Abonnements d'un agent, du plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<AbonnementReponse> lister(Long agentId) {
        Instant maintenant = Instant.now();
        return abonnements.findByTenantIdAndAgentIdOrderByCreeLeDesc(tenantCourant(), agentId)
                .stream()
                .map(abonnement -> AbonnementReponse.de(abonnement, maintenant))
                .toList();
    }

    /**
     * Revoque un abonnement. La ligne reste : elle documente la coupure, et une
     * URL revoquee doit continuer a repondre 404 plutot que de redevenir libre si
     * le meme jeton etait un jour retire au sort.
     */
    public AbonnementReponse revoquer(Long id) {
        AbonnementCalendrier abonnement = abonnements
                .findByIdAndTenantId(id, tenantCourant())
                .orElseThrow(() -> RessourceIntrouvable.de("Abonnement", id));
        if (abonnement.getRevoqueLe() == null) {
            abonnement.revoquer(Instant.now());
        }
        return AbonnementReponse.de(abonnement, Instant.now());
    }

    /**
     * Resout un jeton en abonnement utilisable, et marque l'usage.
     *
     * <p>Rend un {@link Optional} vide dans TOUS les cas de refus — jeton inconnu,
     * revoque, expire — et le controleur repond alors 404. Distinguer les trois
     * confirmerait a un appelant qu'un jeton a existe, ce qui est deja une
     * information de trop.
     */
    public Optional<AbonnementCalendrier> resoudre(String jeton) {
        Instant maintenant = Instant.now();
        return abonnements.findByJetonEmpreinte(empreinte(jeton))
                .filter(abonnement -> abonnement.utilisable(maintenant))
                .map(abonnement -> {
                    abonnement.marquerUtilise(maintenant);
                    return abonnement;
                });
    }

    private String nouveauJeton() {
        byte[] octets = new byte[OCTETS_JETON];
        alea.nextBytes(octets);
        // Sans remplissage : le jeton voyage dans un CHEMIN d'URL, ou « = » et
        // « / » demanderaient un echappement que les clients de calendrier ne
        // font pas tous.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** SHA-256 hexadecimal : ce que la base stocke, et la seule chose comparee. */
    private static String empreinte(String jeton) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 est exige de toute JVM : cette branche ne s'atteint pas.
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }

    private static String tenantCourant() {
        return TenantContext.courant()
                .filter(tenant -> !tenant.isBlank())
                .orElseThrow(() -> new RequeteInvalide(
                        "Aucune exploitation dans le contexte de la requete."));
    }
}
