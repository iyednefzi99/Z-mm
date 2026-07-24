/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Émetteur OIDC Keycloak, ex. https://auth.zumm/realms/zumm (US-020). */
  readonly VITE_OIDC_ISSUER?: string;
  /** Identifiant du client public PWA. */
  readonly VITE_OIDC_CLIENT?: string;
  /** URI de redirection après connexion. */
  readonly VITE_OIDC_REDIRECT?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
