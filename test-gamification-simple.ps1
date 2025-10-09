# Test simple de la gamification
Write-Host "=== TEST SIMPLE GAMIFICATION ===" -ForegroundColor Green
Write-Host ""

# 1. Compilation
Write-Host "1. Compilation..." -ForegroundColor Yellow
cd android_keyboard
.\gradlew assembleDebug --quiet
if ($LASTEXITCODE -eq 0) {
    Write-Host "   OK - Compilation reussie" -ForegroundColor Green
} else {
    Write-Host "   ERREUR - Compilation echouee" -ForegroundColor Red
    exit 1
}

# 2. Installation
Write-Host ""
Write-Host "2. Installation..." -ForegroundColor Yellow
$apk = Get-ChildItem "app\build\outputs\apk\debug\*.apk" | Select-Object -First 1
adb install -r $apk.FullName 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "   OK - Installation reussie" -ForegroundColor Green
} else {
    Write-Host "   ERREUR - Installation echouee" -ForegroundColor Red
    exit 1
}

# 3. Reset app
Write-Host ""
Write-Host "3. Reset des donnees app..." -ForegroundColor Yellow
adb shell pm clear com.potomitan.kreyolkeyboard 2>&1 | Out-Null
Write-Host "   OK - Donnees effacees" -ForegroundColor Green

# 4. Activer le clavier manuellement
Write-Host ""
Write-Host "4. Activation du clavier..." -ForegroundColor Yellow
Write-Host "   MANUEL: Veuillez activer le clavier Potomitan dans les parametres" -ForegroundColor Cyan
Write-Host "   puis ouvrir l'app Messages et taper quelques mots creoles" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Appuyez sur ENTREE quand vous avez fini..." -ForegroundColor Yellow
Read-Host

# 5. Vérifier les logs
Write-Host ""
Write-Host "5. Verification des logs..." -ForegroundColor Yellow
Write-Host ""

$logs = adb logcat -d | Select-String "CreoleDictUsage|Gamification|Coverage" | Select-Object -Last 50

if ($logs.Count -gt 0) {
    Write-Host "   LOGS TROUVES:" -ForegroundColor Green
    $logs | ForEach-Object { Write-Host $_.Line -ForegroundColor White }
} else {
    Write-Host "   AUCUN LOG - Le clavier n'a peut-etre pas ete active" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== FIN DU TEST ===" -ForegroundColor Green
