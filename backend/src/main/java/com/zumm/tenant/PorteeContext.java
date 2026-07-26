package com.zumm.tenant;

import java.util.function.Supplier;

/**
 * Porte la PORTEE d'autorisation de l'appelant courant (US-057).
 *
 * <p>Compagnon de {@link TenantContext} : celui-ci dit « quelle exploitation »,
 * celui-la dit « quelle part de cette exploitation ». Les deux sont poses par la
 * chaine de filtres et relus par {@link TenantConnectionProvider}, qui les
 * transmet a PostgreSQL sous forme de variables de session — c'est la base, et
 * non le code applicatif, qui applique la restriction.
 *
 * <p><strong>Le defaut est le plus restrictif possible</strong> : hors contexte —
 * tache planifiee, connexion du pool rendue puis reprise, test mal isole — la
 * portee n'est pas globale et aucun agent n'est designe, donc les politiques ne
 * rendent rien. Une portee absente ne doit jamais valoir « tout voir ».
 */
public final class PorteeContext {

    /** Portee d'un appelant : soit globale, soit limitee a un agent. */
    public record Portee(boolean globale, Long agentId) {

        /**
         * Portee des profils de pilotage et des appelants machine.
         *
         * <p>Nommee ainsi et non `globale()` : ce nom-la est deja celui de
         * l'accesseur du composant, et un enregistrement Java ne tolere pas la
         * collision.
         */
        public static Portee touteExploitation() {
            return new Portee(true, null);
        }

        /** Portee d'un agent de terrain, limitee a ses affectations. */
        public static Portee agent(Long agentId) {
            return new Portee(false, agentId);
        }

        /** Aucune visibilite : identite connue, mais rattachee a aucun agent. */
        public static Portee aucune() {
            return new Portee(false, null);
        }
    }

    private static final ThreadLocal<Portee> COURANTE = new ThreadLocal<>();

    private PorteeContext() {
    }

    /** Portee courante ; {@link Portee#aucune()} a defaut — jamais « globale ». */
    public static Portee courante() {
        Portee portee = COURANTE.get();
        return portee == null ? Portee.aucune() : portee;
    }

    public static void definir(Portee portee) {
        if (portee == null) {
            COURANTE.remove();
        } else {
            COURANTE.set(portee);
        }
    }

    public static void effacer() {
        COURANTE.remove();
    }

    /**
     * Execute une action sous une portee donnee, puis restaure l'etat precedent.
     *
     * <p>Utile aux traitements hors requete HTTP — notification d'alerte, rapport
     * planifie — qui doivent lire l'ensemble d'une exploitation et n'ont aucun
     * agent pour le faire.
     */
    public static <T> T executer(Portee portee, Supplier<T> action) {
        Portee precedente = COURANTE.get();
        definir(portee);
        try {
            return action.get();
        } finally {
            definir(precedente);
        }
    }
}
