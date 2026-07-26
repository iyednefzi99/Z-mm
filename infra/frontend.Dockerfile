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

# Image NON PRIVILEGIEE, et non `nginx:alpine`. L'image officielle demarre son
# processus maitre en root pour ouvrir le port 80, puis rabaisse ses workers ;
# celle-ci tourne entierement sous l'utilisateur `nginx` (UID 101) et ecoute sur
# un port non privilegie — ce qui suffit, puisque seul le proxy inverse l'atteint
# par le reseau interne du compose.
#
# Ce n'est pas une precaution theorique : c'est le conteneur qui sert du contenu
# a des navigateurs, donc le premier qu'un defaut de nginx exposerait. Un
# processus non root y transforme une execution de code en incident borne.
FROM nginxinc/nginx-unprivileged:alpine AS execution

# Configuration de service : repli SPA et politique de cache (cf. le fichier).
COPY --from=construction /construction/dist /usr/share/nginx/html
COPY nginx-pwa.conf /etc/nginx/conf.d/default.conf

# L'image de base pose deja cet utilisateur ; on le redeclare EXPLICITEMENT.
# Deux raisons : l'analyse statique lit le Dockerfile et ne peut pas connaitre
# l'utilisateur effectif d'une image de base, et une image de base changee un
# jour ne doit pas faire remonter le conteneur en root sans que cela se voie.
USER nginx

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/index.html || exit 1
