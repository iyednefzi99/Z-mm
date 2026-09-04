# Image back-end « JAR seul » — chemin de repli quand le build conteneurise
# n'aboutit pas.
#
# `infra/backend.Dockerfile` compile dans `maven:3.9-eclipse-temurin-17`, soit
# ~146 Mo a tirer avant la premiere ligne de code. Sur un lien degrade, ce pull
# echoue (« unexpected EOF ») et le back-end devient irreconstructible alors que
# le poste sait parfaitement compiler : `~/.m2` est garni et `./mvnw -o` passe
# hors ligne.
#
# On repart donc de l'image DEJA construite, qui porte le runtime
# `eclipse-temurin:17-jre-jammy`, l'utilisateur `zumm`, le point d'entree et la
# sonde — et on ne remplace que le JAR.
#
# Usage (contexte = backend/, donc `target/` est visible) :
#   cd backend && ./mvnw -o -B package -DskipTests
#   docker build -f ../infra/backend-jar.Dockerfile -t zumm-backend:dev .
#
# LIMITES, a lire avant de s'y habituer :
#   - ce n'est PAS le build de reference : il court-circuite l'etape
#     reproductible que rejoue la CI, et depend du JDK de l'hote ;
#   - il exige une image `infra-backend:latest` deja presente — un depot
#     fraichement clone n'a rien a quoi se raccrocher ;
#   - le bytecode reste en 17 grace a `maven.compiler.release`, quel que soit le
#     JDK local, mais rien ne le verifie ici.
# Revenir a `docker compose build backend` des que le reseau le permet.
FROM infra-backend:latest
COPY --chown=zumm:zumm target/zumm.jar /app/zumm.jar
