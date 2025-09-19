# Script de generation du keystore de production
# Pour signature Google Play Store

Write-Host "GENERATION KEYSTORE DE PRODUCTION" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan

$KEYSTORE_NAME = "kreyol-keyboard-release.jks"
$KEY_ALIAS = "kreyol-keyboard"

Write-Host ""
Write-Host "ATTENTION: Ce script va generer un nouveau keystore de production" -ForegroundColor Yellow
Write-Host "Ce keystore sera necessaire pour signer l'app pour Google Play Store" -ForegroundColor Yellow
Write-Host ""

# Vérifier si keytool est disponible
try {
    $null = keytool -help 2>$null
} catch {
    Write-Host "ERREUR: keytool non trouve dans PATH" -ForegroundColor Red
    Write-Host "Installez Java JDK et ajoutez-le au PATH" -ForegroundColor Red
    exit 1
}

Write-Host "Generation du keystore: $KEYSTORE_NAME" -ForegroundColor Green
Write-Host "Alias de la cle: $KEY_ALIAS" -ForegroundColor Green
Write-Host ""

Write-Host "IMPORTANT: Notez bien les mots de passe choisis !" -ForegroundColor Red
Write-Host "Ils seront necessaires pour chaque signature d'app" -ForegroundColor Red
Write-Host ""

# Commande de generation
$cmd = "keytool -genkey -v -keystore $KEYSTORE_NAME -alias $KEY_ALIAS -keyalg RSA -keysize 2048 -validity 10000"

Write-Host "Commande a executer:" -ForegroundColor Cyan
Write-Host $cmd -ForegroundColor White
Write-Host ""

Write-Host "Informations a fournir:" -ForegroundColor Yellow
Write-Host "- Mot de passe du keystore (NOTEZ-LE !)" -ForegroundColor Yellow
Write-Host "- Nom: Clavier Kreyol Karukera" -ForegroundColor Yellow
Write-Host "- Unite organisationnelle: Potomitan" -ForegroundColor Yellow
Write-Host "- Organisation: Potomitan" -ForegroundColor Yellow
Write-Host "- Ville: Pointe-a-Pitre" -ForegroundColor Yellow
Write-Host "- Etat/Province: Guadeloupe" -ForegroundColor Yellow
Write-Host "- Code pays: GP" -ForegroundColor Yellow
Write-Host ""

$response = Read-Host "Continuer la generation du keystore ? (y/N)"
if ($response -eq "y" -or $response -eq "Y") {
    Write-Host "Generation en cours..." -ForegroundColor Green
    Invoke-Expression $cmd
    
    if (Test-Path $KEYSTORE_NAME) {
        Write-Host ""
        Write-Host "SUCCES: Keystore genere !" -ForegroundColor Green
        Write-Host "Fichier: $KEYSTORE_NAME" -ForegroundColor White
        Write-Host ""
        Write-Host "PROCHAINES ETAPES:" -ForegroundColor Cyan
        Write-Host "1. Mettre a jour build.gradle avec ce keystore" -ForegroundColor White
        Write-Host "2. Creer gradle.properties avec les mots de passe" -ForegroundColor White
        Write-Host "3. Regenerer l'AAB avec signature de production" -ForegroundColor White
        Write-Host ""
        Write-Host "SECURITE:" -ForegroundColor Red
        Write-Host "- Sauvegardez ce keystore en lieu sur !" -ForegroundColor Red
        Write-Host "- Ne le committez JAMAIS dans Git !" -ForegroundColor Red
        Write-Host "- Notez les mots de passe dans un gestionnaire sur !" -ForegroundColor Red
    } else {
        Write-Host "ERREUR: Keystore non genere" -ForegroundColor Red
    }
} else {
    Write-Host "Generation annulee" -ForegroundColor Yellow
}