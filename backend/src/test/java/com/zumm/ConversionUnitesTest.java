package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zumm.service.ConversionUnites;
import com.zumm.web.RequeteInvalide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifie la conversion d'unites heterogenes (US-019). */
class ConversionUnitesTest {

    private final ConversionUnites conversion = new ConversionUnites();

    @Test
    @DisplayName("convertit les masses vers la reference et entre unites")
    void masses() {
        assertThat(conversion.convertir(1500, "g", "kg")).isEqualTo(1.5);
        assertThat(conversion.convertir(2, "kg", "g")).isEqualTo(2000);
        assertThat(conversion.convertir(1, "t", "kg")).isEqualTo(1000);
        assertThat(conversion.convertir(1, "lb", "g")).isCloseTo(453.592_37, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("convertit les temperatures (conversions affines)")
    void temperatures() {
        assertThat(conversion.convertir(0, "c", "f")).isEqualTo(32);
        assertThat(conversion.convertir(100, "c", "k")).isCloseTo(373.15, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(conversion.convertir(32, "f", "c")).isCloseTo(0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("refuse une unité inconnue ou des familles incompatibles")
    void erreurs() {
        assertThatThrownBy(() -> conversion.convertir(1, "g", "pouce"))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> conversion.convertir(1, "kg", "c"))
                .isInstanceOf(RequeteInvalide.class);
    }
}
