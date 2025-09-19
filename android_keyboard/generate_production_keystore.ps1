# Script pour générer le keystore de production pour Potomitan Kreyol Keyboard
# Exécuter depuis: android_keyboard/
# Prérequis: Java JDK installé

Write-Host "=== GÉNÉRATION KEYSTORE PRODUCTION - Potomitan Kreyol Keyboard ===" -ForegroundColor Green
Write-Host ""

# Configuration du keystore
$KEYSTORE_NAME = "potomitan-keystore.jks"
$KEY_ALIAS = "potomitan-release-key"
$KEYSTORE_PATH = "app\src\main\assets\$KEYSTORE_NAME"
$VALIDITY_YEARS = 25

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Keystore: $KEYSTORE_NAME"
Write-Host "  Alias: $KEY_ALIAS"
Write-Host "  Validité: $VALIDITY_YEARS ans"
Write-Host "  Chemin: $KEYSTORE_PATH"
Write-Host ""

# Vérifier si le keystore existe déjà
if (Test-Path $KEYSTORE_PATH) {
    Write-Host "⚠️  ATTENTION: Le keystore existe déjà!" -ForegroundColor Red
    Write-Host "   Chemin: $KEYSTORE_PATH"
    $response = Read-Host "Voulez-vous le remplacer? (y/N)"
    if ($response -ne "y" -and $response -ne "Y") {
        Write-Host "❌ Opération annulée" -ForegroundColor Red
        exit 1
    }
    Remove-Item $KEYSTORE_PATH -Force
    Write-Host "✅ Ancien keystore supprimé" -ForegroundColor Green
}

