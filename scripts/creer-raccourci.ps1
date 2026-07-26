<#
.SYNOPSIS
    Cree les raccourcis Zumm sur le Bureau (demarrer / arreter).

.DESCRIPTION
    Deux raccourcis pointant sur Demarrer-Zumm.cmd, avec le favicon du projet
    comme icone : « Zumm » (demarre et ouvre le navigateur) et « Zumm - arreter ».

.PARAMETER Supprimer
    Retire les raccourcis du Bureau au lieu de les creer.

.EXAMPLE
    .\scripts\creer-raccourci.ps1
#>
[CmdletBinding()]
param([switch]$Supprimer)

$ErrorActionPreference = 'Stop'
$racine = Split-Path -Parent $PSScriptRoot
$bureau = [Environment]::GetFolderPath('Desktop')
$cible  = Join-Path $racine 'Demarrer-Zumm.cmd'
$icone  = Join-Path $racine 'assets\logo\favicon\favicon.ico'

$raccourcis = @(
    @{ Nom = 'Zumm.lnk';           Args = '';          Desc = 'Demarrer Zumm et ouvrir https://localhost' },
    @{ Nom = 'Zumm - arreter.lnk'; Args = '-Arreter';  Desc = 'Arreter la pile Zumm' }
)

if ($Supprimer) {
    foreach ($r in $raccourcis) {
        $chemin = Join-Path $bureau $r.Nom
        if (Test-Path -LiteralPath $chemin) { Remove-Item -LiteralPath $chemin }
    }
    Write-Host '✓ Raccourcis retires du Bureau.' -ForegroundColor Green
    exit 0
}

if (-not (Test-Path -LiteralPath $cible)) { throw "Introuvable : $cible" }

$shell = New-Object -ComObject WScript.Shell
foreach ($r in $raccourcis) {
    $lnk = $shell.CreateShortcut((Join-Path $bureau $r.Nom))
    $lnk.TargetPath       = $cible
    $lnk.Arguments        = $r.Args
    $lnk.WorkingDirectory = $racine
    $lnk.Description      = $r.Desc
    if (Test-Path -LiteralPath $icone) { $lnk.IconLocation = $icone }
    $lnk.Save()
    Write-Host "✓ $($r.Nom) cree sur le Bureau." -ForegroundColor Green
}
