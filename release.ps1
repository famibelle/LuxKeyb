# 🇬🇵 Script de Release Potomitan Kreyol Keyboard
# Usage: .\release.ps1 [version]

param(
    [string]$Version = "v1.0.0"
)

$Date = Get-Date -Format "yyyy-MM-dd"

Write-Host "🇬🇵 === POTOMITAN KREYOL KEYBOARD RELEASE SCRIPT ===" -ForegroundColor Cyan
Write-Host "📦 Version: $Version" -ForegroundColor Yellow
Write-Host "📅 Date: $Date" -ForegroundColor Yellow
Write-Host ""

# Vérifier que nous sommes dans le bon répertoire
if (!(Test-Path "android_keyboard")) {
    Write-Host "❌ Erreur: Exécuter depuis la racine du projet (où se trouve android_keyboard/)" -ForegroundColor Red
    exit 1
}

Set-Location android_keyboard

Write-Host "🧹 Nettoyage des builds précédents..." -ForegroundColor Green
.\gradlew clean

Write-Host "🏗️ Compilation Debug APK..." -ForegroundColor Green
.\gradlew assembleDebug

Write-Host "🏗️ Compilation Release APK..." -ForegroundColor Green
.\gradlew assembleRelease

Write-Host ""
Write-Host "📊 Tailles des APK générés:" -ForegroundColor Cyan
Get-ChildItem app\build\outputs\apk\debug\*.apk | Format-List Name, Length
Get-ChildItem app\build\outputs\apk\release\*.apk | Format-List Name, Length

Write-Host ""
Write-Host "✅ Builds terminés avec succès!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Prochaines étapes pour publier sur GitHub:" -ForegroundColor Cyan
Write-Host "1. Commit et push des changements: git add . && git commit -m 'Release $Version' && git push"
Write-Host "2. Créer un tag: git tag $Version && git push origin $Version"
Write-Host "3. Le workflow GitHub Actions créera automatiquement la release"
Write-Host ""
Write-Host "Ou utiliser le workflow manuel:" -ForegroundColor Yellow
Write-Host "- Aller sur GitHub → Actions → 'Build and Release'"
Write-Host "- Cliquer 'Run workflow' et spécifier la version: $Version"

Set-Location ..
