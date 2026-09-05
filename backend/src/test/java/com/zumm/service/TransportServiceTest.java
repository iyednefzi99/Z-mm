package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.Site;
import com.zumm.domain.Transport;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.TransportRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.DemenagementCorps;
import com.zumm.web.dto.TransportCorps;
import com.zumm.web.dto.TransportReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Transports de transhumance (SPRINT-21, lot 3 du plan de couverture).
 *
 * <p>72,6 % d'instructions et 60 % de branches. Trois décisions du SPRINT-21 s'y
 * jouent, et la première est la plus facile à défaire par inadvertance :
 *
 * <ol>
 *   <li><strong>Réaliser un transport ne duplique pas le déménagement, il
 *       l'appelle.</strong> {@code SiteService.demenager} reste le seul endroit
 *       qui clôt un emplacement et en ouvre un autre — deux implémentations de
 *       cette règle finiraient par diverger, et c'est l'historique du parc qui
 *       en porterait la trace.</li>
 *   <li><strong>La destination sort masquée</strong>, comme toute position de
 *       rucher : une destination de transhumance <em>est</em> une position de
 *       rucher, avec une semaine d'avance.</li>
 *   <li><strong>Sans coordonnées, le plan reste un plan.</strong> Ouvrir un
 *       emplacement sans position ferait un trou dans l'historique — et un trou
 *       dans un historique ne se voit pas.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TransportServiceTest {

    @Mock private TransportRepository transports;
    @Mock private SiteRepository sites;
    @Mock private AgentRepository agents;
    @Mock private SiteService siteService;
    @Mock private PolitiquePositions positions;

    private TransportService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    private static final BigDecimal LAT = new BigDecimal("36.812345");
    private static final BigDecimal LON = new BigDecimal("10.234567");

    @BeforeEach
    void monter() {
        service = new TransportService(transports, sites, agents, siteService, positions);
        lenient().when(transports.save(any())).thenAnswer(i -> i.getArgument(0));
        // Le masquage par défaut : deux décimales, comme pour un rucher.
        lenient().when(positions.masquer(any(), any()))
                .thenReturn(new BigDecimal[] {new BigDecimal("36.81"), new BigDecimal("10.23")});
    }

    private static Site site(Long id) {
        Site s = mock(Site.class);
        lenient().when(s.getId()).thenReturn(id);
        lenient().when(s.getNom()).thenReturn("Rucher du haut");
        return s;
    }

    private static Agent agent() {
        Agent a = mock(Agent.class);
        lenient().when(a.getId()).thenReturn(3L);
        lenient().when(a.getNom()).thenReturn("Amal");
        return a;
    }

    private static Transport transport(Site site, String statut, BigDecimal lat, BigDecimal lon) {
        Agent a = agent();
        Transport t = mock(Transport.class);
        lenient().when(t.getSite()).thenReturn(site);
        lenient().when(t.getAgent()).thenReturn(a);
        lenient().when(t.getStatut()).thenReturn(statut);
        lenient().when(t.getDatePrevue()).thenReturn(JOUR);
        lenient().when(t.getDestinationLibelle()).thenReturn("Plateau de Zaghouan");
        lenient().when(t.getDestinationLatitude()).thenReturn(lat);
        lenient().when(t.getDestinationLongitude()).thenReturn(lon);
        lenient().when(t.destinationLocalisee()).thenReturn(lat != null && lon != null);
        return t;
    }

    private static TransportCorps corps(BigDecimal lat, BigDecimal lon) {
        return new TransportCorps(1L, 3L, JOUR, null, "Camionnette", 20, 18,
                "  Plateau de Zaghouan  ", lat, lon, null);
    }

    // ── Planification ───────────────────────────────────────────────────────

    @Test
    @DisplayName("une latitude sans longitude est refusée : elle ne désigne rien")
    void coordonneesIncompletes() {
        Site s = site(1L);
        Agent a = agent();
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(agents.findById(3L)).thenReturn(Optional.of(a));

        // La base le refuserait aussi ; on le dit avant, et en clair.
        assertThatThrownBy(() -> service.planifier(corps(LAT, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("les deux coordonnees, ou aucune");
        assertThatThrownBy(() -> service.planifier(corps(null, LON)))
                .isInstanceOf(RequeteInvalide.class);
        verify(transports, never()).save(any());
    }

    @Test
    @DisplayName("aucune coordonnée est accepté : le plan se complète plus tard")
    void planSansCoordonnees() {
        Site s = site(1L);
        Agent a = agent();
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(agents.findById(3L)).thenReturn(Optional.of(a));

        service.planifier(corps(null, null));

        verify(transports).save(any(Transport.class));
    }

    @Test
    @DisplayName("le libellé de destination est débarrassé de ses espaces")
    void libelleNettoye() {
        Site s = site(1L);
        Agent a = agent();
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(agents.findById(3L)).thenReturn(Optional.of(a));

        TransportReponse reponse = service.planifier(corps(LAT, LON));

        assertThat(reponse.destinationLibelle()).isEqualTo("Plateau de Zaghouan");
    }

    @Test
    @DisplayName("un rucher ou un agent inconnu dans ce tenant est refusé")
    void rattachementsInconnus() {
        when(sites.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.planifier(corps(null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("1");

        Site s = site(1L);
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(agents.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.planifier(corps(null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("3");
    }

    // ── La destination masquée ──────────────────────────────────────────────

    @Test
    @DisplayName("la destination sort MASQUÉE, comme une position de rucher")
    void destinationMasquee() {
        Site s = site(1L);
        Agent a = agent();
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(agents.findById(3L)).thenReturn(Optional.of(a));

        TransportReponse reponse = service.planifier(corps(LAT, LON));

        // Une destination de transhumance EST une position de rucher, avec une
        // semaine d'avance : la publier en clair annulerait le masquage du
        // SPRINT-12 pour toute la durée du plan.
        verify(positions).masquer(LAT, LON);
        assertThat(reponse.destinationLatitude()).isEqualByComparingTo("36.81");
        assertThat(reponse.destinationLongitude()).isEqualByComparingTo("10.23");
    }

    @Test
    @DisplayName("un transport sans destination localisée ne passe pas par le masquage")
    void sansPositionAucunMasquage() {
        Site s = site(1L);
        Agent a = agent();
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(agents.findById(3L)).thenReturn(Optional.of(a));

        TransportReponse reponse = service.planifier(corps(null, null));

        assertThat(reponse.destinationLatitude()).isNull();
        verify(positions, never()).masquer(any(), any());
    }

    // ── Réalisation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("réaliser APPELLE le déménagement, il ne le duplique pas")
    void realiserDelegueAuDemenagement() {
        Site s = site(1L);
        Transport t = transport(s, "prevu", LAT, LON);
        when(transports.findById(1L)).thenReturn(Optional.of(t));

        service.realiser(1L);

        // `SiteService.demenager` reste le seul endroit qui clôt un emplacement
        // et en ouvre un autre. Deux implémentations de cette règle finiraient
        // par diverger, et c'est l'historique du parc qui en porterait la trace.
        ArgumentCaptor<DemenagementCorps> demenagement =
                ArgumentCaptor.forClass(DemenagementCorps.class);
        verify(siteService).demenager(eq(1L), demenagement.capture());
        assertThat(demenagement.getValue().latitude()).isEqualByComparingTo(LAT);
        assertThat(demenagement.getValue().longitude()).isEqualByComparingTo(LON);
        assertThat(demenagement.getValue().dateDebut()).isEqualTo(JOUR);
        // Le motif est fixe a « transhumance » : c'est ce qui rend l'historique
        // du parc lisible sans avoir a deviner pourquoi un rucher a bouge.
        assertThat(demenagement.getValue().motif()).isEqualTo("transhumance");
        verify(t).setStatut("realise");
    }

    @Test
    @DisplayName("sans coordonnées, réaliser est refusé : un trou d'historique ne se voit pas")
    void realiserSansCoordonnees() {
        Site s = site(1L);
        Transport t = transport(s, "prevu", null, null);
        when(transports.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.realiser(1L))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("pas de coordonnees");
        verify(siteService, never()).demenager(any(), any());
        verify(t, never()).setStatut(any());
    }

    @Test
    @DisplayName("un transport déjà réalisé ou annulé ne se réalise pas deux fois")
    void realiserDeuxFois() {
        Site s = site(1L);
        Transport realise = transport(s, "realise", LAT, LON);
        Transport annule = transport(s, "annule", LAT, LON);
        when(transports.findById(1L)).thenReturn(Optional.of(realise));
        when(transports.findById(2L)).thenReturn(Optional.of(annule));

        // Déménager deux fois vers le même point créerait deux emplacements
        // identiques dans l'historique, et le second serait faux.
        assertThatThrownBy(() -> service.realiser(1L))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("realise");
        assertThatThrownBy(() -> service.realiser(2L))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("annule");
        verify(siteService, never()).demenager(any(), any());
    }

    // ── Annulation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("un transport réalisé ne s'annule pas : le rucher a bougé")
    void annulerUnTransportRealise() {
        Site s = site(1L);
        Transport t = transport(s, "realise", LAT, LON);
        when(transports.findById(1L)).thenReturn(Optional.of(t));

        // Annuler un fait accompli laisserait un rucher déplacé et un plan qui
        // dit qu'il ne l'est pas.
        assertThatThrownBy(() -> service.annuler(1L))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("le rucher a bouge");
        verify(t, never()).setStatut(any());
    }

    @Test
    @DisplayName("un transport prévu s'annule")
    void annulerUnTransportPrevu() {
        Site s = site(1L);
        Transport t = transport(s, "prevu", LAT, LON);
        when(transports.findById(1L)).thenReturn(Optional.of(t));

        service.annuler(1L);

        verify(t).setStatut("annule");
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("les transports d'un rucher inconnu sortent en 404, pas en liste vide")
    void parSiteInconnu() {
        when(sites.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.parSite(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("les transports d'un rucher passent par son finder, destination masquée")
    void parSite() {
        Site s = site(1L);
        Transport t = transport(s, "prevu", LAT, LON);
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(transports.findBySite_IdOrderByDatePrevueDescIdDesc(1L)).thenReturn(List.of(t));

        List<TransportReponse> liste = service.parSite(1L);

        assertThat(liste).hasSize(1);
        assertThat(liste.get(0).destinationLatitude()).isEqualByComparingTo("36.81");
    }

    @Test
    @DisplayName("sans période, les prévus sont tous rendus ; avec période, ils sont bornés")
    void prevusAvecEtSansPeriode() {
        Site s = site(1L);
        Transport t = transport(s, "prevu", null, null);
        when(transports.findByStatutOrderByDatePrevueAscIdAsc("prevu")).thenReturn(List.of(t));
        when(transports.findByStatutAndDatePrevueBetweenOrderByDatePrevueAscIdAsc(
                "prevu", JOUR, JOUR.plusDays(30))).thenReturn(List.of());

        // Une période absente n'est pas une période vide : rendre zéro résultat
        // ferait croire qu'il n'y a rien à déplacer.
        assertThat(service.prevus(null, null)).hasSize(1);
        assertThat(service.prevus(JOUR, null)).hasSize(1);
        assertThat(service.prevus(JOUR, JOUR.plusDays(30))).isEmpty();
    }

    @Test
    @DisplayName("un transport inconnu est refusé en 404")
    void transportIntrouvable() {
        when(transports.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.realiser(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.annuler(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt")
    void suppression() {
        Site s = site(1L);
        Transport t = transport(s, "prevu", null, null);
        when(transports.findById(1L)).thenReturn(Optional.of(t));

        service.supprimer(1L);

        verify(transports).delete(t);
    }
}
