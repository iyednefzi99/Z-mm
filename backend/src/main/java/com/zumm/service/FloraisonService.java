package com.zumm.service;

import com.zumm.domain.FloraisonObservee;
import com.zumm.domain.RessourceFlorale;
import com.zumm.repository.FloraisonObserveeRepository;
import com.zumm.repository.RessourceFloraleRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.FloraisonCorps;
import com.zumm.web.dto.FloraisonReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendrier de floraison OBSERVEE (SPRINT-32, lot H).
 *
 * <p>Ferme la ligne « calendrier de floraison / suivi des miellees » du §2 :
 * « HiveBook et HiveTracks en font un module ; Zumm n'a aucune notion de saison
 * mellifere ».
 *
 * <p><strong>L'observe ne remplace pas le declaratif</strong> de la `V21`, il le
 * confronte. Le declaratif prevoit — « le colza fleurit en avril » —, l'observe
 * constate. La reponse rend les deux cote a cote et leur ECART en jours : c'est
 * la seule chose qui permette de dire « cette annee, c'etait en avance », et
 * c'est exactement ce qu'un module de floraison sert a savoir.
 *
 * <p><strong>Une ressource ne fleurit qu'une fois par an.</strong> La base le
 * garantit ; ce service MODIFIE l'observation existante au lieu d'echouer, parce
 * que l'apiculteur note le debut en avril et complete le pic en mai — refuser la
 * seconde saisie lui ferait perdre la premiere.
 */
@Service
@Transactional
public class FloraisonService {

    private final FloraisonObserveeRepository floraisons;
    private final RessourceFloraleRepository ressources;

    public FloraisonService(FloraisonObserveeRepository floraisons,
            RessourceFloraleRepository ressources) {
        this.floraisons = floraisons;
        this.ressources = ressources;
    }

    /** Enregistre ou complete l'observation de l'annee. */
    public FloraisonReponse enregistrer(FloraisonCorps corps) {
        RessourceFlorale ressource = ressources.findById(corps.ressourceId())
                .orElseThrow(() -> new RequeteInvalide(
                        "Ressource florale inconnue dans ce tenant : " + corps.ressourceId()));
        verifierOrdre(corps);

        FloraisonObservee floraison = floraisons
                .findByRessource_IdAndAnnee(corps.ressourceId(), corps.annee())
                .orElseGet(() -> new FloraisonObservee(ressource, corps.annee(),
                        corps.dateDebut()));
        floraison.setDateDebut(corps.dateDebut());
        floraison.setDatePic(corps.datePic());
        floraison.setDateFin(corps.dateFin());
        floraison.setAbondance(corps.abondance() == null ? null
                : corps.abondance().shortValue());
        floraison.setNote(corps.note());
        return FloraisonReponse.de(floraisons.save(floraison));
    }

    @Transactional(readOnly = true)
    public List<FloraisonReponse> lister(Long siteId) {
        List<FloraisonObservee> trouvees = siteId == null
                ? floraisons.findAllByOrderByAnneeDescDateDebutAsc()
                : floraisons.findByRessource_Site_IdOrderByAnneeDescDateDebutAsc(siteId);
        return trouvees.stream().map(FloraisonReponse::de).toList();
    }

    public void supprimer(Long id) {
        floraisons.delete(floraisons.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Floraison", id)));
    }

    /**
     * Une floraison ne finit pas avant d'avoir commence.
     *
     * <p>La base le refuse aussi ({@code ck_floraison_ordre}), mais en 409 : on
     * prefere un 400 qui nomme la date fautive. Deux dates inversees sont une
     * faute de frappe, pas un conflit d'etat.
     */
    private static void verifierOrdre(FloraisonCorps corps) {
        if (corps.datePic() != null && corps.datePic().isBefore(corps.dateDebut())) {
            throw new RequeteInvalide("Le pic de floraison precede son debut.");
        }
        if (corps.dateFin() != null && corps.dateFin().isBefore(corps.dateDebut())) {
            throw new RequeteInvalide("La fin de floraison precede son debut.");
        }
        if (corps.dateFin() != null && corps.datePic() != null
                && corps.dateFin().isBefore(corps.datePic())) {
            throw new RequeteInvalide("La fin de floraison precede son pic.");
        }
    }
}
