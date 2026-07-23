package com.zumm.service;

import com.zumm.domain.Ferme;
import com.zumm.domain.Fermier;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.FermierRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.FermeCorps;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations metier sur les fermes (US-002), rattachees a un fermier.
 *
 * <p>Le fermier reference doit exister <em>dans le tenant courant</em> : on le
 * verifie explicitement pour renvoyer un 400 clair, plutot que de laisser
 * remonter une violation de cle etrangere. La cle composite en base
 * {@code (fermier_id, tenant_id)} reste le garde-fou ultime contre un rattachement
 * inter-tenant.
 */
@Service
@Transactional
public class FermeService {

    private final FermeRepository fermes;
    private final FermierRepository fermiers;

    public FermeService(FermeRepository fermes, FermierRepository fermiers) {
        this.fermes = fermes;
        this.fermiers = fermiers;
    }

    public Ferme creer(FermeCorps corps) {
        return fermes.save(new Ferme(corps.nom(), fermierRequis(corps.fermierId())));
    }

    @Transactional(readOnly = true)
    public List<Ferme> lister() {
        return fermes.findAll();
    }

    @Transactional(readOnly = true)
    public Ferme obtenir(Long id) {
        return fermes.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Ferme", id));
    }

    public Ferme mettreAJour(Long id, FermeCorps corps) {
        Ferme ferme = obtenir(id);
        ferme.setNom(corps.nom());
        ferme.setFermier(fermierRequis(corps.fermierId()));
        return ferme;
    }

    public void supprimer(Long id) {
        fermes.delete(obtenir(id));
    }

    /** Resout un fermier du tenant courant, ou refuse la requete (400). */
    private Fermier fermierRequis(Long fermierId) {
        return fermiers.findById(fermierId).orElseThrow(() ->
                new RequeteInvalide("Fermier inconnu dans ce tenant : " + fermierId));
    }
}
