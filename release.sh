#!/bin/bash

# 🇬🇵 Script de Release Potomitan Kreyol Keyboard
# Usage: ./release.sh [version]

set -e

VERSION=${1:-"v1.0.0"}
DATE=$(date +%Y-%m-%d)

echo "🇬🇵 === POTOMITAN KREYOL KEYBOARD RELEASE SCRIPT ==="
echo "📦 Version: $VERSION"
echo "📅 Date: $DATE"
echo ""

# Vérifier que nous sommes dans le bon répertoire
if [ ! -d "android_keyboard" ]; then
    echo "❌ Erreur: Exécuter depuis la racine du projet (où se trouve android_keyboard/)"
    exit 1
fi

cd android_keyboard

echo "🧹 Nettoyage des builds précédents..."
./gradlew clean

echo "🏗️ Compilation Debug APK..."
./gradlew assembleDebug

echo "🏗️ Compilation Release APK..."
./gradlew assembleRelease

echo ""
echo "📊 Tailles des APK générés:"
ls -lh app/build/outputs/apk/debug/*.apk
ls -lh app/build/outputs/apk/release/*.apk

echo ""
echo "✅ Builds terminés avec succès!"
echo ""
echo "📋 Prochaines étapes pour publier sur GitHub:"
echo "1. Commit et push des changements: git add . && git commit -m 'Release $VERSION' && git push"
echo "2. Créer un tag: git tag $VERSION && git push origin $VERSION"
echo "3. Le workflow GitHub Actions créera automatiquement la release"
echo ""
echo "Ou utiliser le workflow manuel:"
echo "- Aller sur GitHub → Actions → 'Build and Release'"
echo "- Cliquer 'Run workflow' et spécifier la version: $VERSION"

cd ..
