<#
.SYNOPSIS
    Lance la pile Zumm complete et ouvre l'application dans le navigateur.

.DESCRIPTION
    Enchaine tout ce que docs/README.md decrit a la main : demarrage du demon
    Docker, verification des prerequis (.env, ConfigZumm.ini, certificat de dev,
    image PostGIS de test), montee de la pile avec la surcouche de
    DEVELOPPEMENT (comptes de test), attente de la sante reelle du back-end,
    puis ouverture de https://localhost.

    La surcouche docker-compose.dev.yml est volontaire : sans elle, le realm
    importe est celui de production et aucun compte ne permet de se connecter.

.PARAMETER Rapide
    Ne reconstruit pas les images (demarrage le plus court, code non recompile).

.PARAMETER Donnees
    Force le chargement du jeu de demonstration. Charge automatiquement au tout
    premier demarrage (volume PostgreSQL vierge). Attention : le script est
    idempotent par PURGE du tenant « exploitation-demo » — le rejouer efface les
    donnees saisies sur ce tenant.

.PARAMETER Arreter
    Arrete la pile au lieu de la demarrer (les volumes sont conserves).

.PARAMETER SansNavigateur
    N'ouvre pas le navigateur.

.PARAMETER Delai
    Secondes d'attente maximale de la sante du back-end (defaut 300).

.EXAMPLE
    .\scripts\demarrer.ps1
    .\scripts\demarrer.ps1 -Rapide
    .\scripts\demarrer.ps1 -Arreter
#>
[CmdletBinding()]
param(
    [switch]$Rapide,
    [switch]$Donnees,
    [switch]$Arreter,
    [switch]$SansNavigateur,
    [int]$Delai = 300
)

$ErrorActionPreference = 'Stop'
$racine = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $racine

function Etape($m) { Write-Host "→ $m" -ForegroundColor Cyan }
function Bien($m)  { Write-Host "✓ $m" -ForegroundColor Green }
function Alerte($m){ Write-Host "! $m" -ForegroundColor Yellow }
function Fatal($m) { Write-Host "✗ $m" -ForegroundColor Red; exit 1 }

# Arguments communs a tous les appels compose. Le .env est a la RACINE, d'ou le
# --env-file : sans lui, les ${MOT_DE_PASSE:?...} font echouer l'interpolation.
$compose = @(
    'compose', '--env-file', '.env',
    '-f', 'infra/docker-compose.yml',
    '-f', 'infra/docker-compose.dev.yml'
)

# Fonction simple (sans bloc param) : les jetons non reconnus, « -d » compris,
# arrivent tels quels dans $args au lieu d'etre pris pour des parametres.
function Compose { & docker @compose @args }

# ─── 1. Demon Docker ────────────────────────────────────────────────────────
Etape 'Verification du demon Docker…'
& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    $bureau = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "${env:ProgramFiles(x86)}\Docker\Docker\Docker Desktop.exe"
    ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

    if (-not $bureau) { Fatal 'Docker ne repond pas et Docker Desktop est introuvable. Demarre-le a la main.' }

    Alerte 'Docker ne repond pas — demarrage de Docker Desktop (jusqu''a 3 min)…'
    Start-Process -FilePath $bureau | Out-Null

    $fin = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 5
        & docker info *> $null
    } while ($LASTEXITCODE -ne 0 -and (Get-Date) -lt $fin)

    if ($LASTEXITCODE -ne 0) { Fatal 'Docker n''a pas demarre dans le delai imparti.' }
}
Bien 'Docker est operationnel.'

# ─── 2. Arret demande ───────────────────────────────────────────────────────
if ($Arreter) {
    Etape 'Arret de la pile Zumm…'
    Compose down
    if ($LASTEXITCODE -ne 0) { Fatal 'L''arret a echoue.' }
    Bien 'Pile arretee (les volumes sont conserves).'
    exit 0
}

# ─── 3. Prerequis de fichiers ───────────────────────────────────────────────
Etape 'Verification des prerequis…'

if (-not (Test-Path -LiteralPath '.env')) {
    Fatal 'Fichier .env absent a la racine. Copie .env.example et renseigne les mots de passe.'
}

if (-not (Test-Path -LiteralPath 'config/ConfigZumm.ini')) {
    Copy-Item 'config/ConfigZumm.example.ini' 'config/ConfigZumm.ini'
    Bien 'config/ConfigZumm.ini cree depuis le gabarit.'
}

