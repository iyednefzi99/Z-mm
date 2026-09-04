package com.zumm.service;

import com.zumm.domain.Consommable;
import com.zumm.repository.ConsommableRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.ConsommableCorps;
import com.zumm.web.dto.ConsommableReponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock de consommables (SPRINT-27, lot E).
 *
 * <p>Ferme « stock de consommables avec seuils de réapprovisionnement » (§6),
 * qui était simplement « absent ».
 *
 * <p><strong>Le mouvement plutôt que la valeur absolue.</strong>
 * {@link #mouvementer} ajoute ou retire une quantité au lieu de poser un total.
 * Deux personnes qui prélèvent du candi le même jour ne s'écrasent pas l'une
 * l'autre — alors qu'une saisie « il reste 12 kg » écrase, sans que personne ne
 * le voie. La saisie du total reste possible par la modification, pour corriger
 * après un inventaire réel.
 */
@Service
@Transactional
public class StockService {

    private final ConsommableRepository consommables;

    public StockService(ConsommableRepository consommables) {
        this.consommables = consommables;
    }

    public ConsommableReponse creer(ConsommableCorps corps) {
        Consommable consommable = new Consommable(corps.libelle().trim(), corps.categorie(),
                corps.unite());
        consommable.setQuantite(corps.quantite());
        consommable.setSeuilAlerte(corps.seuilAlerte());
        consommable.setNote(corps.note());
        return ConsommableReponse.de(consommables.save(consommable));
    }

    @Transactional(readOnly = true)
    public List<ConsommableReponse> lister() {
        return consommables.findAllByOrderByCategorieAscLibelleAsc().stream()
                .map(ConsommableReponse::de)
                .toList();
    }

    public ConsommableReponse mettreAJour(Long id, ConsommableCorps corps) {
        Consommable consommable = entite(id);
        consommable.setLibelle(corps.libelle().trim());
        consommable.setCategorie(corps.categorie());
        consommable.setQuantite(corps.quantite());
        consommable.setUnite(corps.unite());
        consommable.setSeuilAlerte(corps.seuilAlerte());
        consommable.setNote(corps.note());
        return ConsommableReponse.de(consommable);
    }

    /**
     * Ajoute (positif) ou retire (négatif) une quantité.
     *
     * <p>Un stock ne descend pas sous zéro : la base le refuserait, mais le
     * message serait celui de PostgreSQL. Le refus est ici, avec la quantité
     * disponible — c'est elle qui permet de corriger la saisie.
     */
    public ConsommableReponse mouvementer(Long id, BigDecimal delta) {
        Consommable consommable = entite(id);
        BigDecimal nouvelle = consommable.getQuantite().add(delta);
        if (nouvelle.signum() < 0) {
            throw new RequeteInvalide("Stock insuffisant : il reste %s %s."
                    .formatted(consommable.getQuantite(), consommable.getUnite()));
        }
        consommable.setQuantite(nouvelle);
        return ConsommableReponse.de(consommable);
    }

    public void supprimer(Long id) {
        consommables.delete(entite(id));
    }

    private Consommable entite(Long id) {
        return consommables.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Consommable", id));
    }
}
