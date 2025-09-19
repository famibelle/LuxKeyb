# Script pour generer le keystore de production pour Potomitan Kreyol Keyboard
# Executer depuis: android_keyboard/

Write-Host "=== GENERATION KEYSTORE PRODUCTION - Potomitan Kreyol Keyboard ===" -ForegroundColor Green
Write-Host ""

# Configuration du keystore
$KEYSTORE_NAME = "potomitan-keystore.jks"
$KEY_ALIAS = "potomitan-release-key"
$KEYSTORE_PATH = "app\$KEYSTORE_NAME"
$VALIDITY_YEARS = 25

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Keystore: $KEYSTORE_NAME"
Write-Host "  Alias: $KEY_ALIAS"
Write-Host "  Validite: $VALIDITY_YEARS ans"
Write-Host "  Chemin: $KEYSTORE_PATH"
Write-Host ""

# Verifier si le keystore existe deja
if (Test-Path $KEYSTORE_PATH) {
    Write-Host "ATTENTION: Le keystore existe deja!" -ForegroundColor Red
    Write-Host "   Chemin: $KEYSTORE_PATH"
    $response = Read-Host "Voulez-vous le remplacer? (y/N)"
    if ($response -ne "y" -and $response -ne "Y") {
        Write-Host "Operation annulee" -ForegroundColor Red
        exit 1
    }
    Remove-Item $KEYSTORE_PATH -Force
    Write-Host "Ancien keystore supprime" -ForegroundColor Green
}

Write-Host "Generation du keystore de production..." -ForegroundColor Cyan
Write-Host ""

# Utiliser des valeurs par defaut pour eviter les problemes d'interaction
$storePwd = "potomitan2024!"
$keyPwd = "potomitan2024!"
$dn = "CN=Potomitan,OU=IT,O=Potomitan,L=Port-au-Prince,ST=Ouest,C=HT"

Write-Host "Informations utilisees:" -ForegroundColor Yellow
Write-Host "  Organisation: Potomitan"
Write-Host "  Ville: Port-au-Prince"
Write-Host "  Pays: Haiti (HT)"
Write-Host "  Mot de passe: potomitan2024!"
Write-Host ""

try {
    # Executer keytool
    $arguments = @(
        "-genkey", "-v",
        "-keystore", $KEYSTORE_PATH,
        "-alias", $KEY_ALIAS,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", ($VALIDITY_YEARS * 365),
        "-storepass", $storePwd,
        "-keypass", $keyPwd,
        "-dname", $dn
    )
    
    Write-Host "Execution de keytool..." -ForegroundColor Cyan
    $process = Start-Process -FilePath "keytool" -ArgumentList $arguments -Wait -PassThru -NoNewWindow
    
    if ($process.ExitCode -eq 0) {
        Write-Host ""
        Write-Host "KEYSTORE GENERE AVEC SUCCES!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Fichier cree: $KEYSTORE_PATH" -ForegroundColor Cyan
        Write-Host "Alias: $KEY_ALIAS" -ForegroundColor Cyan
        Write-Host "Mot de passe: potomitan2024!" -ForegroundColor Cyan
        Write-Host ""
        
        # Generer le fichier de configuration
        $configContent = @"
# Configuration keystore production - A ajouter dans build.gradle

android {
    signingConfigs {
        release {
            storeFile file('$KEYSTORE_NAME')
            storePassword 'potomitan2024!'
            keyAlias '$KEY_ALIAS'
            keyPassword 'potomitan2024!'
            v1SigningEnabled true
            v2SigningEnabled true
        }
    }
}
"@
        
        $configFile = "keystore-config.txt"
        $configContent | Out-File -FilePath $configFile -Encoding UTF8
        Write-Host "Configuration generee: $configFile" -ForegroundColor Cyan
        Write-Host ""
        
        Write-Host "PROCHAINES ETAPES:" -ForegroundColor Yellow
        Write-Host "   1. Modifier build.gradle avec la configuration generee"
        Write-Host "   2. Tester avec: gradlew assembleRelease"
        Write-Host "   3. Generer AAB: gradlew bundleRelease"
        Write-Host ""
        
    } else {
        Write-Host ""
        Write-Host "ERREUR lors de la generation du keystore" -ForegroundColor Red
        Write-Host "   Code de sortie: $($process.ExitCode)" -ForegroundColor Red
        exit 1
    }
    
} catch {
    Write-Host ""
    Write-Host "ERREUR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "KEYSTORE DE PRODUCTION PRET!" -ForegroundColor Green