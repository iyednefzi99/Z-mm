package com.zumm.service;

import com.zumm.domain.PartageTelemetrie;
import com.zumm.repository.PartageTelemetrieRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.tenant.TenantContext;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.PartageCorps;
import com.zumm.web.dto.PartageReponse;
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
 * Partage d'un flux de télémétrie hors de l'exploitation (SPRINT-26, lot F1).
 *
 * <p>Ferme le 🟡 du §5 : le partage passait par l'appartenance au même tenant ;
 * montrer une courbe à un mentor, un technicien sanitaire ou un groupement était
 * impossible sans lui ouvrir un compte — c'est-à-dire sans lui ouvrir tout le
 * cheptel.
 *
 * <p><strong>Ce service reprend exactement les garanties de
 * {@link AbonnementCalendrierService}</strong>, et pour les mêmes raisons — un
 * jeton permanent dans une URL est un secret de plus, non révocable en pratique :
 *
 * <ul>
 *   <li>256 bits tirés d'un {@link SecureRandom}, <strong>jamais stockés</strong> :
 *       la base n'en garde que l'empreinte SHA-256, si bien qu'une fuite de la
 *       base ne rend aucune URL utilisable ;
 *   <li>expiration <strong>obligatoire</strong>, bornée à un an par le DTO ;
 *   <li>révocation, la ligne survivant à la coupure — savoir qu'un partage a été
 *       coupé vaut mieux que de ne plus rien savoir ;
 *   <li>usage <strong>visible</strong> : un partage jamais consulté se coupe sans
 *       hésiter, un partage lu hier appartient à quelqu'un qui s'en sert.
 * </ul>
 *
 * <p><strong>Le cloisonnement de {@code partage_telemetrie} est applicatif</strong>,
 * comme celui de {@code abonnement_calendrier} : la table porte le tenant sans le
 * discriminer, parce que c'est le jeton qui le résout. Ce service est donc le
 * seul à la toucher, et le seul endroit à auditer.
 *
 * <p><strong>Deux bornes propres à ce partage.</strong> Il porte sur UNE ruche —
 * un jeton qui ouvrirait l'exploitation ne serait plus un partage, ce serait un
 * compte sans mot de passe. Et il ne rend qu'une <strong>série journalière</strong>
 * d'un indicateur, sans identifiant, sans rucher et sans position : le
 * destinataire regarde une courbe, il n'explore pas un cheptel.
 *
 * <p>La lecture elle-même vit dans {@link FluxPartageService}, hors transaction :
 * la séparation reprend celle de {@code FluxCalendrierService}, et elle n'est pas
 * cosmétique — une méthode de ce service qui appellerait {@link #resoudre} sur
 * elle-même contournerait le proxy transactionnel, et l'horodatage d'usage ne
 * serait jamais écrit.
 */
@Service
@Transactional
public class PartageTelemetrieService {

    /** 32 octets : de quoi rendre une URL indevinable, même sans limitation de débit. */
    private static final int OCTETS_JETON = 32;

    private final PartageTelemetrieRepository partages;
    private final RucheRepository ruches;
    private final SecureRandom alea = new SecureRandom();

    public PartageTelemetrieService(PartageTelemetrieRepository partages,
            RucheRepository ruches) {
        this.partages = partages;
        this.ruches = ruches;
    }

    /**
     * Ouvre un partage et rend l'URL UNE seule fois.
     *
     * <p>La ruche est résolue par le repository ordinaire, donc sous le
     * discriminant de tenant : partager la ruche d'une autre exploitation échoue
     * ici, avant toute écriture.
     */
    public PartageReponse creer(PartageCorps corps, String baseUrl) {
        String tenant = tenantCourant();
        if (ruches.findById(corps.rucheId()).isEmpty()) {
            throw new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId());
        }
        String jeton = nouveauJeton();
        PartageTelemetrie partage = partages.saveAndFlush(new PartageTelemetrie(
                tenant, corps.rucheId(), corps.libelle().trim(), empreinte(jeton),
                Instant.now().plus(Duration.ofDays(corps.dureeJours()))));
        return PartageReponse.de(partage, Instant.now())
                .avecUrl(baseUrl + "/api/flux/" + jeton);
    }

    /** Partages ouverts sur une ruche, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<PartageReponse> lister(Long rucheId) {
        Instant maintenant = Instant.now();
        return partages.findByTenantIdAndRucheIdOrderByCreeLeDesc(tenantCourant(), rucheId)
                .stream()
                .map(partage -> PartageReponse.de(partage, maintenant))
                .toList();
    }

    /** Révoque un partage. La ligne reste : elle documente la coupure. */
    public PartageReponse revoquer(Long id) {
        PartageTelemetrie partage = partages.findByIdAndTenantId(id, tenantCourant())
                .orElseThrow(() -> RessourceIntrouvable.de("Partage", id));
        if (partage.getRevoqueLe() == null) {
            partage.revoquer(Instant.now());
        }
        return PartageReponse.de(partage, Instant.now());
    }

    /**
     * Résout un jeton et marque son usage. Transactionnel, et court.
     *
     * <p>Rend un {@link Optional} vide dans TOUS les cas de refus — jeton
     * inconnu, révoqué, expiré — et le contrôleur répond alors 404. Distinguer
     * les trois confirmerait à un appelant qu'un jeton a existé, ce qui est déjà
     * une information de trop.
     */
    public Optional<PartageTelemetrie> resoudre(String jeton) {
        Instant maintenant = Instant.now();
        return partages.findByJetonEmpreinte(empreinte(jeton))
                .filter(partage -> partage.utilisable(maintenant))
                .map(partage -> {
                    partage.marquerUtilise(maintenant);
                    return partage;
                });
    }

    private String nouveauJeton() {
        byte[] octets = new byte[OCTETS_JETON];
        alea.nextBytes(octets);
        // Sans remplissage : le jeton voyage dans un CHEMIN d'URL, où « = » et
        // « / » demanderaient un échappement que tous les clients ne font pas.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** SHA-256 hexadécimal : ce que la base stocke, et la seule chose comparée. */
    private static String empreinte(String jeton) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 est exigé de toute JVM : cette branche ne s'atteint pas.
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }

    private static String tenantCourant() {
        return TenantContext.courant()
                .filter(tenant -> !tenant.isBlank())
                .orElseThrow(() -> new RequeteInvalide(
                        "Aucune exploitation dans le contexte de la requête."));
    }
}
