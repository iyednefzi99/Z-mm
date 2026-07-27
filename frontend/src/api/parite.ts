/**
 * Parité entre les types écrits à la main et le contrat OpenAPI du serveur.
 *
 * <p>La dette que ce fichier solde : `api/types.ts` était écrit à la main alors
 * que le serveur publie un contrat. Rien ne garantissait qu'ils décrivent la même
 * chose — « la parité des types n'est garantie par rien d'autre que l'attention »,
 * comme le disait `client.ts` lui-même en en-tête. Une propriété renommée côté
 * serveur ne se voyait qu'à l'exécution, sous la forme d'un champ vide.
 *
 * <p><strong>Pourquoi vérifier plutôt que générer.</strong> Remplacer purement le
 * client écrit à la main par un client généré aurait touché quarante fonctions et
 * seize vues, pour un gain limité au seul typage : les fonctions elles-mêmes sont
 * lisibles et stables. Ce fichier obtient la garantie recherchée — **aucune dérive
 * silencieuse** — sans réécrire ce qui fonctionne. Une divergence casse `tsc`,
 * donc la chaîne, avec le nom du champ fautif.
 *
 * <p>Ce fichier n'est jamais importé à l'exécution : il ne contient que des types,
 * et disparaît à la compilation.
 *
 * <p>Régénérer le contrat après toute évolution de l'API :
 * <pre>
 *   cd backend && ./mvnw -B verify -Dit.test=ContratOpenApiIT   # écrit openapi.json
 *   cd frontend && npm run api:contrat                          # écrit contrat.ts
 * </pre>
 */

import type { components } from './contrat';
import type {
  Agent,
  AlerteMesure,
  AlerteSanitaire,
  Anomalie,
  AuditEntree,
  CalendrierCellule,
  Compartiment,
  EtapeTournee,
  Ferme,
  Fermier,
  GrappeSites,
  LigneProduction,
  Lot,
  MentionOrigine,
  MesureReponse,
  Meteo,
  PartLot,
  Photo,
  Planning,
  PointJournalier,
  PrevisionRecolte,
  QuantiteMiel,
  Recolte,
  Reine,
  Ruche,
  Seuils,
  Site,
  Synthese,
  Tache,
  Tournee,
  Trace,
  Visite,
  VisiteBreve,
  VoisinSite,
} from './types';

type Schemas = components['schemas'];

/**
 * Échoue à la compilation si `Ecrit` n'est pas compatible avec `Publie`.
 *
 * <p>Le sens de la vérification est délibéré : on exige que le type écrit à la
 * main soit **assignable** au type publié. Une propriété manquante côté main
 * passe donc — le client a le droit de ne pas tout consommer — mais une propriété
 * de TYPE différent, elle, est rejetée. C'est exactement la dérive qu'on veut
 * attraper : le champ qui existe des deux côtés et ne dit plus la même chose.
 */
type Conforme<Ecrit extends Publie, Publie> = Ecrit;

/**
 * Rend chaque propriété du contrat tolérante au `null`.
 *
 * <p>Ce décalage est réel et mérite d'être expliqué plutôt que contourné en
 * silence. Jackson sérialise un champ Java nul en `"champ": null` — la clé est
 * **présente**, sa valeur est nulle. springdoc, lui, décrit ce champ comme
 * simplement *non requis*, ce qu'openapi-typescript rend par `champ?: T`, donc
 * `T | undefined`.
 *
 * <p>Les types écrits à la main disent `T | null`, ce qui décrit **mieux** la
 * réalité du fil que le contrat généré. On normalise donc du côté publié plutôt
 * que de dégrader le côté écrit : la vérification porte alors sur ce qui compte —
 * le NOM et le TYPE de base de chaque champ — sans buter sur une convention de
 * description.
 *
 * <p>Corriger la source demanderait d'annoter chaque champ nullable côté Java
 * (`@Schema(nullable = true)`), soit plusieurs centaines d'annotations pour un
 * gain nul à l'exécution. C'est un arbitrage, pas un oubli.
 */
type TolerantAuNull<T> = T extends (infer Element)[]
  ? TolerantAuNull<Element>[]
  : T extends object
    ? { [K in keyof T]: TolerantAuNull<T[K]> | null }
    : T;

