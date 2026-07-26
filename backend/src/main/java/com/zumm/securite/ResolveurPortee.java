package com.zumm.securite;

import com.zumm.domain.Agent;
import com.zumm.repository.AgentRepository;
import com.zumm.tenant.PorteeContext.Portee;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Determine ce que l'appelant a le droit de VOIR a l'interieur de son
 * exploitation (US-057).
 *
 * <p>La regle, et la raison de chaque ligne :
 *
 * <ul>
 *   <li><strong>responsable, admin</strong> — toute l'exploitation. C'est leur
 *       fonction : piloter suppose de tout voir ;
 *   <li><strong>capteur</strong> — portee globale egalement, mais le RBAC le
 *       borne deja au seul depot de mesures. Une passerelle n'a pas d'agent, et
 *       lui refuser la portee la rendrait incapable de rattacher une mesure a sa
 *       ruche ;
 *   <li><strong>apiculteur, superviseur</strong> — la portee de leur agent. Un
 *       saisonnier, un stagiaire ou un compte compromis ne peut plus enumerer
 *       l'integralite du parc, c'est-a-dire la carte des ruchers.
 * </ul>
 *
 * <p><strong>Une identite sans agent correspondant ne voit RIEN</strong>, et c'est
 * volontaire. Le repli inverse — « pas d'agent, donc tout voir » — transformerait
 * un defaut de parametrage en fuite complete. Il vaut mieux un ecran vide et un
 * appel a l'administrateur.
 */
@Component
public class ResolveurPortee {

    private static final Logger LOG = LoggerFactory.getLogger(ResolveurPortee.class);

    /** Profils dont la fonction meme suppose la vue d'ensemble. */
    private static final java.util.Set<String> ROLES_GLOBAUX =
            java.util.Set.of("ROLE_responsable", "ROLE_admin", "ROLE_capteur");

    private final AgentRepository agents;

    public ResolveurPortee(AgentRepository agents) {
        this.agents = agents;
    }

    /**
     * Portee de l'appelant.
     *
     * <p>Transactionnelle car la premiere resolution peut ECRIRE : la liaison du
     * compte a l'agent (voir {@link #agentDe}).
     */
    @Transactional
    public Portee resoudre(Authentication authentification) {
        if (authentification == null || !authentification.isAuthenticated()) {
            return Portee.aucune();
        }
        if (aUnRoleGlobal(authentification)) {
            return Portee.touteExploitation();
        }
        return agentDe(authentification)
                .map(agent -> Portee.agent(agent.getId()))
                .orElseGet(() -> {
                    // Trace explicite : c'est un defaut de parametrage, pas un
                    // comportement normal, et l'utilisateur va voir un ecran vide
                    // sans comprendre pourquoi.
                    LOG.warn("Aucun agent ne correspond au compte authentifie : portee vide.");
                    return Portee.aucune();
                });
    }

    private boolean aUnRoleGlobal(Authentication authentification) {
        return authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_GLOBAUX::contains);
    }

    /**
     * Agent correspondant au compte, avec liaison a la premiere connexion.
     *
     * <p>Deux cles, dans cet ordre :
     * <ol>
     *   <li>le <strong>sujet</strong> ({@code sub}), stable chez le fournisseur ;
     *   <li>a defaut, le <strong>courriel</strong> — et le sujet est alors inscrit,
     *       une fois pour toutes.
     * </ol>
     *
     * <p>Cette liaison differee evite d'imposer une reprise de donnees : les
     * agents deja saisis n'ont qu'un courriel, et se relient d'eux-memes a la
     * premiere connexion. Une fois le sujet inscrit, un changement d'adresse ne
     * casse plus l'affectation.
     */
    private Optional<Agent> agentDe(Authentication authentification) {
        IdentiteAppelant identite = IdentiteAppelant.de(authentification);
        if (identite.sujet() != null) {
            Optional<Agent> parSujet = agents.findBySujetOidc(identite.sujet());
            if (parSujet.isPresent()) {
                return parSujet;
            }
        }
        if (identite.email() == null) {
            return Optional.empty();
        }
        Optional<Agent> parEmail = agents.findByEmailIgnoreCase(identite.email());
        parEmail.ifPresent(agent -> {
            if (agent.getSujetOidc() == null && identite.sujet() != null) {
                agent.setSujetOidc(identite.sujet());
                LOG.info("Compte lie a l'agent {} par son courriel ; sujet enregistre.",
                        agent.getId());
            }
        });
        return parEmail;
    }
}
