@echo off
rem ============================================================
rem  Zumm - lanceur double-cliquable.
rem  Delegue a scripts\demarrer.ps1 (pile Docker + navigateur).
rem  Arguments passes tels quels : -Rapide, -Arreter, -Donnees...
rem ============================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demarrer.ps1" %*
if errorlevel 1 (
  echo.
  echo Le demarrage a echoue. Lis les messages ci-dessus.
  pause
)
endlocal
