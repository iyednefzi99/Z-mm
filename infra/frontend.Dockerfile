# ═══════════════════════════════════════════════════════════
# Image de la PWA Zümm (SPRINT-13).
#
# Deux etages : Node compile, nginx sert. L'image finale ne contient ni Node, ni
# les sources, ni node_modules — seulement les fichiers statiques produits par
# Vite. Surface d'attaque et poids reduits d'autant.
#
# ⚠️ Vite fige les variables `VITE_*` AU MOMENT DU BUILD : elles sont inlinees
# dans le bundle. Elles arrivent donc en ARG (pas en variable d'environnement du
# conteneur, qui n'aurait aucun effet). Aucune d'elles n'est un secret : un client
# public OIDC n'en detient pas.
# ═══════════════════════════════════════════════════════════

FROM node:20-alpine AS construction
WORKDIR /construction

# Couche de dependances separee : le cache reste valable tant que le verrou ne
# bouge pas, meme quand le code change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_OIDC_ISSUER=https://localhost/realms/zumm
ARG VITE_OIDC_CLIENT=zumm-frontend
ARG VITE_OIDC_REDIRECT=https://localhost/
ENV VITE_OIDC_ISSUER=$VITE_OIDC_ISSUER \
    VITE_OIDC_CLIENT=$VITE_OIDC_CLIENT \
    VITE_OIDC_REDIRECT=$VITE_OIDC_REDIRECT

RUN npm run build

FROM nginx:alpine AS execution

# Configuration de service : repli SPA et politique de cache (cf. le fichier).
COPY --from=construction /construction/dist /usr/share/nginx/html
COPY nginx-pwa.conf /etc/nginx/conf.d/default.conf

# nginx:alpine tourne deja en `nginx` pour ses workers ; on retire seulement les
# pages par defaut pour ne rien servir d'autre que la PWA.
RUN rm -f /usr/share/nginx/html/50x.html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://localhost/index.html || exit 1
