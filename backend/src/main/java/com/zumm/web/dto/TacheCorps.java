package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Corps de requete pour creer ou mettre a jour une tache (US-031).
 *
 * @param libelle  intitule de la tache, obligatoire
 * @param rucheId  ruche concernee (optionnel) ; doit exister dans le tenant
 * @param agentId  agent assigne (optionnel) ; doit exister dans le tenant
 * @param echeance  date d'echeance (optionnel), alimente les rappels
 * @param faite     tache accomplie
 * @param priorite  basse | normale | haute | critique. Absente, elle vaut
 *                  {@code normale} : une tache sans priorite declaree n'est pas
 *                  une tache urgente
 * @param categorie controle | traitement | nourrissement | recolte | materiel |
 *                  elevage | administratif | autre
 * @param consommableId  ce que la tache consomme (SPRINT-33), pour la feuille de
 *                  chargement. Optionnel : toutes les taches ne prelevent rien
 * @param quantitePrevue combien, dans l'unite du consommable. Optionnelle meme
 *                  quand le consommable est designe : « prendre du candi » sans
 *                  savoir encore combien reste une consigne utile
 */
public record TacheCorps(
        @NotBlank @Size(max = 200) String libelle,
        Long rucheId,
        Long agentId,
        LocalDate echeance,
        boolean faite,
        @Pattern(regexp = "basse|normale|haute|critique") String priorite,
        @Pattern(regexp = "controle|traitement|nourrissement|recolte|materiel"
                + "|elevage|administratif|autre") String categorie,
        Long consommableId,
        @jakarta.validation.constraints.Positive java.math.BigDecimal quantitePrevue) {
}
