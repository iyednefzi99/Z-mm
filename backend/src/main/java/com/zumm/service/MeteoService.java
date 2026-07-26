package com.zumm.service;

import com.zumm.domain.Site;
import com.zumm.repository.SiteRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MeteoReponse;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contexte meteo local d'un site (US-029).
 *
 * <p>Selon {@code zumm.meteo.mode} : {@code auto} (defaut) interroge le fournisseur
 * externe et retombe sur une estimation deterministe en cas d'indisponibilite ;
 * {@code simulation} force l'estimation hors-ligne (utile en test et sans reseau).
 * La correlation meteo × capteurs enrichira les alertes (perspective du cahier).
 */
@Service
@Transactional(readOnly = true)
public class MeteoService {

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

    public MeteoReponse pourSite(Long siteId) {
        Site site = sites.findById(siteId).orElseThrow(() -> RessourceIntrouvable.de("Site", siteId));
        double lat = site.getLatitude().doubleValue();
        double lon = site.getLongitude().doubleValue();

        // Le fournisseur externe et la simulation travaillent sur la position EXACTE ;
        // seule la position RENDUE est masquee (SPRINT-12). Repondre la meteo d'un
        // point arrondi degraderait la donnee metier sans rien proteger de plus.
        java.math.BigDecimal[] exposee = positions.masquer(site.getLatitude(), site.getLongitude());
        double latVue = exposee[0].doubleValue();
        double lonVue = exposee[1].doubleValue();

        if (!"simulation".equalsIgnoreCase(mode)) {
            Optional<FournisseurMeteo.Meteo> reelle = fournisseur.courante(lat, lon);
            if (reelle.isPresent()) {
                FournisseurMeteo.Meteo m = reelle.get();
                return new MeteoReponse(siteId, latVue, lonVue, m.temperatureCelsius(),
                        m.humiditePourcent(), m.ventKmh(), "open-meteo", Instant.now());
            }
        }
        MeteoReponse simulee = simulation(siteId, lat, lon);
        return new MeteoReponse(siteId, latVue, lonVue, simulee.temperatureCelsius(),
                simulee.humiditePourcent(), simulee.ventKmh(), simulee.source(), simulee.instant());
    }

    /**
     * Estimation deterministe a partir des coordonnees : la temperature decroit avec
     * la latitude, l'humidite et le vent en derivent. Suffit a une demonstration
     * hors-ligne, sans pretendre a l'exactitude meteorologique.
     */
    private MeteoReponse simulation(Long siteId, double lat, double lon) {
        double temperature = Math.round((28 - Math.abs(lat) * 0.35) * 10) / 10.0;
        int humidite = (int) (55 + Math.floorMod((long) (lon * 100), 30));
        double vent = Math.round((5 + Math.abs(lon) % 15) * 10) / 10.0;
        return new MeteoReponse(siteId, lat, lon, temperature, humidite, vent, "simulation", Instant.now());
    }
}
