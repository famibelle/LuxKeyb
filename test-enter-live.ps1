# Test ENTER en temps reel - Monitoring continu
Write-Host "=== TEST ENTER EN TEMPS REEL ===" -ForegroundColor Green
Write-Host ""

# 1. Verifier connexion
Write-Host "1. Verification connexion ADB..." -ForegroundColor Yellow
$device = adb devices | Select-String "device$"
if (!$device) {
    Write-Host "   ERREUR: Aucun appareil connecte" -ForegroundColor Red
    exit 1
}
Write-Host "   OK - Appareil connecte" -ForegroundColor Green

# 2. Trouver le PID
Write-Host ""
Write-Host "2. Recherche du processus Potomitan Keyboard..." -ForegroundColor Yellow
$processInfo = adb shell ps | Select-String "potomitan"

if (!$processInfo) {
    Write-Host "   Le clavier n'est pas actif. Veuillez:" -ForegroundColor Yellow
    Write-Host "   1. Activer le clavier Potomitan" -ForegroundColor Cyan
    Write-Host "   2. Ouvrir Messages" -ForegroundColor Cyan
    Write-Host "   3. Cliquer dans le champ de texte" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "   Appuyez sur ENTREE quand c'est fait..." -ForegroundColor Yellow
    Read-Host
    
    $processInfo = adb shell ps | Select-String "potomitan"
    if (!$processInfo) {
        Write-Host "   ERREUR: Processus toujours introuvable" -ForegroundColor Red
        exit 1
    }
}

$appPid = ($processInfo -split '\s+')[1]
Write-Host "   PID: $appPid" -ForegroundColor Cyan

# 3. Vider les logs
Write-Host ""
Write-Host "3. Nettoyage des logs..." -ForegroundColor Yellow
adb logcat -c
Write-Host "   OK" -ForegroundColor Green

# 4. Instructions TRES CLAIRES
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "INSTRUCTIONS IMPORTANTES:" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  1. Dans Messages, tapez UN MOT simple" -ForegroundColor White
Write-Host "     Ex: bon" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. APPUYEZ SUR LA TOUCHE ENTER (fleche retour a droite)" -ForegroundColor White
Write-Host "     -> Le message DOIT etre envoye" -ForegroundColor Gray
Write-Host "     -> Le clavier DOIT rester actif (Potomitan)" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. OBSERVEZ LE COMPORTEMENT:" -ForegroundColor White
Write-Host "     [OK] Le clavier Potomitan reste-t-il actif ?" -ForegroundColor Green
Write-Host "     [KO] Le clavier change-t-il pour Android default ?" -ForegroundColor Red
Write-Host ""
Write-Host "  4. NE CHANGEZ PAS de clavier manuellement !" -ForegroundColor Yellow
Write-Host "  5. NE FERMEZ PAS l'app Messages !" -ForegroundColor Yellow
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# 5. Monitoring en temps reel
Write-Host "MONITORING EN TEMPS REEL (logs du clavier):" -ForegroundColor Yellow
Write-Host "Appuyez sur Ctrl+C pour arreter le monitoring" -ForegroundColor Gray
Write-Host ""
Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray

# Monitoring avec couleurs
try {
    adb logcat --pid=$appPid | ForEach-Object {
        $line = $_
        
        # Colorier selon le contenu
        if ($line -match "TOUCHE PRESSEE") {
            Write-Host ""
            Write-Host "[ENTER] TOUCHE DETECTEE!" -ForegroundColor Cyan
            Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
            Write-Host $line -ForegroundColor White
        }
        elseif ($line -match "DEBUT handleEnter") {
            Write-Host "[ENTER] DEBUT handleEnter()" -ForegroundColor Cyan
            Write-Host $line -ForegroundColor White
        }
        elseif ($line -match "IME Action") {
            Write-Host $line -ForegroundColor Yellow
        }
        elseif ($line -match "Action SEND|Action SEARCH|Action GO|Action NEXT|Action DONE") {
            Write-Host $line -ForegroundColor Magenta
        }
        elseif ($line -match "performEditorAction") {
            Write-Host $line -ForegroundColor Green
        }
        elseif ($line -match "FIN handleEnter") {
            Write-Host "[ENTER] FIN handleEnter()" -ForegroundColor Cyan
            Write-Host $line -ForegroundColor White
            Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
            Write-Host ""
        }
        elseif ($line -match "onFinishInput") {
            Write-Host ""
            Write-Host "[ALERT] onFinishInput() APPELE - CLAVIER VA SE FERMER!" -ForegroundColor Red
            Write-Host "------------------------------------------------------------" -ForegroundColor Red
            Write-Host $line -ForegroundColor Red
        }
        elseif ($line -match "DESTRUCTION DU SERVICE") {
            Write-Host "[ALERT] SERVICE DETRUIT - CLAVIER FERME!" -ForegroundColor Red
            Write-Host $line -ForegroundColor Red
        }
        elseif ($line -match "ERROR|Exception|Erreur") {
            Write-Host $line -ForegroundColor Red
        }
        elseif ($line -match "processKeyPress") {
            Write-Host $line -ForegroundColor White
        }
        else {
            # Logs normaux en gris
            Write-Host $line -ForegroundColor DarkGray
        }
    }
}
catch {
    Write-Host ""
    Write-Host "Monitoring arrete par l'utilisateur" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== FIN DU TEST ===" -ForegroundColor Green
