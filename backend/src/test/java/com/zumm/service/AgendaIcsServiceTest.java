package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.Planning;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.StatutPlanning;
import com.zumm.repository.PlanningRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests de l'export iCalendar (SPRINT-21, §1 de {@code docs/ECART-CONCURRENTS.md}).
 *
 * <p>Trois proprietes valent d'etre verrouillees ici, parce qu'aucune ne se voit
 * a la lecture du fichier produit : le refus d'exporter une position, la
 * distinction entre visite datee et visite horodatee, et l'echappement RFC 5545 —
 * un nom de rucher contenant une virgule suffit a rendre un .ics illisible par le
 * client de calendrier qui le recoit.
 */
@ExtendWith(MockitoExtension.class)
class AgendaIcsServiceTest {

    @Mock
    private PlanningRepository plannings;

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 7);

    private Planning planning(long id, LocalTime heure, String nomSite, String ville,
            StatutPlanning statut) {
        Site site = mock(Site.class);
        when(site.getNom()).thenReturn(nomSite);
        when(site.getVille()).thenReturn(ville);

        Ruche ruche = mock(Ruche.class);
        when(ruche.getId()).thenReturn(42L);
        when(ruche.getModele()).thenReturn("Dadant 10 cadres");
        when(ruche.getSite()).thenReturn(site);

        Agent agent = mock(Agent.class);
        when(agent.getNom()).thenReturn("Amel");

        Planning planning = mock(Planning.class);
        when(planning.getId()).thenReturn(id);
        when(planning.getRuche()).thenReturn(ruche);
        when(planning.getAgent()).thenReturn(agent);
        when(planning.getDatePrevue()).thenReturn(LUNDI);
        when(planning.getHeurePrevue()).thenReturn(heure);
        if (heure != null) {
            // La duree n'est lue que par la branche horodatee : la stuber pour une
            // visite de journee ferait echouer Mockito sur un stub inutile.
            when(planning.getDureeMin()).thenReturn(45);
        }
        when(planning.getRaison()).thenReturn(RaisonVisite.CONTROLE);
        when(planning.getStatut()).thenReturn(statut);
        return planning;
    }

    private String calendrier(Planning... contenu) {
        when(plannings.parPeriode(any(), any(), eq(StatutPlanning.REFUSE)))
                .thenReturn(List.of(contenu));
        return new AgendaIcsService(plannings).calendrier(LUNDI, LUNDI.plusDays(7));
    }

    @Test
    @DisplayName("produit un calendrier valide avec un evenement par planning")
    void enveloppeValide() {
        String ics = calendrier(
                planning(1L, LocalTime.of(9, 30), "Rucher des tilleuls", "Figeac",
                        StatutPlanning.APPROUVE));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("VERSION:2.0", "PRODID:-//Zumm//Planning de visites//FR");
        // UID stable : reimporter le fichier met a jour l'evenement au lieu d'en
        // creer un doublon — le defaut reproche a trois des douze concurrents.
        assertThat(ics).contains("UID:zumm-planning-1@zumm");
        assertThat(ics).contains("SUMMARY:Visite ruche 42 — controle");
        assertThat(ics).contains("STATUS:CONFIRMED");
    }

    @Test
    @DisplayName("une visite avec heure devient un creneau, sa duree comprise")
    void visiteHorodatee() {
        String ics = calendrier(
                planning(2L, LocalTime.of(9, 30), "Rucher du causse", null,
                        StatutPlanning.APPROUVE));

        assertThat(ics).contains("DTSTART:20260907T093000Z");
        // 9 h 30 + 45 min : la duree saisie, pas la duree par defaut.
        assertThat(ics).contains("DTEND:20260907T101500Z");
    }

    @Test
    @DisplayName("une visite sans heure est un evenement de journee")
    void visiteSansHeure() {
        String ics = calendrier(
                planning(3L, null, "Rucher du causse", null, StatutPlanning.APPROUVE));

        // Place a 00:00, la visite se rangerait la veille au soir pour tout client
        // en fuseau negatif, et reveillerait les notifications a l'aube.
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260907");
        assertThat(ics).contains("DTEND;VALUE=DATE:20260908");
        assertThat(ics).doesNotContain("DTSTART:20260907T000000Z");
    }

    @Test
    @DisplayName("un planning propose sort en TENTATIVE, avec sa mention dans le titre")
    void planningPropose() {
        String ics = calendrier(
                planning(4L, LocalTime.of(8, 0), "Rucher du causse", null,
                        StatutPlanning.PROPOSE));

        assertThat(ics).contains("STATUS:TENTATIVE");
        assertThat(ics).contains("SUMMARY:[a approuver] Visite ruche 42");
    }

    @Test
    @DisplayName("le lieu ne porte que le nom du rucher et sa commune")
    void aucunePositionExportee() {
        String ics = calendrier(
                planning(5L, LocalTime.of(8, 0), "Rucher des tilleuls", "Figeac",
                        StatutPlanning.APPROUVE));

        assertThat(ics).contains("LOCATION:Rucher des tilleuls\\, Figeac");
        // Un .ics quitte l'application : il est synchronise chez un tiers, indexe,
        // sauvegarde. Exporter en clair ce que l'API arrondit viderait le masque
        // de son sens.
        assertThat(ics).doesNotContain("GEO:");
        assertThat(ics).doesNotContain("44.1");
    }

    @Test
    @DisplayName("la virgule d'un nom de rucher est echappee, pas laissee brute")
    void echappementRfc() {
        String ics = calendrier(
                planning(6L, LocalTime.of(8, 0), "Rucher; les tilleuls, sud", null,
                        StatutPlanning.APPROUVE));

        assertThat(ics).contains("LOCATION:Rucher\\; les tilleuls\\, sud");
    }

    @Test
    @DisplayName("une ruche sans rucher sort sans ligne LOCATION, pas avec une ligne vide")
    void rucheSansRucher() {
        Ruche ruche = mock(Ruche.class);
        when(ruche.getId()).thenReturn(42L);
        when(ruche.getModele()).thenReturn("Dadant 10 cadres");
        when(ruche.getSite()).thenReturn(null);
        Agent agent = mock(Agent.class);
        when(agent.getNom()).thenReturn("Amel");
        Planning planning = mock(Planning.class);
        when(planning.getId()).thenReturn(7L);
        when(planning.getRuche()).thenReturn(ruche);
        when(planning.getAgent()).thenReturn(agent);
        when(planning.getDatePrevue()).thenReturn(LUNDI);
        when(planning.getHeurePrevue()).thenReturn(LocalTime.of(9, 0));
        when(planning.getDureeMin()).thenReturn(30);
        when(planning.getRaison()).thenReturn(RaisonVisite.CONTROLE);
        when(planning.getStatut()).thenReturn(StatutPlanning.APPROUVE);

        String ics = calendrier(planning);

        // `LOCATION:` suivi de rien est une ligne que certains clients de
        // calendrier refusent, et qui n'apporte rien : mieux vaut l'absence.
        assertThat(ics).contains("BEGIN:VEVENT").doesNotContain("LOCATION:");
    }

    @Test
    @DisplayName("une commune vide ne laisse pas de virgule pendante derriere le rucher")
    void communeVide() {
        String ics = calendrier(
                planning(8L, LocalTime.of(8, 0), "Rucher du haut", "   ",
                        StatutPlanning.APPROUVE));

        // « Rucher du haut, » se lit comme une commune qu'on aurait oublie de
        // saisir ; l'absence de virgule dit qu'il n'y en a pas.
        assertThat(ics).contains("LOCATION:Rucher du haut")
                .doesNotContain("Rucher du haut\\,");
    }

    @Test
    @DisplayName("une visite sans duree prend la duree par defaut")
    void dureeParDefaut() {
        Ruche ruche = mock(Ruche.class);
        when(ruche.getId()).thenReturn(42L);
        when(ruche.getModele()).thenReturn("Dadant");
        when(ruche.getSite()).thenReturn(null);
        Agent agent = mock(Agent.class);
        when(agent.getNom()).thenReturn("Amel");
        Planning planning = mock(Planning.class);
        when(planning.getId()).thenReturn(9L);
        when(planning.getRuche()).thenReturn(ruche);
        when(planning.getAgent()).thenReturn(agent);
        when(planning.getDatePrevue()).thenReturn(LUNDI);
        when(planning.getHeurePrevue()).thenReturn(LocalTime.of(10, 0));
        when(planning.getDureeMin()).thenReturn(null);
        when(planning.getRaison()).thenReturn(RaisonVisite.CONTROLE);
        when(planning.getStatut()).thenReturn(StatutPlanning.APPROUVE);

        String ics = calendrier(planning);

        // Un creneau de duree nulle s'affiche comme un point sur l'agenda de
        // l'agent : le defaut vaut mieux qu'un rendez-vous invisible.
        assertThat(ics).contains("DTSTART").contains("DTEND");
        assertThat(ics).doesNotContain("DTEND;TZID=UTC:20260907T100000");
    }

    @Test
    @DisplayName("un calendrier vide reste un calendrier valide")
    void aucunPlanning() {
        String ics = calendrier();

        assertThat(ics).startsWith("BEGIN:VCALENDAR").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).doesNotContain("BEGIN:VEVENT");
    }

    @Test
    @DisplayName("les lignes trop longues sont repliees sans couper un caractere")
    void repliementDesLignes() {
        String nomTresLong = "Rucher des tilleuls centenaires du plateau de l'Aubrac oriental "
                + "et de ses environs immediats";
        String ics = calendrier(
                planning(7L, LocalTime.of(8, 0), nomTresLong, "Saint-Chely-d'Aubrac",
                        StatutPlanning.APPROUVE));

        // RFC 5545 : 75 octets par ligne, la suite prefixee d'une espace.
        for (String ligne : ics.split("\r\n")) {
            assertThat(ligne.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(75);
        }
        assertThat(ics).contains("\r\n ");
    }
}
