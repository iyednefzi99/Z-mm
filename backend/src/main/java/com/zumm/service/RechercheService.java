package com.zumm.service;

import com.zumm.repository.AgentRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.FermierRepository;
import com.zumm.repository.LotConditionnementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.ResultatRecherche;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recherche transverse sur les objets metier (SPRINT-21).
 *
 * <p>Repond au ❌ du §1 de {@code docs/ECART-CONCURRENTS.md} : la palette
 * {@code Ctrl/⌘ + K} cherchait parmi les ECRANS, jamais parmi les donnees. Chaque
 * vue gardait ses filtres, et rien ne repondait a « ou est la ruche 42 ».
 *
 * <p><strong>Ce que cette recherche ne rend pas, et pourquoi.</strong> C'est le
 * seul endroit du produit ou un appelant obtient d'un coup un echantillon de
 * TOUTES les tables. Elle est donc volontairement pauvre :
 *
 * <ul>
 *   <li>aucune position, aucune adresse — la recherche par rue rendrait
 *       interrogeable ce que {@code PolitiquePositions} masque a l'affichage ;
 *   <li>aucun courriel — chercher par adresse transformerait la palette en
 *       annuaire exportable ;
 *   <li>un motif d'au moins deux caracteres, et un plafond de resultats : une
 *       recherche sur {@code "a"} n'est pas une recherche, c'est un export.
 * </ul>
 *
 * <p>Le cloisonnement, lui, n'est pas ecrit ici : chaque requete passe par les
 * repositories, donc par la RLS et par la portee d'agent (V16). Un saisonnier
 * cherchant « rucher » ne trouve que les siens, sans qu'aucune ligne de ce
 * service n'ait a s'en occuper.
 */
@Service
@Transactional(readOnly = true)
public class RechercheService {

    /** Un caractere unique ne discrimine rien : c'est un parcours de table deguise. */
    private static final int LONGUEUR_MINIMALE = 2;

    /** Plafond par famille, pour qu'une famille prolifique n'evince pas les autres. */
    private static final int PLAFOND_PAR_FAMILLE = 5;

    private static final int PLAFOND_TOTAL = 25;

    private final SiteRepository sites;
    private final RucheRepository ruches;
    private final FermeRepository fermes;
    private final FermierRepository fermiers;
    private final AgentRepository agents;
    private final RecolteRepository recoltes;
    private final LotConditionnementRepository lots;

    public RechercheService(SiteRepository sites, RucheRepository ruches, FermeRepository fermes,
            FermierRepository fermiers, AgentRepository agents, RecolteRepository recoltes,
            LotConditionnementRepository lots) {
        this.sites = sites;
        this.ruches = ruches;
        this.fermes = fermes;
        this.fermiers = fermiers;
        this.agents = agents;
        this.recoltes = recoltes;
        this.lots = lots;
    }

    /**
     * Cherche {@code motif} dans les sept familles d'objets, dans l'ordre ou on
     * les cherche au rucher : d'abord ce qu'on a sous les yeux (ruches, sites),
     * ensuite l'organisation, enfin la production.
     */
    public List<ResultatRecherche> rechercher(String motif, Integer limite) {
        String terme = motif == null ? "" : motif.trim();
        if (terme.length() < LONGUEUR_MINIMALE) {
            throw new RequeteInvalide(
                    "La recherche demande au moins " + LONGUEUR_MINIMALE + " caracteres.");
        }
        int plafond = limite == null ? PLAFOND_TOTAL : Math.min(Math.max(limite, 1), PLAFOND_TOTAL);
        Pageable page = PageRequest.of(0, PLAFOND_PAR_FAMILLE);

        List<ResultatRecherche> resultats = new ArrayList<>();
        ruches.rechercher(terme, page).forEach(ruche -> resultats.add(new ResultatRecherche(
                "ruche", ruche.getId(),
                "Ruche " + ruche.getId() + " — " + ruche.getModele(),
                ruche.getSite() == null ? null : ruche.getSite().getNom(),
                "/ruches")));
        sites.rechercher(terme, page).forEach(site -> resultats.add(new ResultatRecherche(
                "site", site.getId(), site.getNom(), site.getVille(), "/sites")));
        fermes.findByNomContainingIgnoreCaseOrderByNomAsc(terme, page)
                .forEach(ferme -> resultats.add(new ResultatRecherche(
                        "ferme", ferme.getId(), ferme.getNom(), null, "/fermes")));
        fermiers.findByNomContainingIgnoreCaseOrderByNomAsc(terme, page)
                .forEach(fermier -> resultats.add(new ResultatRecherche(
                        "fermier", fermier.getId(), fermier.getNom(), null, "/fermiers")));
        agents.findByNomContainingIgnoreCaseOrderByNomAsc(terme, page)
                .forEach(agent -> resultats.add(new ResultatRecherche(
                        "agent", agent.getId(), agent.getNom(), null, "/agents")));
        recoltes.rechercher(terme, page).forEach(recolte -> resultats.add(new ResultatRecherche(
                "recolte", recolte.getId(),
                recolte.getLot() == null ? "Recolte " + recolte.getId() : recolte.getLot(),
                recolte.getTypeMiel(), "/recoltes")));
        lots.findByReferenceContainingIgnoreCaseOrderByReferenceAsc(terme, page)
                .forEach(lot -> resultats.add(new ResultatRecherche(
                        "lot", lot.getId(), lot.getReference(), lot.getTypeMiel(), "/lots")));

        return resultats.size() <= plafond ? resultats : resultats.subList(0, plafond);
    }
}
