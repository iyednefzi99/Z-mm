package com.zumm.service;

import com.zumm.domain.CodeInvitation;
import com.zumm.repository.CodeInvitationRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emission et revocation des codes d'invitation (US-058, ADR-009).
 *
 * <p>C'est le pendant « responsable » de l'inscription : quelqu'un doit pouvoir
 * decider qui rejoint l'exploitation, et avec quel role. Sans cet ecran, le
 * formulaire d'inscription serait une porte sans clef — personne ne pourrait en
 * fabriquer une.
 */
@Service
public class InvitationService {

    /** Duree de vie par defaut d'une invitation. */
    private static final Duration VALIDITE = Duration.ofDays(14);

    /** Roles qu'une invitation peut attribuer. {@code admin} en est absent. */
    private static final List<String> ROLES_INVITABLES =
            List.of("apiculteur", "superviseur", "responsable");

    private final CodeInvitationRepository depot;

    public InvitationService(CodeInvitationRepository depot) {
        this.depot = depot;
    }

    /** Vue d'un code pour l'interface. */
    public record InvitationReponse(Long id, String code, String role, int utilisations,
            int utilisationsMax, Instant expireLe, String creePar, boolean epuise) {
    }

    /** Demande d'emission. Tout est optionnel sauf le role. */
    public record InvitationCorps(String role, Integer utilisationsMax, Integer joursValidite) {
    }

    @Transactional(readOnly = true)
    public List<InvitationReponse> lister() {
        return depot.findAllByOrderByCreeLeDesc().stream().map(InvitationService::vue).toList();
    }

    /**
     * Emet un code.
     *
     * <p>Le code est tire ici et non fourni par l'appelant : laisser choisir sa
     * valeur permettrait d'en poser un devinable — le nom de l'exploitation, une
     * annee — et de le faire deviner a qui n'a jamais ete invite.
     *
     * <p>Le tirage est reessaye en cas de collision. Elle est improbable
     * (30^8 possibilites) mais la contrainte d'unicite est GLOBALE : deux
     * exploitations peuvent se telescoper, et l'erreur tomberait alors sur un
     * responsable qui n'y peut rien.
     */
    @Transactional
    public InvitationReponse emettre(InvitationCorps corps, String auteur) {
        String role = corps.role() == null ? "apiculteur" : corps.role().trim();
        if (!ROLES_INVITABLES.contains(role)) {
            // 400 et non 500 : la demande est recevable, c'est sa valeur qui ne
            // l'est pas. `admin` tombe ici — une invitation ne fabrique pas
            // d'administrateur, quelle que soit la personne qui l'emet.
            throw new RequeteInvalide("Role non invitable : " + role);
        }
        int places = corps.utilisationsMax() == null ? 1 : Math.max(1, corps.utilisationsMax());
        Duration validite = corps.joursValidite() == null
                ? VALIDITE
                : Duration.ofDays(Math.min(90, Math.max(1, corps.joursValidite())));

        String code = CodeInvitation.tirerCode();
        for (int essai = 0; essai < 5 && depot.existsByCode(code); essai++) {
            code = CodeInvitation.tirerCode();
        }
        return vue(depot.save(new CodeInvitation(
                code, role, places, Instant.now().plus(validite), auteur)));
    }

    /**
     * Revoque un code.
     *
     * <p>Suppression et non desactivation : un code revoque n'a pas d'histoire a
     * conserver, et les comptes deja crees avec lui existent independamment de la
     * ligne. Ce qui doit rester tracable — qui a rejoint quoi et quand — releve du
     * journal d'audit, pas de cette table.
     */
    @Transactional
    public void revoquer(Long id) {
        CodeInvitation code = depot.findById(id)
                .orElseThrow(() -> new RessourceIntrouvable("Invitation " + id + " introuvable."));
        depot.delete(code);
    }

    private static InvitationReponse vue(CodeInvitation code) {
        return new InvitationReponse(code.getId(), code.getCode(), code.getRole(),
                code.getUtilisations(), code.getUtilisationsMax(), code.getExpireLe(),
                code.getCreePar(), code.estEpuise());
    }
}
