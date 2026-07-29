package com.zumm.repository;

import com.zumm.domain.CodeInvitation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Gestion des codes d'invitation par une exploitation (US-058).
 *
 * <p>Restreint au tenant courant comme toute entite {@code EntiteTenant} :
 * filtre Hibernate double par la politique RLS. Un responsable ne voit donc que
 * les codes de son exploitation, sans qu'aucun filtre ne soit ecrit ici.
 *
 * <p>A ne pas confondre avec {@link InvitationRepository}, qui resout un code a
 * l'INSCRIPTION — donc sans tenant courant, donc hors de ce chemin.
 */
public interface CodeInvitationRepository extends JpaRepository<CodeInvitation, Long> {

    /** Les plus recents d'abord : c'est le code qu'on vient d'emettre qu'on cherche. */
    List<CodeInvitation> findAllByOrderByCreeLeDesc();

    boolean existsByCode(String code);
}
