package com.zumm.service;

import com.zumm.domain.Materiel;
import com.zumm.domain.Site;
import com.zumm.repository.MaterielRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MaterielCorps;
import com.zumm.web.dto.MaterielReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventaire et entretien du matériel (SPRINT-27, lot E).
 *
 * <p>Ferme « inventaire du matériel et état d'entretien » (§6) et prépare
 * « plan de maintenance » (§13) : c'est {@code RegleMaintenanceMateriel} qui
 * transforme une périodicité dépassée en tâche, au passage du moteur.
 *
 * <p><strong>Ce service ne planifie rien lui-même.</strong> Il tient un
 * inventaire ; l'échéance se déduit, et la tâche naît d'une règle — la même
 * mécanique que le SPRINT-22, avec sa clé d'idempotence et son anti-doublon.
 * Écrire ici un second planificateur aurait doublé ce qui existe.
 */
@Service
@Transactional
public class MaterielService {

    private final MaterielRepository materiels;
    private final SiteRepository sites;

    public MaterielService(MaterielRepository materiels, SiteRepository sites) {
        this.materiels = materiels;
        this.sites = sites;
    }

    public MaterielReponse creer(MaterielCorps corps) {
        Materiel materiel = new Materiel(corps.libelle().trim(), corps.categorie(),
                corps.quantite() == 0 ? 1 : corps.quantite());
        appliquer(materiel, corps);
        return MaterielReponse.de(materiels.save(materiel), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<MaterielReponse> lister() {
        LocalDate jour = LocalDate.now();
        return materiels.findAllByOrderByCategorieAscLibelleAsc().stream()
                .map(m -> MaterielReponse.de(m, jour))
                .toList();
    }

    @Transactional(readOnly = true)
    public MaterielReponse obtenir(Long id) {
        return MaterielReponse.de(entite(id), LocalDate.now());
    }

    public MaterielReponse mettreAJour(Long id, MaterielCorps corps) {
        Materiel materiel = entite(id);
        materiel.setLibelle(corps.libelle().trim());
        materiel.setCategorie(corps.categorie());
        if (corps.quantite() > 0) {
            materiel.setQuantite(corps.quantite());
        }
        appliquer(materiel, corps);
        return MaterielReponse.de(materiel, LocalDate.now());
    }

    /**
     * Marque l'entretien fait, au jour donné.
     *
     * <p>Un geste dédié plutôt qu'une modification du champ : c'est l'action
     * réelle — « je viens de réviser l'extracteur » — et elle repousse
     * l'échéance sans rien d'autre à ressaisir.
     */
    public MaterielReponse entretenir(Long id, LocalDate jour) {
        Materiel materiel = entite(id);
        LocalDate fait = jour == null ? LocalDate.now() : jour;
        if (fait.isAfter(LocalDate.now())) {
            throw new RequeteInvalide("Un entretien ne se date pas dans l'avenir.");
        }
        materiel.setDerniereMaintenance(fait);
        // Un entretien fait remet l'état à « bon » — mais jamais à « neuf » : le
        // matériel révisé n'est pas du matériel neuf, et le prétendre ferait
        // perdre la trace de son âge.
        if ("a_reviser".equals(materiel.getEtat())) {
            materiel.setEtat("bon");
        }
        return MaterielReponse.de(materiel, LocalDate.now());
    }

    public void supprimer(Long id) {
        materiels.delete(entite(id));
    }

    private void appliquer(Materiel materiel, MaterielCorps corps) {
        materiel.setSite(siteEventuel(corps.siteId()));
        if (corps.etat() != null) {
            materiel.setEtat(corps.etat());
        }
        materiel.setPeriodiciteJours(corps.periodiciteJours());
        materiel.setDerniereMaintenance(corps.derniereMaintenance());
        materiel.setNote(corps.note());
    }

    private Site siteEventuel(Long siteId) {
        if (siteId == null) {
            return null;
        }
        return sites.findById(siteId)
                .orElseThrow(() -> new RequeteInvalide("Site inconnu dans ce tenant : " + siteId));
    }

    private Materiel entite(Long id) {
        return materiels.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Materiel", id));
    }
}
