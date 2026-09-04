package com.zumm.service;

import com.zumm.domain.PartageTelemetrie;
import com.zumm.domain.TypeIndicateur;
import com.zumm.tenant.PorteeContext;
import com.zumm.tenant.TenantContext;
import com.zumm.web.dto.FluxPartage;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sert le flux d'un partage de telemetrie, sans session (SPRINT-26, lot F1).
 *
 * <p>Jumeau de {@code FluxCalendrierService}, et pour les memes raisons exactes.
 *
 * <p><strong>Hors transaction.</strong> Si cette methode etait transactionnelle,
 * la connexion serait prise AVANT que les contextes soient poses, et la requete
 * partirait sans tenant — en rendant zero point, silencieusement. En restant
 * dehors, elle laisse {@link MesureService} ouvrir la sienne apres coup,
 * contextes en place.
 *
 * <p><strong>Service distinct</strong> de {@link PartageTelemetrieService}, et
 * non une methode de plus : la resolution du jeton doit etre transactionnelle
 * pour que l'horodatage d'usage soit ecrit, et un appel de ce service sur
 * lui-meme contournerait le proxy.
 *
 * <p><strong>Portee globale, borne par le jeton.</strong> Le destinataire n'est
 * agent de rien — il n'est pas de l'exploitation. La portee est donc globale le
 * temps de la lecture, mais la seule chose lue est la serie de LA ruche que le
 * jeton nomme : la portee est le mecanisme, le jeton reste la borne.
 *
 * <p><strong>Le nettoyage est ici</strong> et non dans {@code TenantFilter} : le
 * chemin du partage est exempte de ce filtre — il doit repondre sans jeton
 * d'acces —, donc son {@code finally} ne s'executerait pas. Un
 * {@code ThreadLocal} laisse en place fuiterait sur la requete suivante servie
 * par le meme fil, et fuiterait ici une EXPLOITATION.
 */
@Service
public class FluxPartageService {

    private final PartageTelemetrieService partages;
    private final MesureService mesures;

    public FluxPartageService(PartageTelemetrieService partages, MesureService mesures) {
        this.partages = partages;
        this.mesures = mesures;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<FluxPartage> flux(String jeton, TypeIndicateur type) {
        Optional<PartageTelemetrie> trouve = partages.resoudre(jeton);
        if (trouve.isEmpty()) {
            return Optional.empty();
        }
        PartageTelemetrie partage = trouve.get();
        TenantContext.definir(partage.getTenantId());
        PorteeContext.definir(PorteeContext.Portee.touteExploitation());
        try {
            return Optional.of(new FluxPartage(partage.getLibelle(),
                    mesures.serieJournaliere(partage.getRucheId(), type)));
        } finally {
            PorteeContext.effacer();
            TenantContext.effacer();
        }
    }
}
