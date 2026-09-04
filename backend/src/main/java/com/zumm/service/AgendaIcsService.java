package com.zumm.service;

import com.zumm.domain.Planning;
import com.zumm.domain.StatutPlanning;
import com.zumm.repository.PlanningRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Export des visites planifiees au format iCalendar (RFC 5545, SPRINT-21).
 *
 * <p>Repond au ❌ du §1 de {@code docs/ECART-CONCURRENTS.md} :
 * {@code CalendrierService} produisait une matrice agents × ruches pour l'ecran,
 * pas un flux consommable par Google Agenda, Outlook ou Apple Calendar.
 *
 * <p><strong>Deux usages, une seule fabrique.</strong> Le TELECHARGEMENT
 * authentifie ({@link #calendrier}) couvre l'essentiel — « mes visites de la
 * semaine dans mon agenda » — sans creer le moindre secret. L'ABONNEMENT
 * ({@link #calendrierAgent}), lui, est servi a un client de calendrier qui
 * appelle sans session : il suppose donc un jeton dans l'URL, et il a fallu
 * lever l'objection plutot que la contourner — 256 bits, stocke en empreinte
 * seule, expirant, revocable, borne a l'agenda d'un agent. Voir
 * {@code AbonnementCalendrierService}, ou tient toute cette garantie.
 *
 * <p><strong>Aucune position dans le fichier.</strong> Un .ics quitte
 * l'application : il est synchronise chez un tiers, indexe, sauvegarde. Le
 * {@code LOCATION} ne porte donc que le NOM du rucher et, au plus, sa commune —
 * jamais l'adresse ni les coordonnees, que {@code PolitiquePositions} masque
 * jusque dans l'API. Exporter en clair ce que l'API arrondit aurait vide la
 * mesure de son sens.
 */
@Service
@Transactional(readOnly = true)
public class AgendaIcsService {

    /** Horodatage UTC de base : « 20260830T142530Z ». */
    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Duree retenue quand le planning n'en donne pas : une visite type. */
    private static final int DUREE_PAR_DEFAUT_MIN = 30;

    private final PlanningRepository plannings;

    public AgendaIcsService(PlanningRepository plannings) {
        this.plannings = plannings;
    }

    /**
     * Calendrier des visites planifiees entre deux dates incluses.
     *
     * <p>Les plannings REFUSES sont exclus : un refus n'est pas un rendez-vous, et
     * le voir apparaitre dans un agenda personnel ferait deplacer l'apiculteur
     * pour rien. Les proposes, eux, restent — avec leur statut dans le titre,
     * parce qu'une visite en attente d'approbation se prepare quand meme.
     */
    public String calendrier(LocalDate debut, LocalDate fin) {
        return rendre(plannings.parPeriode(debut, fin, StatutPlanning.REFUSE));
    }

    /** Fabrique commune aux deux calendriers : une seule facon d'ecrire un .ics. */
    private String rendre(List<Planning> retenus) {
        StringBuilder ics = new StringBuilder();
        ligne(ics, "BEGIN:VCALENDAR");
        ligne(ics, "VERSION:2.0");
        ligne(ics, "PRODID:-//Zumm//Planning de visites//FR");
        ligne(ics, "CALSCALE:GREGORIAN");
        ligne(ics, "METHOD:PUBLISH");
        ligne(ics, "X-WR-CALNAME:Zumm — visites planifiees");
        String maintenant = HORODATAGE.format(LocalDateTime.now(ZoneOffset.UTC));
        retenus.forEach(planning -> evenement(ics, planning, maintenant));
        ligne(ics, "END:VCALENDAR");
        return ics.toString();
    }

    /**
     * Calendrier d'un SEUL agent, servi a un abonnement (SPRINT-21).
     *
     * <p>Meme fabrique que {@link #calendrier}, meme refus d'exporter la moindre
     * position : ce fichier-la va plus loin encore que le telechargement, puisque
     * le client de calendrier le recopie chez un tiers a chaque rafraichissement.
     */
    public String calendrierAgent(Long agentId, LocalDate debut, LocalDate fin) {
        return rendre(plannings.parPeriodeEtAgent(agentId, debut, fin, StatutPlanning.REFUSE));
    }

    private void evenement(StringBuilder ics, Planning planning, String maintenant) {
        ligne(ics, "BEGIN:VEVENT");
        // UID stable : reimporter le meme calendrier MET A JOUR l'evenement au
        // lieu d'en creer un doublon. C'est ce que trois des douze concurrents
        // ratent, et ce qui rend leur export inutilisable a la deuxieme semaine.
        ligne(ics, "UID:zumm-planning-" + planning.getId() + "@zumm");
        ligne(ics, "DTSTAMP:" + maintenant);
        ligne(ics, horaire(planning));
        ligne(ics, "SUMMARY:" + echapper(titre(planning)));
        String lieu = lieu(planning);
        if (lieu != null) {
            ligne(ics, "LOCATION:" + echapper(lieu));
        }
        ligne(ics, "DESCRIPTION:" + echapper(description(planning)));
        // Un planning propose n'est pas confirme : le dire au calendrier evite de
        // bloquer un creneau qu'un superviseur peut encore refuser.
        ligne(ics, "STATUS:" + (planning.getStatut() == StatutPlanning.APPROUVE
                ? "CONFIRMED" : "TENTATIVE"));
        ligne(ics, "END:VEVENT");
    }

    /**
     * Une visite sans heure est un evenement de JOURNEE, pas une visite a minuit.
     *
     * <p>La distinction compte a l'usage : place a 00:00, la visite se range dans
     * l'agenda la veille au soir pour tout client de calendrier en fuseau negatif,
     * et reveille les notifications a l'aube.
     */
    private String horaire(Planning planning) {
        if (planning.getHeurePrevue() == null) {
            return "DTSTART;VALUE=DATE:" + JOUR.format(planning.getDatePrevue()) + "\r\n"
                    + "DTEND;VALUE=DATE:" + JOUR.format(planning.getDatePrevue().plusDays(1));
        }
        LocalDateTime debut = LocalDateTime.of(planning.getDatePrevue(), planning.getHeurePrevue());
        int duree = planning.getDureeMin() == null ? DUREE_PAR_DEFAUT_MIN : planning.getDureeMin();
        // Les heures saisies sont locales au rucher ; sans fuseau declare, un
        // client de calendrier les lit dans le sien, ce qui est le comportement
        // attendu pour un agent qui travaille la ou il habite.
        return "DTSTART:" + HORODATAGE.format(debut.atOffset(ZoneOffset.UTC)) + "\r\n"
                + "DTEND:" + HORODATAGE.format(
                        debut.plus(Duration.ofMinutes(duree)).atOffset(ZoneOffset.UTC));
    }

    private String titre(Planning planning) {
        String prefixe = planning.getStatut() == StatutPlanning.PROPOSE ? "[a approuver] " : "";
        return prefixe + "Visite ruche " + planning.getRuche().getId()
                + " — " + planning.getRaison().enBase();
    }

    /** Nom du rucher, et sa commune si elle est connue. Jamais l'adresse. */
    private String lieu(Planning planning) {
        if (planning.getRuche().getSite() == null) {
            return null;
        }
        String nom = planning.getRuche().getSite().getNom();
        String ville = planning.getRuche().getSite().getVille();
        return ville == null || ville.isBlank() ? nom : nom + ", " + ville;
    }

    private String description(Planning planning) {
        return "Agent : " + planning.getAgent().getNom()
                + "\nRuche : " + planning.getRuche().getModele()
                + "\nStatut : " + planning.getStatut().enBase();
    }

    /**
     * Echappement RFC 5545 : la virgule, le point-virgule et la barre oblique
     * inverse sont des separateurs de propriete, et un saut de ligne non echappe
     * coupe l'evenement en deux. Un nom de rucher contenant une virgule suffirait
     * a rendre le fichier illisible par le client de calendrier.
     */
    private static String echapper(String valeur) {
        return valeur.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /**
     * Ecrit une ligne repliee a 75 octets, comme l'exige la RFC.
     *
     * <p>Le decoupage se fait sur les OCTETS et non sur les caracteres, mais sans
     * jamais couper au milieu d'un caractere UTF-8 : « miellee d'acacia » compte
     * ses accents double, et une coupure au mauvais octet produirait un fichier
     * que les clients de calendrier refusent d'ouvrir.
     */
    private static void ligne(StringBuilder ics, String contenu) {
        for (String partie : contenu.split("\r\n")) {
            byte[] octets = partie.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (octets.length <= 75) {
                ics.append(partie).append("\r\n");
                continue;
            }
            int position = 0;
            boolean premiere = true;
            while (position < octets.length) {
                int reste = premiere ? 75 : 74;
                int fin = Math.min(position + reste, octets.length);
                // Recule tant que l'octet suivant est une continuation UTF-8.
                while (fin < octets.length && (octets[fin] & 0xC0) == 0x80) {
                    fin--;
                }
                ics.append(premiere ? "" : " ")
                        .append(new String(octets, position, fin - position,
                                java.nio.charset.StandardCharsets.UTF_8))
                        .append("\r\n");
                position = fin;
                premiere = false;
            }
        }
    }
}
