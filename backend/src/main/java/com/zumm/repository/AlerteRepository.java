package com.zumm.repository;

import com.zumm.domain.Alerte;
import com.zumm.domain.TypeIndicateur;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux alertes de seuils (US-018). Restreint au tenant courant
 * (@TenantId + RLS). Une seule alerte ouverte par (ruche, indicateur).
 */
public interface AlerteRepository extends JpaRepository<Alerte, Long> {

    /**
     * Alerte de SEUIL actuellement ouverte pour une ruche et un indicateur.
     *
     * <p>La categorie est dans la signature depuis le SPRINT-31 : sans elle,
     * une alerte de vol deja ouverte sur le poids aurait empeche l'alerte de
     * seuil de s'ouvrir, et reciproquement. Une ruche peut etre legere ET volee.
     */
    Optional<Alerte> findByRuche_IdAndTypeIndicateurAndCategorieAndOuverteTrue(
            Long rucheId, TypeIndicateur type, String categorie);

    /** Alertes ouvertes, les plus recentes d'abord (tableau de bord / synthese). */
    List<Alerte> findByOuverteTrueOrderByOuverteLeDesc();

    /**
     * Nombre d'alertes ouvertes.
     *
     * <p>La synthese en affichait le COMPTE apres avoir charge la liste entiere.
     * Compter est le travail du SGBD ; rapatrier les lignes pour en mesurer la
     * taille ne l'a jamais ete.
     */
    long countByOuverteTrue();
}
