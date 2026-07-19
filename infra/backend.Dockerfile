# ═══════════════════════════════════════════════════════════
# Image de l'application Zümm (Spring Boot)
# Build multi-etapes : compilation Maven puis image d'execution JRE.
# Derive de roadmap/operationnel/03_devops_pipeline/Dockerfile.
# Contexte de build attendu : backend/
# ═══════════════════════════════════════════════════════════

# ─── Etape 1 : build ───
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache des dependances : le POM avant les sources, pour ne re-telecharger
# que lorsque les dependances changent reellement.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ─── Etape 2 : execution ───
FROM eclipse-temurin:17-jre-jammy

LABEL description="Zümm — système d'information de gestion et de suivi apicole"

# Utilisateur non-root dedie ; aucun secret n'est embarque dans l'image.
RUN groupadd --system zumm && useradd --system --gid zumm --home /app zumm
WORKDIR /app

# JAR autoporteur (Tomcat embarque) produit par l'etape de build.
COPY --from=build --chown=zumm:zumm /build/target/zumm.jar /app/zumm.jar

# Repertoire de configuration metier ; ConfigZumm.ini est monte en volume au run.
ENV CONFIG_DIR=/app/config
RUN mkdir -p $CONFIG_DIR && chown zumm:zumm $CONFIG_DIR

USER zumm

# Un seul port applicatif. TLS et routage sont assures par le proxy inverse ;
# aucun port d'administration n'est publie.
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Le profil et les secrets viennent de l'environnement, jamais de l'image.
ENTRYPOINT ["java", "-jar", "/app/zumm.jar"]
