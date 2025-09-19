# Script de nettoyage sécurisé - Suppression des keystores en double
# ATTENTION: Ce script va supprimer les keystores en double

Write-Host "=== NETTOYAGE SÉCURISÉ KEYSTORES ===" -ForegroundColor Red
Write-Host ""

$WORKING_KEYSTORE = "app\potomitan-keystore.jks"
$KEYSTORES_TO_REMOVE = @(
    "app-release.jks",
    "my-release-key.jks", 
    "app\app-release.jks",
    "app\keystore\app-release.jks",
    "app\keystore\my-release-key.jks"
)

Write-Host "Keystore de production à conserver:" -ForegroundColor Green
Write-Host "  ✓ $WORKING_KEYSTORE" -ForegroundColor Green
Write-Host ""

Write-Host "Keystores à supprimer (doublons/obsolètes):" -ForegroundColor Yellow
foreach ($keystore in $KEYSTORES_TO_REMOVE) {
    if (Test-Path $keystore) {
        Write-Host "  ❌ $keystore" -ForegroundColor Red
    } else {
        Write-Host "  ⚪ $keystore (inexistant)" -ForegroundColor Gray
    }
}
Write-Host ""

$response = Read-Host "Confirmer la suppression des keystores obsolètes? (y/N)"
if ($response -ne "y" -and $response -ne "Y") {
    Write-Host "❌ Opération annulée" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🧹 Suppression en cours..." -ForegroundColor Cyan

$removed = 0
foreach ($keystore in $KEYSTORES_TO_REMOVE) {
    if (Test-Path $keystore) {
        try {
            Remove-Item $keystore -Force
            Write-Host "  ✅ Supprimé: $keystore" -ForegroundColor Green
            $removed++
        } catch {
            Write-Host "  ❌ Erreur: $keystore - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# Supprimer le dossier keystore s'il est vide
if (Test-Path "app\keystore") {
    $keystoreFiles = Get-ChildItem "app\keystore" -Force
    if ($keystoreFiles.Count -eq 0) {
        Remove-Item "app\keystore" -Force
        Write-Host "  ✅ Dossier keystore vide supprimé" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "📊 RÉSULTAT:" -ForegroundColor Cyan
Write-Host "  Keystores supprimés: $removed" -ForegroundColor Cyan
Write-Host "  Keystore production: $(if (Test-Path $WORKING_KEYSTORE) {'✅ OK'} else {'❌ MANQUANT'})" -ForegroundColor $(if (Test-Path $WORKING_KEYSTORE) {'Green'} else {'Red'})

Write-Host ""
Write-Host "✅ NETTOYAGE TERMINÉ" -ForegroundColor Green
Write-Host "⚠️  Vérifiez que le build fonctionne toujours avec: gradlew assembleRelease" -ForegroundColor Yellow