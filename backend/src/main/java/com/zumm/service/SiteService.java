package com.zumm.service;

import com.zumm.domain.Ferme;
import com.zumm.domain.Site;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.SiteCorps;
import com.zumm.web.dto.SiteReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations metier sur les sites (US-003), geolocalises et rattaches a une ferme.
 *
 * <p>Applique les contraintes de composition (US-006) qui croisent plusieurs
 * champs — l'ordre des dates de cycle de vie — pour renvoyer un 400 clair. Les
 * bornes de coordonnees sont deja validees sur le DTO, et les contraintes
 * {@code CHECK} en base restent le garde-fou ultime. Les DTO sont construits dans
 * la transaction (la ferme, chargee paresseusement, y est accessible).
 */
@Service
@Transactional
public class SiteService {

    private final SiteRepository sites;
    private final FermeRepository fermes;

    public SiteService(SiteRepository sites, FermeRepository fermes) {
        this.sites = sites;
        this.fermes = fermes;
    }

    public SiteReponse creer(SiteCorps corps) {
        verifierDates(corps);
        Site site = new Site(corps.nom(), fermeRequise(corps.fermeId()),
                corps.latitude(), corps.longitude(), corps.dateMiseEnOeuvre());
        appliquerOptionnels(site, corps);
        return SiteReponse.de(sites.save(site));
    }

    @Transactional(readOnly = true)
    public List<SiteReponse> lister() {
        return sites.findAll().stream().map(SiteReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public SiteReponse obtenir(Long id) {
        return SiteReponse.de(entite(id));
    }

    public SiteReponse mettreAJour(Long id, SiteCorps corps) {
        verifierDates(corps);
        Site site = entite(id);
        site.setNom(corps.nom());
        site.setFerme(fermeRequise(corps.fermeId()));
        site.setLatitude(corps.latitude());
        site.setLongitude(corps.longitude());
        appliquerOptionnels(site, corps);
        return SiteReponse.de(site);
    }

    public void supprimer(Long id) {
        sites.delete(entite(id));
    }

    /** Sites du tenant a moins de {@code rayonMetres} d'un point (US-003, PostGIS). */
    @Transactional(readOnly = true)
    public List<SiteReponse> proches(double latitude, double longitude, double rayonMetres) {
        return sites.findAllById(sites.idsProches(latitude, longitude, rayonMetres))
                .stream().map(SiteReponse::de).toList();
    }

    Site entite(Long id) {
        return sites.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Site", id));
    }

    private void appliquerOptionnels(Site site, SiteCorps corps) {
        site.setAltitude(corps.altitude());
        site.setDateDemenagement(corps.dateDemenagement());
        site.setDateCloture(corps.dateCloture());
    }

    /** US-006 : demenagement et cloture ne peuvent preceder la mise en oeuvre. */
    private void verifierDates(SiteCorps corps) {
        LocalDate debut = corps.dateMiseEnOeuvre();
        if (corps.dateDemenagement() != null && corps.dateDemenagement().isBefore(debut)) {
            throw new RequeteInvalide("La date de demenagement precede la mise en oeuvre.");
        }
        if (corps.dateCloture() != null && corps.dateCloture().isBefore(debut)) {
            throw new RequeteInvalide("La date de cloture precede la mise en oeuvre.");
        }
    }

    private Ferme fermeRequise(Long fermeId) {
        return fermes.findById(fermeId).orElseThrow(() ->
                new RequeteInvalide("Ferme inconnue dans ce tenant : " + fermeId));
    }
}
