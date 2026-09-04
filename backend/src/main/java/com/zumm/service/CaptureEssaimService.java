package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.CaptureEssaim;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.CaptureEssaimRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.CaptureEssaimCorps;
import com.zumm.web.dto.CaptureEssaimReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registre des captures d'essaim (SPRINT-21).
 *
 * <p>Repond au ❌ du §1 de {@code docs/ECART-CONCURRENTS.md} : le depot ne
 * nommait nulle part l'autre porte d'entree d'une colonie — celle qui ne coute
 * rien, et dont le suivi decide si un piege vaut d'etre repose l'an prochain.
 */
@Service
@Transactional
public class CaptureEssaimService {

    private final CaptureEssaimRepository captures;
    private final AgentRepository agents;
    private final RucheRepository ruches;
    private final SiteRepository sites;

    public CaptureEssaimService(CaptureEssaimRepository captures, AgentRepository agents,
            RucheRepository ruches, SiteRepository sites) {
        this.captures = captures;
        this.agents = agents;
        this.ruches = ruches;
        this.sites = sites;
    }

    public CaptureEssaimReponse enregistrer(CaptureEssaimCorps corps) {
        Agent agent = agents.findById(corps.agentId()).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + corps.agentId()));

        CaptureEssaim capture = new CaptureEssaim(agent, corps.dateCapture(), corps.origine());
        capture.setLieu(corps.lieu());
        capture.setPoidsKg(corps.poidsKg());
        capture.setHauteurM(corps.hauteurM());
        capture.setNote(corps.note());
        if (corps.siteId() != null) {
            capture.setSite(siteRequis(corps.siteId()));
        }
        if (corps.rucheId() != null) {
            capture.setRuche(logee(corps.rucheId()));
        }
        return CaptureEssaimReponse.de(captures.save(capture));
    }

    /** Toutes les captures, la plus recente d'abord. */
    @Transactional(readOnly = true)
    public List<CaptureEssaimReponse> lister() {
        return captures.findAllByOrderByDateCaptureDescIdDesc().stream()
                .map(CaptureEssaimReponse::de).toList();
    }

    /** Captures d'une saison — la seule maille a laquelle une annee se juge. */
    @Transactional(readOnly = true)
    public List<CaptureEssaimReponse> saison(LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw new RequeteInvalide("La fin de periode (" + fin + ") precede son debut ("
                    + debut + ").");
        }
        return captures.findByDateCaptureBetweenOrderByDateCaptureDescIdDesc(debut, fin).stream()
                .map(CaptureEssaimReponse::de).toList();
    }

    /** Captures encore en ruchette d'attente : ce qui reste a loger. */
    @Transactional(readOnly = true)
    public List<CaptureEssaimReponse> enAttente() {
        return captures.findByRucheIsNullOrderByDateCaptureDescIdDesc().stream()
                .map(CaptureEssaimReponse::de).toList();
    }

    /**
     * Loge une capture dans une ruche.
     *
     * <p>Operation a part entiere parce qu'elle arrive PLUS TARD : on capture un
     * jour, on loge quand la colonie a pris. La forcer a la saisie initiale
     * reviendrait a n'enregistrer que les captures reussies, c'est-a-dire a
     * perdre la statistique qui a de la valeur.
     */
    public CaptureEssaimReponse loger(Long id, Long rucheId) {
        CaptureEssaim capture = captures.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Capture", id));
        capture.setRuche(logee(rucheId));
        if (capture.getRuche().getSite() != null) {
            capture.setSite(capture.getRuche().getSite());
        }
        return CaptureEssaimReponse.de(capture);
    }

    public void supprimer(Long id) {
        CaptureEssaim capture = captures.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Capture", id));
        captures.delete(capture);
    }

    /**
     * Resout la ruche d'accueil et lui pose son origine.
     *
     * <p>Comme pour une division, l'origine de la ruche est alignee sur
     * l'evenement qui l'explique — {@code 'essaim_capture'} — quand elle n'est pas
     * deja renseignee : deux endroits qui disent la meme chose ne doivent pas
     * pouvoir la dire differemment.
     */
    private Ruche logee(Long rucheId) {
        Ruche ruche = ruches.findById(rucheId).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + rucheId));
        if (ruche.getOrigine() == null) {
            ruche.setOrigine("essaim_capture");
        }
        return ruche;
    }

    private Site siteRequis(Long siteId) {
        return sites.findById(siteId).orElseThrow(() ->
                new RequeteInvalide("Site inconnu dans ce tenant : " + siteId));
    }
}
