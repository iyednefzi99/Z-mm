# 🔐 Analyse d'impact relative à la protection des données (AIPD)

**Projet :** Zümm — Système de gestion et de suivi apicole
**Version :** 0.1 — **brouillon**
**Date :** 2026-07-19
**Statut :** 🟠 **À faire valider par un juriste ou un DPO**

> ⚠️ **Avertissement.** Ce document est un **brouillon technique** destiné à
> préparer le travail du délégué à la protection des données. Il n'est **pas**
> un avis juridique et n'engage personne en l'état. Une AIPD n'est valide
> qu'après revue par une personne compétente en protection des données et,
> le cas échéant, consultation de l'autorité de contrôle.

---

## 1. Pourquoi une AIPD est probablement obligatoire

L'article 35 du RGPD impose une AIPD lorsqu'un traitement est susceptible
d'engendrer un risque élevé pour les droits et libertés des personnes. Deux
critères des lignes directrices du CEPD sont ici réunis, ce qui déclenche
généralement l'obligation :

| Critère CEPD | Présence dans Zümm |
|:---|:---|
| **Données de localisation** | ✅ Coordonnées GPS des sites et des ruchers (US-003), carte et rayons de butinage (US-030) |
| **Suivi systématique de personnes** | ✅ Calendrier agents × ruches (US-012), horodatage et durée des visites (US-009) |
| Données à grande échelle | ⚠️ Dépend de la volumétrie (cf. ADR-002) |
| Croisement de jeux de données | ⚠️ Mesures capteurs × visites × agents |

**Conclusion provisoire : AIPD à mener.** Le point le plus sensible n'est pas
la donnée personnelle classique, mais la **combinaison géolocalisation +
horodatage + identité de l'agent**, qui permet de reconstituer les
déplacements et le rythme de travail d'une personne.

## 2. Description du traitement

### 2.1 Finalités

| Finalité | Base légale envisagée |
|:---|:---|
| Gestion et suivi des ruchers et des visites | Exécution du contrat / intérêt légitime de l'exploitant |
| Planification et affectation des agents | Exécution du contrat de travail |
| Tableaux de bord de production et de ROI | Intérêt légitime de l'exploitant |
| Alertes sanitaires | Intérêt légitime, voire obligation réglementaire (déclaration sanitaire) |
| Traçabilité des lots de récolte | Obligation légale (traçabilité alimentaire) |

### 2.2 Catégories de données

