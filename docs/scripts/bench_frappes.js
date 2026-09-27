#!/usr/bin/env node
/**
 * Banc d'essai du classement des suggestions : « le mot voulu est-il proposé
 * dans les trois premiers après k frappes ? »
 *
 *     python docs/scripts/export_paralux.py /tmp/paralux.json
 *     node docs/scripts/bench_frappes.js /tmp/paralux.json
 *
 * Pourquoi ce banc existe. Deux constantes de score ont cessé de fonctionner
 * en silence quand le corpus est passé à LuxAlign et que la fréquence maximale
 * a été multipliée par six (15 519 → 100 105) :
 *
 *   - EDIT_DISTANCE_WEIGHT, à 100 000, laissait une correction à deux éditions
 *     vers un mot très fréquent passer devant une correction à une édition ;
 *   - le bonus de contexte n-gramme, à 50, ne réordonnait plus rien du tout :
 *     le passer à 0 ne changeait pratiquement pas le résultat.
 *
 * Les deux sont passées à travers 138 tests verts, parce qu'aucun ne mesurait
 * ce que l'utilisateur ressent — le nombre de frappes économisées. C'est ce que
 * mesure ce fichier, et c'est pourquoi il tourne en CI avec un plancher.
 *
 * Ce qu'il mesure exactement : pour chaque mot d'au moins quatre lettres ayant
 * au moins un mot devant lui dans la phrase, on tape ses k premières lettres et
 * on regarde si le mot figure dans les trois suggestions affichées. L'historique
 * est alimenté avec les mots réellement prononcés, pas avec les prédictions :
 * on mesure le classement, pas la capacité à s'auto-entretenir.
 *
 * Il s'appuie sur docs/assets/simulateur-engine.js, miroir JavaScript de
 * SuggestionEngine. Ce miroir peut diverger — il l'a fait, en gardant
 * EDIT_DISTANCE_WEIGHT à 100 000 pendant deux jours après la correction côté
 * Android. `SimulatorMirrorTest` (suite JVM) verrouille l'égalité des
 * constantes, sans quoi ce banc pourrait rester vert sur une application
 * cassée.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const RACINE = path.resolve(__dirname, '..', '..');
const ASSETS = path.join(RACINE, 'android_keyboard', 'app', 'src', 'main', 'assets');
const MOTEUR = path.join(RACINE, 'docs', 'assets', 'simulateur-engine.js');

// Planchers de non-régression, et non objectifs : ils sont placés sous les
// valeurs mesurées le 2026-09-04 (22,5 / 37,1 / 59,6) avec assez de marge pour
// absorber une régénération du dictionnaire, et assez près pour qu'un bonus
// redevenu inopérant les franchisse. Un bonus de contexte remis à 50 fait
// tomber la première colonne à 13,0 % : le plancher se déclenche.
const PLANCHERS = {1: 20.0, 2: 33.0, 3: 55.0};

// Découpage aligné sur celui du clavier : `InputProcessor.isWordCharacter`
// n'accepte que des lettres, donc l'apostrophe sépare — `d'Leit` donne `d`
// puis `Leit` — et les mots d'une lettre en sont. Le motif d'origine faisait
// l'inverse sur les deux points, et mesurait donc un modèle que l'application
// n'exécute pas.
const MOT = /[\p{L}\-]{1,}/gu;
const LONGUEUR_MINIMALE = 4; // sous 4 lettres, taper le mot coûte moins cher

function phrasesDepuis(chemin) {
  if (!chemin) {
    console.error('Usage : node docs/scripts/bench_frappes.js <paralux.json>');
    console.error('        (produit par docs/scripts/export_paralux.py)');
    process.exit(2);
  }
  return JSON.parse(fs.readFileSync(chemin, 'utf8'));
}

function main() {
  const phrases = phrasesDepuis(process.argv[2]);
  const E = require(MOTEUR);

  const moteur = new E.SuggestionEngine();
  moteur.loadDictionary(JSON.parse(fs.readFileSync(
    path.join(ASSETS, 'luxemburgish_dict.json'), 'utf8')));
  moteur.ngramModel = JSON.parse(fs.readFileSync(
    path.join(ASSETS, 'luxemburgish_ngrams.json'), 'utf8'));

  const touches = {1: 0, 2: 0, 3: 0};
  let evenements = 0;

  for (const phrase of phrases) {
    const mots = (phrase.match(MOT) || [])
      .map((m) => m.replace(/^-+|-+$/g, ''))
      .filter((m) => m.length >= 2);
    moteur.wordHistory = [];
    for (let i = 0; i < mots.length; i++) {
      const mot = mots[i];
      if (i >= 1 && mot.length >= LONGUEUR_MINIMALE) {
        evenements++;
        for (const k of [1, 2, 3]) {
          const propositions = moteur.getLuxSuggestions(mot.slice(0, k))
            .slice(0, 3).map((s) => s.word.toLowerCase());
          if (propositions.includes(mot.toLowerCase())) touches[k]++;
        }
      }
      moteur.addWordToHistory(mot);
    }
  }

  const taux = (k) => 100 * touches[k] / evenements;
  console.log(`Banc de frappes — ${phrases.length} phrases inédites, ` +
              `${evenements} mots en contexte`);
  let echec = false;
  for (const k of [1, 2, 3]) {
    const t = taux(k);
    const plancher = PLANCHERS[k];
    const verdict = t >= plancher ? 'ok' : 'SOUS LE PLANCHER';
    if (t < plancher) echec = true;
    console.log(`  top-3 après ${k} frappe${k > 1 ? 's' : ''} : ` +
                `${t.toFixed(2)} %  (plancher ${plancher.toFixed(1)} %) ${verdict}`);
  }
  if (echec) {
    console.error('\n❌ Le classement des suggestions a régressé. Vérifier les ' +
                  'constantes de calculateDictionaryScore en regard de la ' +
                  'fréquence maximale du dictionnaire livré.');
    process.exit(1);
  }
}

main();
