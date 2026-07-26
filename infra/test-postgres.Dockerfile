# ═══════════════════════════════════════════════════════════
# Image PostgreSQL de TEST : PostGIS + TimescaleDB
#
# Pourquoi cette image existe :
# L'image de reference de la roadmap, `timescale/timescaledb-ha`, embarque les
# deux extensions, mais le depot `timescale/*` est injoignable depuis certains
# reseaux : le telechargement des couches ne demarre jamais (constate le
# 2026-07-19, hors quota Docker Hub, alors que `postgis/postgis` passe).
#
# On repart donc de l'image PostGIS officielle, qui se telecharge de facon
# fiable, et on y ajoute TimescaleDB depuis le depot APT de l'editeur.
#
# Portee : tests d'integration UNIQUEMENT. La cible d'execution reste
# `timescale/timescaledb-ha` (cf. infra/docker-compose.yml et
# roadmap/operationnel/03_devops_pipeline/docker-compose.yml).
# ═══════════════════════════════════════════════════════════

# Derogation « conteneur en root », assumee et bornee :
#   * l'entrypoint officiel de PostgreSQL DOIT demarrer en root — il execute
#     `initdb`, ajuste les droits du repertoire de donnees, puis redescend
#     lui-meme vers l'utilisateur `postgres` via `gosu`. Forcer `USER postgres`
#     ici empeche l'initialisation du cluster : l'image ne demarre plus ;
#   * cette image sert EXCLUSIVEMENT aux tests d'integration, sur un conteneur
#     ephemere, sans donnee reelle et sans port publie. La cible d'execution
#     reste `timescale/timescaledb-ha` (cf. l'en-tete de ce fichier).
#
# Les images qui, elles, servent en execution sont non privilegiees :
# `backend.Dockerfile` declare `USER zumm`, `frontend.Dockerfile` part de
# `nginxinc/nginx-unprivileged` et redeclare `USER nginx`.
#
# L'exemption est portee par `.trivyignore.yaml`, qui la borne a cette regle et
# a ce seul fichier.
FROM postgis/postgis:16-3.4

ENV DEBIAN_FRONTEND=noninteractive

# Le paquet TimescaleDB (~65 Mo) est servi par CloudFront via une URL signee a
# duree limitee : sur une liaison lente, la signature expire avant la fin du
# telechargement. Les tentatives multiples reobtiennent une URL fraiche, et le
# cache APT evite de tout retelecharger a chaque essai.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    set -eux; \
    printf 'Acquire::Retries "10";\nAcquire::http::Timeout "180";\nAcquire::https::Timeout "180";\nAcquire::http::Pipeline-Depth "0";\n' \
        > /etc/apt/apt.conf.d/99-reseau-lent; \
    rm -f /etc/apt/apt.conf.d/docker-clean; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl gnupg lsb-release; \
    curl -fsSL --retry 10 --retry-all-errors --retry-delay 5 \
        https://packagecloud.io/timescale/timescaledb/gpgkey \
        | gpg --dearmor -o /etc/apt/trusted.gpg.d/timescaledb.gpg; \
    echo "deb https://packagecloud.io/timescale/timescaledb/debian/ $(lsb_release -c -s) main" \
        > /etc/apt/sources.list.d/timescaledb.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends timescaledb-2-postgresql-16

# TimescaleDB exige d'etre precharge ; PostGIS n'en a pas besoin.
# Le conteneur de test surcharge la commande pour l'activer, mais on fixe
# aussi la valeur par defaut ici pour que l'image soit utilisable seule.
ENV POSTGRES_INITDB_ARGS=""
CMD ["postgres", "-c", "shared_preload_libraries=timescaledb"]
