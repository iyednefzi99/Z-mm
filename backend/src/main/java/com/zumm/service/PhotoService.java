package com.zumm.service;

import com.zumm.domain.Photo;
import com.zumm.repository.PhotoRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.SuiviReineRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.PhotoCibleCorps;
import com.zumm.web.dto.PhotoReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Photos attachees aux objets du parc (SPRINT-21).
 *
 * <p>Repond au 🟡 du §1 de {@code docs/ECART-CONCURRENTS.md} : {@code Photo}
 * n'existait que rattachee a une visite ({@code optional = false}), si bien
 * qu'une photo de ruche, de reine marquee, de cadre de recolte ou de rucher
 * n'avait aucun endroit ou aller.
 *
 * <p>Le SPRINT-28 y ajoute une sixieme cible, le {@code TRAITEMENT} : un
 * registre d'elevage devient verifiable quand le scan de l'ordonnance y est
 * attache, et non seulement sa reference recopiee a la main.
 *
 * <p>Les routes de visite ({@code /api/visites/{id}/photos}) restent servies par
 * {@code VisiteService} : elles fonctionnent, elles sont utilisees, et les
 * deplacer ici aurait casse un contrat pour un gain nul.
 */
@Service
@Transactional
public class PhotoService {

    private final PhotoRepository photos;
    private final VisiteRepository visites;
    private final RucheRepository ruches;
    private final SiteRepository sites;
    private final SuiviReineRepository reines;
    private final RecolteRepository recoltes;
    private final TraitementRepository traitements;

    public PhotoService(PhotoRepository photos, VisiteRepository visites, RucheRepository ruches,
            SiteRepository sites, SuiviReineRepository reines, RecolteRepository recoltes,
            TraitementRepository traitements) {
        this.photos = photos;
        this.visites = visites;
        this.ruches = ruches;
        this.sites = sites;
        this.reines = reines;
        this.recoltes = recoltes;
        this.traitements = traitements;
    }

    public PhotoReponse attacher(PhotoCibleCorps corps) {
        Object porteur = porteur(corps.cible(), corps.cibleId());
        return PhotoReponse.de(photos.save(
                Photo.sur(corps.cible(), porteur, corps.url(), corps.legende())));
    }

    @Transactional(readOnly = true)
    public List<PhotoReponse> lister(Photo.Cible cible, Long cibleId) {
        porteur(cible, cibleId);
        List<Photo> trouvees = switch (cible) {
            case VISITE -> photos.findByVisiteIdOrderByIdAsc(cibleId);
            case RUCHE -> photos.findByRucheIdOrderByIdAsc(cibleId);
            case SITE -> photos.findBySiteIdOrderByIdAsc(cibleId);
            case REINE -> photos.findByReineIdOrderByIdAsc(cibleId);
            case RECOLTE -> photos.findByRecolteIdOrderByIdAsc(cibleId);
            case TRAITEMENT -> photos.findByTraitementIdOrderByIdAsc(cibleId);
        };
        return trouvees.stream().map(PhotoReponse::de).toList();
    }

    public void supprimer(Long id) {
        Photo photo = photos.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Photo", id));
        photos.delete(photo);
    }

    /**
     * Resout l'objet porteur, et refuse une cible inexistante.
     *
     * <p>La cle etrangere composite la refuserait aussi, mais en 500 : on prefere
     * un 400 qui nomme l'objet manquant. La resolution passe par les repositories,
     * donc par la RLS — attacher une photo a la ruche d'un autre tenant echoue au
     * meme endroit qu'une lecture.
     */
    private Object porteur(Photo.Cible cible, Long cibleId) {
        return switch (cible) {
            case VISITE -> visites.findById(cibleId).orElseThrow(() -> inconnu("Visite", cibleId));
            case RUCHE -> ruches.findById(cibleId).orElseThrow(() -> inconnu("Ruche", cibleId));
            case SITE -> sites.findById(cibleId).orElseThrow(() -> inconnu("Site", cibleId));
            case REINE -> reines.findById(cibleId).orElseThrow(() -> inconnu("Reine", cibleId));
            case RECOLTE -> recoltes.findById(cibleId)
                    .orElseThrow(() -> inconnu("Recolte", cibleId));
            case TRAITEMENT -> traitements.findById(cibleId)
                    .orElseThrow(() -> inconnu("Traitement", cibleId));
        };
    }

    private static RequeteInvalide inconnu(String quoi, Long id) {
        return new RequeteInvalide(quoi + " inconnu(e) dans ce tenant : " + id);
    }
}
