# ═══════════════════════════════════════════════════════════
# Dockerfile Zümm - Application J2EE (WildFly)
# ═══════════════════════════════════════════════════════════
FROM quay.io/wildfly/wildfly:30.0.0.Final-jdk17

LABEL maintainer="equipe-zumm@example.com"
LABEL version="1.0"
LABEL description="Zümm - Système de gestion apicole"

# Variables d'environnement
ENV WILDFLY_HOME=/opt/jboss/wildfly
ENV DEPLOYMENT_DIR=$WILDFLY_HOME/standalone/deployments
ENV CONFIG_DIR=$WILDFLY_HOME/standalone/configuration

# Créer un utilisateur non-root pour la sécurité
RUN $WILDFLY_HOME/bin/add-user.sh -u admin -p admin123! --silent

# Copier le WAR
COPY target/zumm.war $DEPLOYMENT_DIR/

# Copier les certificats SSL (X.509)
COPY docker/certs/ $CONFIG_DIR/

# Copier la configuration standalone.xml personnalisée
COPY docker/standalone-zumm.xml $CONFIG_DIR/standalone.xml

# Configurer les datasources PostgreSQL
COPY docker/zumm-ds.xml $DEPLOYMENT_DIR/

# Exposer les ports
EXPOSE 8080 8443 9990

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/zumm/api/health || exit 1

# Commande de démarrage
CMD ["sh", "-c", "$WILDFLY_HOME/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0"]