| Catégorie | Exemples | Sensibilité |
|:---|:---|:---|
| Identité des agents | nom, rôle, identifiant | Standard |
| **Géolocalisation des ruchers** | latitude, longitude, altitude | 🔴 **Élevée** |
| **Activité des agents** | date, heure, durée des visites, ruches visitées | 🔴 **Élevée** (suivi d'activité) |
| Photographies d'inspection | images de cadres, de ruches | Moyenne (métadonnées EXIF ⚠️) |
| Données d'exploitation | production, ROI, interventions | Économique, non personnelle |
| Mesures de capteurs | poids, température, humidité | Non personnelle |

> ⚠️ **Métadonnées EXIF.** Les photographies prises sur le terrain (US-010,
> US-028) embarquent par défaut les coordonnées GPS et l'horodatage. Elles
> doivent être **purgées à l'ingestion**, faute de quoi un export de photos
> devient un export de positions de ruchers.

### 2.3 Personnes concernées

Agents apiculteurs, superviseurs, responsables, administrateurs. À l'échelle
envisagée dans l'ADR-002 : quelques centaines de personnes.

### 2.4 Destinataires

Utilisateurs authentifiés selon la matrice RBAC (US-022), l'hébergeur, et le
prestataire de maintenance dans le cadre d'un contrat de sous-traitance
(article 28 RGPD — **un DPA doit être signé**).

## 3. Analyse des risques

| # | Risque | Gravité | Vraisemblance | Mesures prévues |
|:---|:---|:---|:---|:---|
| R1 | **Fuite des coordonnées de ruchers** → vol de ruches, préjudice économique direct | 🔴 Élevée | Moyenne | RBAC serveur, chiffrement au repos, exclusion des exports par défaut, absence dans les logs, pentest externe |
| R2 | **Surveillance des agents** via l'historique de visites horodatées | 🔴 Élevée | Moyenne | Finalité limitée à la planification, pas d'usage disciplinaire, information des agents, rétention limitée, agrégation par défaut dans les tableaux de bord |
| R3 | **Fuite inter-exploitations** (multi-tenant, cf. ADR-001) | 🔴 Élevée | Faible | Row Level Security PostgreSQL, tests d'intégration dédiés aux tentatives de fuite |
| R4 | Réidentification via les métadonnées EXIF des photos | Moyenne | Élevée | Purge EXIF à l'ingestion |
| R5 | Conservation illimitée | Moyenne | Élevée | Politique de rétention, purge automatisée (TimescaleDB) |
| R6 | Compromission des accès | Élevée | Faible | Keycloak, OIDC, MFA recommandé pour les rôles d'administration |
| R7 | Perte de données | Moyenne | Faible | Sauvegardes chiffrées, restauration testée mensuellement (annexe H) |

## 4. Mesures — statut

| Mesure | Statut | Référence |
|:---|:---|:---|
| Chiffrement en transit (TLS 1.3, X.509) | ✅ Spécifié | Ch. 08, annexe G |
| Chiffrement au repos des coordonnées GPS | 🟠 **À spécifier** | — |
| RBAC appliqué côté serveur | ✅ Spécifié | US-022 |
| Exclusion du GPS des exports par défaut | 🟠 **À ajouter à US-027** | — |
| Exclusion du GPS des journaux | 🟠 **À ajouter aux règles de log** | — |
| Purge des métadonnées EXIF | 🟠 **À ajouter à US-010 / US-028** | — |
| Politique de rétention et purge | 🟠 **À définir** | ADR-002 |
| Isolation multi-tenant (RLS) | 🟠 Conditionné à l'ADR-001 | ADR-001 |
| Registre des traitements | ❌ **À créer** | — |
| DPA avec les sous-traitants | ❌ **À signer** | — |
| Information des personnes concernées | ❌ **À rédiger** | — |
| Procédure d'exercice des droits | ❌ **À définir** | — |
| Procédure de notification de violation (72 h) | ❌ **À définir** | — |
| Pentest externe | 🟠 Budgété, à planifier | Critères de recette |

## 5. Actions à mener pendant le Sprint 0

1. **Désigner un référent** protection des données côté client.
2. Créer le **registre des traitements**.
3. Faire **valider cette AIPD** par un juriste ou un DPO.
4. Trancher l'**ADR-001** — l'isolation multi-tenant est une mesure de sécurité
   au sens du RGPD, pas seulement un choix d'architecture.
5. Définir les **durées de conservation** par catégorie de données.
6. Rédiger l'**information des agents** sur le traitement de leur activité.

> 💡 **Le délai juridique est souvent plus long que le délai technique.** C'est
> pourquoi ces actions démarrent au Sprint 0 et non en fin de projet : une AIPD
> validée après la mise en production ne protège de rien.

## 6. Points à arbitrer avec le juriste

- L'intérêt légitime suffit-il pour le suivi d'activité des agents, ou faut-il
  une autre base légale ? Une information et une consultation des représentants
  du personnel sont-elles requises ?
- Faut-il consulter l'autorité de contrôle (article 36) au vu du risque
  résiduel sur la géolocalisation ?
- Quelle durée de conservation retenir pour l'historique des visites, au regard
  des obligations de traçabilité sanitaire et alimentaire ?
- Un transfert hors UE est-il envisagé (hébergement, sous-traitants) ?
