package com.zumm.service;

import com.zumm.domain.Fermier;
import com.zumm.repository.FermierRepository;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.FermierCorps;
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
 */
@Service
@Transactional
public class FermierService {

    private final FermierRepository fermiers;

    public FermierService(FermierRepository fermiers) {
        this.fermiers = fermiers;
    }

    public Fermier creer(FermierCorps corps) {
        return fermiers.save(new Fermier(corps.nom(), corps.contact()));
    }

    @Transactional(readOnly = true)
    public List<Fermier> lister() {
        return fermiers.findAll();
    }

    @Transactional(readOnly = true)
    public Fermier obtenir(Long id) {
        return fermiers.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Fermier", id));
    }

    public Fermier mettreAJour(Long id, FermierCorps corps) {
        Fermier fermier = obtenir(id);
        fermier.setNom(corps.nom());
        fermier.setContact(corps.contact());
        return fermier; // Flush a la validation de la transaction (dirty checking).
    }

    public void supprimer(Long id) {
        // On charge d'abord pour distinguer un 404 d'une suppression silencieuse.
        fermiers.delete(obtenir(id));
    }
}
