package com.zumm.repository;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acces aux codes d'invitation, hors contexte de tenant (ADR-009).
 *
 * <p>Ce depot est volontairement en JDBC nu et non en JPA : les deux operations
 * qu'il expose sont des appels de FONCTION {@code SECURITY DEFINER} (V18), pas
 * des lectures d'entite. Passer par JPA supposerait une entite mappee sur une
 * table que l'appelant, non authentifie a ce stade, ne peut de toute facon pas
 * lire — la RLS la rend vide sans {@code app.current_tenant}.
 *
 * <p>C'est le seul chemin de lecture de cette table hors tenant, et il est
 * etroit : on n'y entre qu'avec un code, et il n'en sort qu'un rattachement.
 */
@Repository
public class InvitationRepository {

    /** Rattachement porte par une invitation reservee. */
    public record Invitation(String tenantId, String role) {
    }

    private final JdbcTemplate jdbc;

    public InvitationRepository(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /**
     * Reserve une place sur le code et rend le rattachement associe.
     *
     * <p>Verification et reservation sont faites par le MEME ordre SQL : entre
     * un controle et une consommation separes, deux inscriptions simultanees
     * passeraient toutes les deux sur la derniere place.
     *
     * @return le rattachement, ou vide si le code est inconnu, expire ou epuise
     */
    public Optional<Invitation> reserver(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                "SELECT tenant_id, role FROM zumm_reserver_invitation(?)",
                (resultat, ligne) -> new Invitation(
                        resultat.getString("tenant_id"), resultat.getString("role")),
                code)
                .stream()
                .findFirst();
    }

    /**
     * Rend une place reservee.
     *
     * <p>Appelee quand la creation du compte a echoue APRES la reservation :
     * sans cela, chaque tentative ratee — mot de passe refuse, adresse deja
     * prise — consommerait definitivement une place du code.
     */
    public void relacher(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        // `query` et non `update` : la fonction est appelee par un SELECT, qui
        // rend une ligne (a colonne vide) meme lorsqu'elle ne rend rien. Un
        // `update` refuse ce resultat — « A result was returned when none was
        // expected ». L'extracteur consomme la ligne et n'en tire rien.
        jdbc.query("SELECT zumm_relacher_invitation(?)", resultat -> null, code);
    }
}
