import type { ReactElement } from 'react';
import { useT } from '../i18n/langue';
import { PageLegale } from './PageLegale';

/**
 * Page « Éditions » (SPRINT-19).
 *
 * <p><strong>Pourquoi elle ne compare rien.</strong> L'arbitrage du 18/08/2026
 * retenait une page descriptive « sans afficher un seul montant ». Reste que des
 * paliers, eux non plus, ne s'inventent pas : le dépôt ne contient ni
 * facturation, ni abonnement, ni fonction verrouillée — aucune trace de
 * découpage commercial dans le back. Fabriquer trois colonnes « Essentiel /
 * Pro / Entreprise » aurait été inventer une stratégie produit, pas décrire
 * un logiciel.
 *
 * <p>La page dit donc ce qui est vrai : une seule édition, tout inclus, et le
 * modèle économique au bandeau des manques. Elle redeviendra une vraie
 * comparaison le jour où il y aura quelque chose à comparer.
 */
export function EditionsVue(): ReactElement {
  const t = useT();

  return (
    <PageLegale
      titre={t.editions.titre}
      chapo={t.editions.chapo}
      maj={t.editions.maj}
      manques={t.editions.manques}
      sections={t.editions.sections}
    />
  );
}
