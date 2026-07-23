package com.zumm.service;

import com.zumm.domain.Fermier;
import com.zumm.repository.FermierRepository;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.FermierCorps;
import com.zumm.web.dto.FermierReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations metier sur les fermiers (US-001).
 *
 * <p>Chaque methode est transactionnelle : la transaction est ouverte dans le
 * contexte tenant de la requete, ce qui positionne la variable de session RLS et
 * le discriminant Hibernate. L'isolation inter-tenant est donc automatique — aucun
 * filtre {@code tenant_id} n'apparait ici.
 *
 * <p>Le service renvoie des DTO, jamais des entites : la conversion a lieu dans la
 * transaction, ce qui evite toute initialisation paresseuse hors session.
 */
@Service
@Transactional
public class FermierService {

    private final FermierRepository fermiers;

    public FermierService(FermierRepository fermiers) {
        this.fermiers = fermiers;
    }

    public FermierReponse creer(FermierCorps corps) {
        return FermierReponse.de(fermiers.save(new Fermier(corps.nom(), corps.contact())));
    }

    @Transactional(readOnly = true)
    public List<FermierReponse> lister() {
        return fermiers.findAll().stream().map(FermierReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public FermierReponse obtenir(Long id) {
        return FermierReponse.de(entite(id));
    }

    public FermierReponse mettreAJour(Long id, FermierCorps corps) {
        Fermier fermier = entite(id);
        fermier.setNom(corps.nom());
        fermier.setContact(corps.contact());
        return FermierReponse.de(fermier); // Flush a la validation de la transaction.
    }

    public void supprimer(Long id) {
        fermiers.delete(entite(id));
    }

    /** Charge l'entite du tenant courant ou leve un 404. Reserve a l'interne. */
    Fermier entite(Long id) {
        return fermiers.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Fermier", id));
    }
}
