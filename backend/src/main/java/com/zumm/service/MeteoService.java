package com.zumm.service;

import com.zumm.domain.Site;
import com.zumm.repository.SiteRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MeteoReponse;
import com.zumm.web.dto.PrevisionJour;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contexte meteo local d'un site (US-029) : conditions courantes et previsions.
 *
 * <p>Selon {@code zumm.meteo.mode} : {@code auto} (defaut) interroge le fournisseur
 * externe et retombe sur une estimation deterministe en cas d'indisponibilite ;
 * {@code simulation} force l'estimation hors-ligne (utile en test et sans reseau).
 * Le repli couvre les previsions comme l'instantane : un ecran a moitie rempli
 * serait plus deroutant qu'un ecran ouvertement simule — c'est ce que dit le champ
 * {@code source}.
 *
 * <p>La correlation meteo × capteurs enrichira les alertes (perspective du cahier).
 */
@Service
@Transactional(readOnly = true)
public class MeteoService {

    /** Plafond du fournisseur ; au-dela, la demande est ramenee a cette valeur. */
    public static final int JOURS_MAX = 16;

    private final SiteRepository sites;
    private final FournisseurMeteo fournisseur;
    private final PolitiquePositions positions;
    private final String mode;

    public MeteoService(SiteRepository sites, FournisseurMeteo fournisseur,
            PolitiquePositions positions,
            @Value("${zumm.meteo.mode:auto}") String mode) {
        this.sites = sites;
        this.fournisseur = fournisseur;
        this.positions = positions;
        this.mode = mode;
    }

    /**
     * Contexte meteo d'un site.
     *
     * @param joursPrevision jours de prevision demandes ; borne a
     *                       {@code [0, JOURS_MAX]} plutot que rejetee — une valeur
     *                       hors bornes est une erreur d'appelant, pas une raison
     *                       de priver l'apiculteur de sa meteo
     */
    public MeteoReponse pourSite(Long siteId, int joursPrevision) {
        Site site = sites.findById(siteId).orElseThrow(() -> RessourceIntrouvable.de("Site", siteId));
        double lat = site.getLatitude().doubleValue();
        double lon = site.getLongitude().doubleValue();
        int jours = Math.max(0, Math.min(joursPrevision, JOURS_MAX));

        // Le fournisseur externe et la simulation travaillent sur la position EXACTE ;
        // seule la position RENDUE est masquee (SPRINT-12). Repondre la meteo d'un
        // point arrondi degraderait la donnee metier sans rien proteger de plus.
        java.math.BigDecimal[] exposee = positions.masquer(site.getLatitude(), site.getLongitude());
        double latVue = exposee[0].doubleValue();
        double lonVue = exposee[1].doubleValue();

        if (!"simulation".equalsIgnoreCase(mode)) {
            Optional<FournisseurMeteo.Releve> reel = fournisseur.releve(lat, lon, jours);
            if (reel.isPresent()) {
                FournisseurMeteo.Meteo m = reel.get().courante();
                return new MeteoReponse(siteId, latVue, lonVue, m.temperatureCelsius(),
                        m.humiditePourcent(), m.ventKmh(), "open-meteo", Instant.now(),
                        previsions(reel.get().previsions()));
            }
        }
        return simulation(siteId, latVue, lonVue, lat, lon, jours);
    }

    /** Conversion du releve du fournisseur vers le contrat expose. */
    private List<PrevisionJour> previsions(List<FournisseurMeteo.Jour> jours) {
        return jours.stream()
                .map(j -> new PrevisionJour(j.date(), j.temperatureMinCelsius(),
                        j.temperatureMaxCelsius(), j.precipitationsMm(), j.ventMaxKmh()))
                .toList();
    }

    /**
     * Estimation deterministe a partir des coordonnees : la temperature decroit avec
     * la latitude, l'humidite et le vent en derivent. Les previsions oscillent autour
     * de cette base selon le rang du jour, sans aucun aleatoire — deux appels
     * successifs rendent la meme serie, ce qui la rend testable. Suffit a une
     * demonstration hors-ligne, sans pretendre a l'exactitude meteorologique.
     */
    private MeteoReponse simulation(Long siteId, double latVue, double lonVue,
            double lat, double lon, int jours) {
        double base = Math.round((28 - Math.abs(lat) * 0.35) * 10) / 10.0;
        int humidite = (int) (55 + Math.floorMod((long) (lon * 100), 30));
        double vent = Math.round((5 + Math.abs(lon) % 15) * 10) / 10.0;

        LocalDate premier = LocalDate.now(ZoneOffset.UTC);
        List<PrevisionJour> serie = new ArrayList<>(jours);
        for (int i = 0; i < jours; i++) {
            // Amplitude jour/nuit et derive saisonniere, toutes deux fonction du rang.
            double amplitude = 4 + (i % 3);
            double derive = (i * 7) % 5 - 2;
            serie.add(new PrevisionJour(premier.plusDays(i),
                    arrondi(base - amplitude + derive),
                    arrondi(base + amplitude + derive),
                    i % 4 == 3 ? 3.2 : 0.0,
                    arrondi(vent * 1.6)));
        }
        return new MeteoReponse(siteId, latVue, lonVue, base, humidite, vent,
                "simulation", Instant.now(), List.copyOf(serie));
    }

    private double arrondi(double valeur) {
        return Math.round(valeur * 10) / 10.0;
    }
}