# Certificat auto-signe du proxy inverse : sans lui, nginx ne demarre pas.
# Meme sujet et memes SAN que infra/generer-certificat-dev.sh, mais produit par
# un conteneur : ni Git Bash ni OpenSSL ne sont exiges sur l'hote.
if (-not (Test-Path -LiteralPath 'infra/nginx/ssl/zumm.crt') -or
    -not (Test-Path -LiteralPath 'infra/nginx/ssl/zumm.key')) {
    Etape 'Generation du certificat de developpement…'
    New-Item -ItemType Directory -Force -Path 'infra/nginx/ssl' | Out-Null
    $ssl = (Resolve-Path 'infra/nginx/ssl').Path
    & docker run --rm -v "${ssl}:/ssl" alpine/openssl req -x509 -nodes -newkey rsa:2048 -days 365 `
        -keyout /ssl/zumm.key -out /ssl/zumm.crt `
        -subj '/C=TN/O=Zumm/CN=localhost' `
        -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
    if ($LASTEXITCODE -ne 0) { Fatal 'Generation du certificat echouee.' }
}

# Image PostgreSQL + PostGIS + TimescaleDB construite localement (cf. docs).
$imagePg = & docker images --format '{{.Repository}}:{{.Tag}}' | Where-Object { $_ -eq 'zumm/test-postgres:16' }
if (-not $imagePg) {
    Etape 'Construction de l''image zumm/test-postgres:16 (premiere fois, quelques minutes)…'
    & docker build -f 'infra/test-postgres.Dockerfile' -t 'zumm/test-postgres:16' 'infra/'
    if ($LASTEXITCODE -ne 0) { Fatal 'Construction de l''image PostgreSQL echouee.' }
}
Bien 'Prerequis satisfaits.'

# Volume vierge = tout premier demarrage : on chargera la demonstration.
$volume = & docker volume ls --format '{{.Name}}' | Where-Object { $_ -match 'postgres_data$' }
$premiereFois = -not $volume

# ─── 4. Montee de la pile ───────────────────────────────────────────────────
Etape 'Demarrage de la pile (PostgreSQL, Keycloak, API, PWA, Nginx, Grafana)…'
if ($Rapide) { Compose up -d } else { Compose up -d --build }
if ($LASTEXITCODE -ne 0) { Fatal 'docker compose up a echoue (voir la sortie ci-dessus).' }

# ─── 5. Attente de la sante reelle ──────────────────────────────────────────
# Un conteneur « Up » ne prouve rien : Flyway migre, Keycloak importe le realm.
# On interroge donc /actuator/health a travers le proxy TLS.
$curl = Join-Path $env:SystemRoot 'System32\curl.exe'
Etape "Attente de la disponibilite de l'application (jusqu'a $Delai s)…"

$fin = (Get-Date).AddSeconds($Delai)
$pret = $false
while (-not $pret -and (Get-Date) -lt $fin) {
    if (Test-Path -LiteralPath $curl) {
        $reponse = & $curl -sk --max-time 5 'https://localhost/actuator/health' 2>$null
    } else {
        # Repli sans curl. Le proxy n'accepte que TLS 1.3 : on force le protocole
        # systeme et on tolere le certificat auto-signe.
        try {
            [Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
            $reponse = (Invoke-WebRequest -Uri 'https://localhost/actuator/health' -UseBasicParsing -TimeoutSec 5).Content
        } catch { $reponse = '' }
    }

    if ($reponse -match '"status"\s*:\s*"UP"') { $pret = $true; break }
    Write-Host '.' -NoNewline
    Start-Sleep -Seconds 3
}
Write-Host ''

if (-not $pret) {
    Alerte 'L''application n''a pas repondu UP dans le delai imparti. Journaux du back-end :'
    Compose logs --tail 40 backend
    Fatal 'Demarrage incomplet.'
}
Bien 'Application en bonne sante.'

# ─── 6. Donnees de demonstration ────────────────────────────────────────────
if ($Donnees -or $premiereFois) {
    Etape 'Chargement du jeu de demonstration (tenant « exploitation-demo »)…'
    # Copie puis « psql -f » plutot qu'un tube : PowerShell reencoderait
    # l'UTF-8 du script SQL vers la page de codes de la console.
    Compose cp infra/seed-demo.sql postgres:/tmp/seed-demo.sql
    if ($LASTEXITCODE -eq 0) {
        Compose exec -T postgres psql -U zumm -d zumm -v ON_ERROR_STOP=1 -f /tmp/seed-demo.sql
    }
    if ($LASTEXITCODE -ne 0) { Alerte 'Chargement des donnees de demonstration echoue (la pile reste utilisable).' }
    else { Bien 'Donnees de demonstration chargees.' }
}

# ─── 7. Ouverture ───────────────────────────────────────────────────────────
Write-Host ''
Write-Host '  Zumm est en ligne' -ForegroundColor Yellow
Write-Host '  ─────────────────'
Write-Host '  Application  https://localhost      (certificat auto-signe : avertissement attendu)'
Write-Host '  Keycloak     http://localhost:8081  (admin / cf. KC_ADMIN_PASSWORD du .env)'
Write-Host '  Grafana      http://localhost:3000  (admin / cf. GRAFANA_PASSWORD du .env)'
Write-Host ''
Write-Host '  Comptes de test (mot de passe : test)'
Write-Host '    admin-test · responsable-test · superviseur-test · apiculteur-test'
Write-Host ''
Write-Host '  Arret : .\scripts\demarrer.ps1 -Arreter'
Write-Host ''

if (-not $SansNavigateur) { Start-Process 'https://localhost' }
