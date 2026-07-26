/// <reference types="vite-plugin-pwa/client" />
/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Émetteur OIDC Keycloak, ex. https://auth.zumm/realms/zumm (US-020). */
  readonly VITE_OIDC_ISSUER?: string;
  /** Identifiant du client public PWA. */
  readonly VITE_OIDC_CLIENT?: string;
  /** URI de redirection après connexion. */
  readonly VITE_OIDC_REDIRECT?: string;
  /**
   * Gabarit d'URL des tuiles cartographiques (SPRINT-13). Par défaut les tuiles
   * publiques d'OpenStreetMap, dont la politique d'usage exclut la production :
   * en exploitation, pointer un fournisseur souscrit ou un serveur interne.
   */
  readonly VITE_TUILES_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
