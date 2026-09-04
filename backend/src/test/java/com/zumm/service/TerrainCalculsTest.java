package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zumm.domain.RessourceFlorale;
import com.zumm.domain.Transport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Les deux calculs du terrain qui ne se voient pas a la lecture (SPRINT-21).
 *
 * <p>Ni l'un ni l'autre ne touche la base, et c'est exactement pour cela qu'ils
 * meritent un test unitaire : une fenetre de floraison mal lue ou un nombre de
 * voyages arrondi par defaut ne produit aucune erreur, seulement une reponse
 * fausse.
 */
class TerrainCalculsTest {

    @Nested
    @DisplayName("fenetre de floraison")
    class Floraison {

        private RessourceFlorale ressource(Integer debut, Integer fin) {
            RessourceFlorale ressource = new RessourceFlorale("eucalyptus", null, null);
            ressource.setMoisDebut(debut);
            ressource.setMoisFin(fin);
            return ressource;
        }

        @Test
        @DisplayName("une fenetre ordinaire se lit du debut a la fin")
        void fenetreOrdinaire() {
            RessourceFlorale tilleul = ressource(6, 7);

            assertThat(tilleul.enFloraison(6)).isTrue();
            assertThat(tilleul.enFloraison(7)).isTrue();
            assertThat(tilleul.enFloraison(5)).isFalse();
            assertThat(tilleul.enFloraison(8)).isFalse();
        }

        @Test
        @DisplayName("une fenetre qui enjambe l'annee inclut decembre et exclut juin")
        void fenetreAChevalSurLAnnee() {
            // L'eucalyptus du Sud fleurit de novembre a fevrier. Un simple
            // « entre debut et fin » repondrait exactement l'inverse sur les
            // quatre mois qui comptent.
            RessourceFlorale eucalyptus = ressource(11, 2);

            assertThat(eucalyptus.enFloraison(11)).isTrue();
            assertThat(eucalyptus.enFloraison(12)).isTrue();
            assertThat(eucalyptus.enFloraison(1)).isTrue();
            assertThat(eucalyptus.enFloraison(2)).isTrue();
            assertThat(eucalyptus.enFloraison(6)).isFalse();
            assertThat(eucalyptus.enFloraison(10)).isFalse();
        }

        @Test
        @DisplayName("une fenetre absente ne fleurit jamais, plutot que toujours")
        void fenetreAbsente() {
            // Le repli compte : une ressource sans periode declaree ne doit pas
            // se retrouver « en fleur » douze mois sur douze dans un futur
            // calendrier de miellees.
            assertThat(ressource(null, null).enFloraison(5)).isFalse();
            assertThat(ressource(5, null).enFloraison(5)).isFalse();
        }
    }

    @Nested
    @DisplayName("voyages d'un transport")
    class Voyages {

        private Transport transport(Integer capacite, Integer ruches) {
            Transport transport = new Transport(null, null, null, "Plateau");
            transport.setCapaciteRuches(capacite);
            transport.setNbRuches(ruches);
            return transport;
        }

        @Test
        @DisplayName("le reste compte pour un voyage entier")
        void arrondiAuSuperieur() {
            // 41 ruches dans un camion de 20, c'est trois voyages, pas deux : un
            // arrondi par defaut laisserait une ruche au rucher.
            assertThat(transport(20, 41).voyages()).isEqualTo(3);
            assertThat(transport(20, 40).voyages()).isEqualTo(2);
            assertThat(transport(20, 1).voyages()).isEqualTo(1);
        }

        @Test
        @DisplayName("sans capacite ou sans ruches, aucun nombre n'est avance")
        void donneesIncompletes() {
            // Rendre 0 ou 1 par defaut ferait passer une inconnue pour une
            // reponse, et l'ecran l'afficherait comme telle.
            assertThat(transport(null, 40).voyages()).isNull();
            assertThat(transport(20, null).voyages()).isNull();
            assertThat(transport(20, 0).voyages()).isNull();
        }
    }
}
