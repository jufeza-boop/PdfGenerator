$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -Path $RootDir

Write-Host "==> Directorio de trabajo: $PWD"
Write-Host "==> Verificando dependencias y compilando el proyecto..."
# Ejecuta assembleDebug para verificar que el código compila
.\gradlew.bat :composeApp:assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "==> Error en la verificación base. Por favor, corrige esto antes de comenzar trabajo nuevo."
    exit $LASTEXITCODE
}

Write-Host "==> Verificación base completada con éxito."
Write-Host "==> Comandos útiles:"
Write-Host "    Para ejecutar en Windows: .\gradlew.bat :composeApp:run"
Write-Host "    Para empaquetar MSI:      .\gradlew.bat :composeApp:packageMsi"
