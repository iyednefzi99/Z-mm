package com.zumm.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.web.dto.SiteReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * La position exacte d'un rucher est la donnee la plus sensible du produit : le
 * vol de ruches est le premier sinistre du metier. Ces tests fixent la regle
 * d'exposition, et surtout ce qu'elle ne doit PAS laisser passer.
 */
class PolitiquePositionsSelonRoleTest {

    private final ConfigurationMetier configuration = mock(ConfigurationMetier.class);
    private final PolitiquePositionsSelonRole politique =
            new PolitiquePositionsSelonRole(configuration);

    private static final SiteReponse RUCHER = new SiteReponse(
            1L, "Rucher des tilleuls", 2L, "Ferme du causse",
            new BigDecimal("44.123456"), new BigDecimal("1.987654"), new BigDecimal("312.50"),
            LocalDate.of(2026, 3, 1), null, null, Instant.EPOCH, Instant.EPOCH);

    private void authentifier(String... roles) {
        var autorites = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("agent", "n/a", autorites));
    }

    private void seuilArrondi(int decimales) {
        SeuilsMetier defauts = SeuilsMetier.defauts();
        when(configuration.seuils()).thenReturn(new SeuilsMetier(
                defauts.langueParDefaut(), defauts.languesActives(), defauts.poidsRucheAlerteKg(),
                defauts.temperatureMinCelsius(), defauts.temperatureMaxCelsius(),
                defauts.humiditeMaxPourcent(), defauts.delaiAlerteJours(), decimales,
                defauts.taillePageParDefaut()));
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("le responsable voit la position exacte")
    void responsableVoitExact() {
        authentifier("ROLE_responsable");
        assertThat(politique.masquer(RUCHER).latitude()).isEqualByComparingTo("44.123456");
        assertThat(politique.masquer(RUCHER).altitude()).isEqualByComparingTo("312.50");
    }

    @Test
    @DisplayName("l'apiculteur recoit une position arrondie au seuil configure")
    void apiculteurRecoitArrondi() {
        authentifier("ROLE_apiculteur");
        seuilArrondi(2);
        SiteReponse vue = politique.masquer(RUCHER);
        // 2 decimales de degre ≈ 1,1 km : de quoi situer le rucher sur une carte,
        // pas de quoi s'y rendre de nuit.
        assertThat(vue.latitude()).isEqualByComparingTo("44.12");
        assertThat(vue.longitude()).isEqualByComparingTo("1.99");
    }

    @Test
    @DisplayName("l'altitude disparait avec la position exacte")
    void altitudeMasquee() {
        authentifier("ROLE_apiculteur");
        seuilArrondi(2);
        // En terrain accidente, l'altitude discrimine autant qu'une coordonnee :
        // la laisser au metre pres reduirait a neant l'arrondi.
        assertThat(politique.masquer(RUCHER).altitude()).isNull();
    }

    @Test
    @DisplayName("le reste de la fiche est intact")
    void resteDeLaFicheIntact() {
        authentifier("ROLE_superviseur");
        seuilArrondi(2);
        SiteReponse vue = politique.masquer(RUCHER);
        assertThat(vue.id()).isEqualTo(1L);
        assertThat(vue.nom()).isEqualTo("Rucher des tilleuls");
        assertThat(vue.fermeNom()).isEqualTo("Ferme du causse");
        assertThat(vue.dateMiseEnOeuvre()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("un appelant non authentifie n'obtient jamais la position exacte")
    void anonymeMasque() {
        seuilArrondi(2);
        assertThat(politique.positionExacteAutorisee()).isFalse();
        assertThat(politique.masquer(RUCHER).latitude()).isEqualByComparingTo("44.12");
    }

    @Test
    @DisplayName("un seuil negatif desactive l'arrondi")
    void seuilNegatifDesactive() {
        // Soupape d'exploitation documentee : une exploitation qui assume la
        // publication de ses positions peut la retablir sans redeploiement.
        authentifier("ROLE_apiculteur");
        seuilArrondi(-1);
        assertThat(politique.masquer(RUCHER).latitude()).isEqualByComparingTo("44.123456");
    }
}
