# Microservice IA Zümm (US-035) — image minimale, bibliothèque standard uniquement.
FROM python:3.12-slim

WORKDIR /app
COPY scoring.py app.py ./

# Utilisateur non-root (durcissement, cf. annexe sécurité).
RUN useradd --create-home --uid 10001 zumm
USER zumm

EXPOSE 8000
ENV PORT=8000

# Sonde de vie alignée sur l'endpoint /health.
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health').status==200 else 1)"

CMD ["python", "app.py"]