export type _Fermier = Conforme<Fermier, TolerantAuNull<Schemas['FermierReponse']>>;
export type _Ferme = Conforme<Ferme, TolerantAuNull<Schemas['FermeReponse']>>;
export type _Site = Conforme<Site, TolerantAuNull<Schemas['SiteReponse']>>;
export type _Agent = Conforme<Agent, TolerantAuNull<Schemas['AgentReponse']>>;
export type _Ruche = Conforme<Ruche, TolerantAuNull<Schemas['RucheReponse']>>;
export type _Visite = Conforme<Visite, TolerantAuNull<Schemas['VisiteReponse']>>;
export type _Planning = Conforme<Planning, TolerantAuNull<Schemas['PlanningReponse']>>;
export type _Tache = Conforme<Tache, TolerantAuNull<Schemas['TacheReponse']>>;
export type _Recolte = Conforme<Recolte, TolerantAuNull<Schemas['RecolteReponse']>>;
export type _Lot = Conforme<Lot, TolerantAuNull<Schemas['LotReponse']>>;
export type _Mesure = Conforme<MesureReponse, TolerantAuNull<Schemas['MesureReponse']>>;

/*
 * Couverture etendue.
 *
 * <p>La verification ne portait que sur onze types — les entites CRUD — alors que
 * `types.ts` en exporte une soixantaine. Tout ce qui n'etait pas couvert pouvait
 * deriver en silence : precisement les reponses analytiques (synthese, previsions,
 * carte, anomalies), c'est-a-dire celles qu'aucun formulaire ne fait echouer
 * visiblement quand un champ arrive vide.
 */
export type _Photo = Conforme<Photo, TolerantAuNull<Schemas['PhotoReponse']>>;
export type _Compartiment = Conforme<Compartiment, TolerantAuNull<Schemas['CompartimentReponse']>>;
export type _Reine = Conforme<Reine, TolerantAuNull<Schemas['ReineReponse']>>;
export type _AuditEntree = Conforme<AuditEntree, TolerantAuNull<Schemas['AuditEntreeReponse']>>;
export type _AlerteMesure = Conforme<AlerteMesure, TolerantAuNull<Schemas['AlerteReponse']>>;
export type _AlerteSanitaire = Conforme<AlerteSanitaire, TolerantAuNull<Schemas['AlerteSanitaire']>>;
export type _Synthese = Conforme<Synthese, TolerantAuNull<Schemas['SyntheseReponse']>>;
export type _LigneProduction = Conforme<LigneProduction, TolerantAuNull<Schemas['LigneProduction']>>;
export type _PrevisionRecolte = Conforme<PrevisionRecolte, TolerantAuNull<Schemas['PrevisionRecolte']>>;
export type _CalendrierCellule = Conforme<CalendrierCellule, TolerantAuNull<Schemas['CalendrierCellule']>>;
export type _VisiteBreve = Conforme<VisiteBreve, TolerantAuNull<Schemas['VisiteBreve']>>;
export type _PointJournalier = Conforme<PointJournalier, TolerantAuNull<Schemas['PointJournalier']>>;
export type _Meteo = Conforme<Meteo, TolerantAuNull<Schemas['MeteoReponse']>>;
export type _Anomalie = Conforme<Anomalie, TolerantAuNull<Schemas['AnomalieReponse']>>;
export type _Trace = Conforme<Trace, TolerantAuNull<Schemas['TraceReponse']>>;
export type _GrappeSites = Conforme<GrappeSites, TolerantAuNull<Schemas['GrappeSites']>>;
export type _VoisinSite = Conforme<VoisinSite, TolerantAuNull<Schemas['VoisinSite']>>;
export type _EtapeTournee = Conforme<EtapeTournee, TolerantAuNull<Schemas['EtapeTournee']>>;
export type _Tournee = Conforme<Tournee, TolerantAuNull<Schemas['TourneeReponse']>>;
export type _QuantiteMiel = Conforme<QuantiteMiel, TolerantAuNull<Schemas['QuantiteMiel']>>;
export type _MentionOrigine = Conforme<MentionOrigine, TolerantAuNull<Schemas['MentionOrigine']>>;
export type _PartLot = Conforme<PartLot, TolerantAuNull<Schemas['PartReponse']>>;
export type _Seuils = Conforme<Seuils, TolerantAuNull<Schemas['SeuilsMetier']>>;
