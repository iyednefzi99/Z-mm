# ═══════════════════════════════════════════════════════════
# Dockerfile Zümm - Application Spring Boot
# Build multi-étapes : compilation Maven puis image d'exécution JRE
# ═══════════════════════════════════════════════════════════

# ─── Étape 1 : build ───
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Cache des dépendances : copie du POM avant le code source
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ─── Étape 2 : exécution ───
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="equipe-zumm@example.com"
LABEL version="1.0"
LABEL description="Zümm - Système de gestion apicole"

# Utilisateur non-root dédié (aucun secret embarqué dans l'image)
RUN groupadd --system zumm && useradd --system --gid zumm --home /app zumm
WORKDIR /app

# Copier le JAR autoporteur (Tomcat embarqué) depuis l'étape de build
COPY --from=build --chown=zumm:zumm /build/target/zumm.jar /app/zumm.jar

# Répertoire de configuration métier (ConfigZumm.ini monté en volume au run)
ENV CONFIG_DIR=/app/config
RUN mkdir -p $CONFIG_DIR && chown zumm:zumm $CONFIG_DIR

USER zumm

# Un seul port applicatif exposé ; TLS et routage sont assurés par le proxy
# inverse. Aucun port d'administration n'est publié.
EXPOSE 8080

# Health check via l'endpoint Actuator de Spring Boot
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Démarrage : le profil et les secrets viennent de l'environnement, jamais de
# l'image (cf. SPRING_PROFILES_ACTIVE, SPRING_DATASOURCE_* dans compose).
ENTRYPOINT ["java", "-jar", "/app/zumm.jar"]
