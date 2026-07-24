package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Ruche;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Visite;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.service.ExportService.Format;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests unitaires de l'export CSV/TXT (US-027, US-036). */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private VisiteRepository visites;

    @Mock
    private RucheRepository ruches;

    @Test
    @DisplayName("CSV : en-tête, et champ contenant une virgule échappé par des guillemets")
    void csvEchappeLesVirgules() {
        Ruche ruche = new Ruche("Dadant", null, null, com.zumm.domain.EtatRuche.CREEE);
        Agent agent = new Agent("Ava", RoleAgent.APICULTEUR, null);
        Visite visite = new Visite(ruche, agent, LocalDate.of(2026, 9, 10), RaisonVisite.CONTROLE);
        visite.setConstatations("Colonie forte, calme");
        when(visites.findAllByOrderByDateVisiteAsc()).thenReturn(List.of(visite));

        String csv = new ExportService(visites, ruches).exporterVisites(Format.CSV);

        assertThat(csv).startsWith("id,date,heure");
        // La virgule interne force l'entourage par des guillemets (RFC 4180).
        assertThat(csv).contains("\"Colonie forte, calme\"");
    }

    @Test
    @DisplayName("TXT : séparateur tabulation")
    void txtSepareParTabulation() {
        Ruche ruche = new Ruche("Dadant", null, null, com.zumm.domain.EtatRuche.CREEE);
        Agent agent = new Agent("Ben", RoleAgent.APICULTEUR, null);
        Visite visite = new Visite(ruche, agent, LocalDate.of(2026, 9, 11), RaisonVisite.RECOLTE);
        when(visites.findAllByOrderByDateVisiteAsc()).thenReturn(List.of(visite));

        String txt = new ExportService(visites, ruches).exporterVisites(Format.TXT);

        assertThat(txt).startsWith("id\tdate\theure");
        assertThat(txt).contains("Dadant\tBen");
    }
}
