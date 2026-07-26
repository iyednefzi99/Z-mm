package com.zumm.web;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Magasin des reponses memorisees par cle d'idempotence (US-055).
 *
 * <p>En base, et non en memoire : c'est precisement l'indisponibilite du serveur
 * qui provoque les rejeux, donc un cache que le redemarrage vide ne protegerait de
 * rien. La table porte la RLS (V14) — la cle vient du client, donc d'une source
 * non fiable, et rien ne doit permettre a une exploitation de lire la reponse
 * memorisee d'une autre en devinant une cle.
 *
 * <p>JDBC direct plutot que JPA : le filtre s'execute HORS de toute transaction
 * applicative et n'a besoin ni d'entite, ni de cache de premier niveau, ni de
 * suivi d'etat. Deux requetes suffisent.
 */
@Component
public class MagasinIdempotence {

    /** Reponse deja produite pour une cle donnee. */
    public record ReponseMemorisee(String empreinte, int statut, String corps) {
    }

    private final JdbcTemplate jdbc;

    public MagasinIdempotence(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** Reponse memorisee pour cette cle, dans le tenant courant. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<ReponseMemorisee> retrouver(String cle) {
        return jdbc.query(
                "SELECT empreinte, statut, corps FROM requete_idempotente WHERE cle = ?",
                (rs, ligne) -> new ReponseMemorisee(
                        rs.getString("empreinte"), rs.getInt("statut"), rs.getString("corps")),
                cle).stream().findFirst();
    }

    /**
     * Memorise la reponse.
     *
     * <p>{@code ON CONFLICT DO NOTHING} : deux requetes concurrentes portant la
     * meme cle peuvent atteindre ce point ensemble (l'utilisateur a double-clique,
     * ou deux onglets rejouent la file). La premiere ecrit, la seconde ne fait
     * rien — plutot qu'echouer sur la cle primaire et transformer un doublon
     * benin en erreur 500.
     *
     * <p>{@code REQUIRES_NEW} : l'ecriture doit survivre a un rollback ulterieur
     * du traitement, sans quoi la cle disparaitrait avec lui.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void memoriser(String cle, String empreinte, int statut, String corps) {
        jdbc.update("""
                INSERT INTO requete_idempotente (tenant_id, cle, empreinte, statut, corps)
                VALUES (current_setting('app.current_tenant', true), ?, ?, ?, ?)
                ON CONFLICT (tenant_id, cle) DO NOTHING
                """, cle, empreinte, statut, corps);
    }

    /**
     * Purge les entrees anciennes. Au-dela d'une semaine, un rejeu n'est plus
     * plausible : la file du client aurait ete videe ou l'appareil reinstalle.
     * Laisser grossir la table indefiniment couterait plus que le service rendu.
     */
    @Transactional
    public int purger(int jours) {
        return jdbc.update(
                "DELETE FROM requete_idempotente WHERE cree_le < now() - make_interval(days => ?)",
                jours);
    }
}