# Créer le répertoire assets s'il n'existe pas
$assetsDir = "app\src\main\assets"
if (!(Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir -Force
    Write-Host "✅ Répertoire assets créé" -ForegroundColor Green
}

Write-Host "🔐 Génération du keystore de production..." -ForegroundColor Cyan
Write-Host "   Vous allez devoir saisir les informations suivantes:" -ForegroundColor Yellow
Write-Host "   - Mot de passe du keystore (IMPORTANT: notez-le !)"
Write-Host "   - Informations sur l'organisation"
Write-Host ""

# Générer le keystore avec keytool
$keytoolCmd = "keytool -genkey -v -keystore `"$KEYSTORE_PATH`" -alias $KEY_ALIAS -keyalg RSA -keysize 2048 -validity $($VALIDITY_YEARS * 365) -storepass `"changeme`" -keypass `"changeme`""

Write-Host "Commande keytool:" -ForegroundColor Gray
Write-Host $keytoolCmd -ForegroundColor Gray
Write-Host ""

try {
    # Demander les informations de signature
    Write-Host "📝 Saisie des informations de signature:" -ForegroundColor Cyan
    $storePassword = Read-Host -AsSecureString "Mot de passe du keystore (minimum 6 caractères)"
    $keyPassword = Read-Host -AsSecureString "Mot de passe de la clé (peut être identique)"
    
    # Convertir les mots de passe sécurisés
    $storePwd = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePassword))
    $keyPwd = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassword))
    
    Write-Host ""
    Write-Host "Informations sur l'organisation:" -ForegroundColor Cyan
    $commonName = Read-Host "Nom complet ou nom de l'organisation [Potomitan]"
    if ([string]::IsNullOrEmpty($commonName)) { $commonName = "Potomitan" }
    
    $organizationalUnit = Read-Host "Unité organisationnelle [IT]"
    if ([string]::IsNullOrEmpty($organizationalUnit)) { $organizationalUnit = "IT" }
    
    $organization = Read-Host "Organisation [Potomitan]"
    if ([string]::IsNullOrEmpty($organization)) { $organization = "Potomitan" }
    
    $city = Read-Host "Ville [Port-au-Prince]"
    if ([string]::IsNullOrEmpty($city)) { $city = "Port-au-Prince" }
    
    $state = Read-Host "Province/État [Ouest]"
    if ([string]::IsNullOrEmpty($state)) { $state = "Ouest" }
    
    $country = Read-Host "Code pays (2 lettres) [HT]"
    if ([string]::IsNullOrEmpty($country)) { $country = "HT" }
    
    # Construire le DN (Distinguished Name)
    $dn = "CN=$commonName, OU=$organizationalUnit, O=$organization, L=$city, ST=$state, C=$country"
    
    Write-Host ""
    Write-Host "🔨 Génération en cours..." -ForegroundColor Yellow
    
    # Exécuter keytool
    $process = Start-Process -FilePath "keytool" -ArgumentList @(
        "-genkey", "-v",
        "-keystore", "`"$KEYSTORE_PATH`"",
        "-alias", $KEY_ALIAS,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", ($VALIDITY_YEARS * 365),
        "-storepass", $storePwd,
        "-keypass", $keyPwd,
        "-dname", "`"$dn`""
    ) -Wait -PassThru -NoNewWindow
    
    if ($process.ExitCode -eq 0) {
        Write-Host ""
        Write-Host "✅ KEYSTORE GÉNÉRÉ AVEC SUCCÈS!" -ForegroundColor Green
        Write-Host ""
        Write-Host "📂 Fichier créé: $KEYSTORE_PATH" -ForegroundColor Cyan
        Write-Host "🔑 Alias: $KEY_ALIAS" -ForegroundColor Cyan
        Write-Host ""
        
        # Générer le fichier de configuration pour build.gradle
        $configContent = @"
# Configuration du keystore de production - À ajouter à build.gradle
# ATTENTION: NE PAS COMMITER CE FICHIER AVEC LES MOTS DE PASSE !

android {
    signingConfigs {
        release {
            storeFile file('src/main/assets/$KEYSTORE_NAME')
            storePassword '$storePwd'
            keyAlias '$KEY_ALIAS'
            keyPassword '$keyPwd'
            // Algorithmes recommandés pour Google Play Store
            v1SigningEnabled true
            v2SigningEnabled true
        }
    }
}

# Dans buildTypes > release, remplacer:
# signingConfig signingConfigs.debug
# par:
# signingConfig signingConfigs.release
"@
        
        $configFile = "keystore-config.txt"
        $configContent | Out-File -FilePath $configFile -Encoding UTF8
        Write-Host "📋 Configuration générée: $configFile" -ForegroundColor Cyan
        Write-Host ""
        
        Write-Host "⚠️  IMPORTANT - SÉCURITÉ:" -ForegroundColor Red
        Write-Host "   • Sauvegardez le keystore et les mots de passe en sécurité"
        Write-Host "   • Ne commitez JAMAIS le keystore ou les mots de passe"
        Write-Host "   • Le keystore est nécessaire pour toutes les mises à jour"
        Write-Host ""
        
        Write-Host "📋 PROCHAINES ÉTAPES:" -ForegroundColor Yellow
        Write-Host "   1. Modifier build.gradle avec la configuration générée"
        Write-Host "   2. Tester la signature avec: gradlew assembleRelease"
        Write-Host "   3. Générer l'AAB final: gradlew bundleRelease"
        Write-Host ""
        
        # Afficher les informations du keystore
        Write-Host "🔍 Vérification du keystore:" -ForegroundColor Cyan
        $verifyCmd = "keytool -list -v -keystore `"$KEYSTORE_PATH`" -storepass $storePwd"
        Invoke-Expression $verifyCmd
        
    } else {
        Write-Host ""
        Write-Host "❌ ERREUR lors de la génération du keystore" -ForegroundColor Red
        Write-Host "   Code de sortie: $($process.ExitCode)" -ForegroundColor Red
        exit 1
    }
    
} catch {
    Write-Host ""
    Write-Host "❌ ERREUR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🎉 KEYSTORE DE PRODUCTION PRÊT!" -ForegroundColor Green
Write-Host "   Vous pouvez maintenant configurer la signature de production."