package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zumm.domain.EffectifQualitatif;
import com.zumm.domain.EtatSante;
import com.zumm.domain.RaisonVisite;
import com.zumm.web.dto.PhotoReponse;
import com.zumm.web.dto.VisiteReponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests unitaires du rendu PDF du rapport de visite (US-044, SPRINT-09). */
class RapportVisitePdfServiceTest {

    private final RapportVisitePdfService service = new RapportVisitePdfService();

    private VisiteReponse visite(List<PhotoReponse> photos) {
        return new VisiteReponse(1L, 2L, "Langstroth", 3L, "Amine Trabelsi", null,
                LocalDate.of(2026, 6, 15), LocalTime.of(9, 30), 25, RaisonVisite.CONTROLE,
                "Colonie vigoureuse, couvain compact.", "Poser une hausse", "Hausse posée",
                "Surveiller les réserves", EffectifQualitatif.FORT, EtatSante.BON, 3,
                photos, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("produit un PDF non vide (en-tête %PDF)")
    void produitUnPdf() {
        byte[] pdf = service.generer(visite(List.of()));

        assertThat(pdf).isNotEmpty();
        // Signature d'un fichier PDF : les octets « %PDF ».
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("inclut les photos sans échouer")
    void inclutLesPhotos() {
        var photo = new PhotoReponse(9L, "https://demo.zumm.tn/p.jpg", "Cadre de couvain", Instant.now());

        byte[] pdf = service.generer(visite(List.of(photo)));

        assertThat(pdf).isNotEmpty();
    }
}
