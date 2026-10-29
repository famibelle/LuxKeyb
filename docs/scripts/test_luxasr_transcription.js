#!/usr/bin/env node
/**
 * Tests du normaliseur et des exports de la rubrique Labs « Transcription ».
 *
 *     node docs/scripts/test_luxasr_transcription.js
 *
 * Le premier cas est une **vraie réponse** de LuxASR (relevé du 29 octobre
 * 2026, `.claude/skills/transcription/releves/`). Les suivants sont
 * synthétiques : ils couvrent les autres formes que le normaliseur accepte
 * (variantes de WhisperX, SRT, VTT, texte à préfixes), pas des réponses
 * observées. Tout nouveau relevé réel s'ajoute en tête
 * (.claude/skills/transcription/SKILL.md § 8).
 */
'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

require(path.join(__dirname, '..', 'assets', 'luxasr-transcription.js'));
const T = globalThis.LuxAsrTranscription;

const cas = [];
function test(nom, f) { cas.push([nom, f]); }

const nom = (l) => ({ SPEAKER_00: 'Anna', SPEAKER_01: 'Jos' })[l] || l;
const RELEVES = path.join(__dirname, '..', '..', '.claude', 'skills', 'transcription',
                          'releves', '2026-10-29');
const RELEVE = path.join(RELEVES, 'resultat.json');
const mots = (t) => t.split(/\s+/).filter(Boolean).length;

test('Réel (29/10/2026, num_speakers=2) : deux voix, et la boucle « deen vun » réduite', () => {
  const brut = fs.readFileSync(path.join(RELEVES, 'resultat-2voix.json'), 'utf8');
  const source = JSON.parse(brut);
  const d = T.normaliser(brut);
  assert.deepStrictEqual(d.locuteurs, ['SPEAKER_01', 'SPEAKER_00']);
  assert.strictEqual(d.boucles.length, 1, JSON.stringify(d.boucles));
  const b = d.boucles[0];
  assert.ok(/^(deen vun|vun deen)$/.test(b.motif) && b.fois >= 15, JSON.stringify(b));
  assert.ok(b.debut > 27 && b.debut < 29, 'la boucle est située dans le temps : ' + b.debut);
  const sortie = d.segments.map((s) => s.texte).join(' ');
  assert.ok(!/(deen vun ){3}/.test(sortie), 'plus de boucle à l\'affichage');
  assert.ok(sortie.includes('eent vun deen v fir Arthros.'), 'la première occurrence est gardée');
  // Rien d'autre n'a bougé : seuls les mots de la boucle manquent.
  assert.strictEqual(mots(source.map((s) => s.text).join(' ')) - mots(sortie), (b.fois - 1) * mots(b.motif));
  // Les 9 changements de voix du service sont tous là (11 segments, dont deux
  // consécutifs de SPEAKER_00) : le découpage en phrases n'en crée ni n'en perd.
  const changements = d.segments.filter((s, i) => i && s.locuteur !== d.segments[i - 1].locuteur).length;
  assert.strictEqual(changements, 9);
});

test('Réel (29/10/2026) : tableau à la racine, un segment de 2 min coupé en phrases', () => {
  const brut = fs.readFileSync(RELEVE, 'utf8');
  const source = JSON.parse(brut)[0];
  const d = T.normaliser(brut);
  assert.strictEqual(d.format, 'json');
  assert.deepStrictEqual(d.locuteurs, ['SPEAKER_00'], 'le service n\'a vu qu\'une voix');
  assert.ok(d.segments.length >= 10, d.segments.length + ' phrases');
  // Fidélité : les phrases recollées redonnent le texte du service, élisions
  // comprises (« d'Leit », pas « d' Leit »).
  assert.strictEqual(d.segments.map((s) => s.texte).join(' '), source.text.replace(/\s+/g, ' ').trim());
  assert.ok(d.segments.some((s) => s.texte.includes("d'Leit")));
  for (const s of d.segments) {
    assert.ok(s.fin - s.debut <= 9, `phrase de ${(s.fin - s.debut).toFixed(1)} s : ${s.texte}`);
    assert.ok(s.texte.length <= 115, `phrase de ${s.texte.length} caractères`);
  }
  assert.strictEqual(d.segments[0].debut, source.start);
  assert.strictEqual(d.segments[d.segments.length - 1].fin, source.end);
  const tours = T.tours(d);
  assert.ok(tours.length >= 3, 'deux minutes d\'une voix font plusieurs paragraphes');
  assert.ok(T.versSrt(d, nom).split('\n\n').length === d.segments.length, 'un sous-titre par phrase');
  assert.deepStrictEqual(d.boucles, [], '« Wéi ginn. Wéi ginn. » n\'est pas une boucle');
});

test('Boucles : cinq répétitions au moins, la première gardée, la vraie répétition intacte', () => {
  let d = T.normaliser('Nee, nee, nee, nee. Gutt.');
  assert.strictEqual(d.segments[0].texte, 'Nee, nee, nee, nee. Gutt.', 'quatre fois, c\'est de la parole');
  assert.deepStrictEqual(d.boucles, []);
  d = T.normaliser('1\n00:00:01,000 --> 00:00:04,000\nMir hunn et gesot gesot gesot gesot gesot gesot, merci.\n');
  assert.strictEqual(d.segments[0].texte, 'Mir hunn et gesot, merci.');
  assert.deepStrictEqual(d.boucles, [{ debut: 1, fois: 6, motif: 'gesot' }]);
  d = T.normaliser([{ start: 0, end: 3, speaker: 'A', text: 'an der an der an der an der an der Stad' }]);
  assert.strictEqual(d.segments[0].texte, 'an der Stad');
  assert.strictEqual(d.boucles[0].motif, 'an der');
});

test('WhisperX : segments, temps et locuteurs', () => {
  const d = T.normaliser(JSON.stringify({
    segments: [
      { start: 0.5, end: 3.2, text: ' Moien, wéi geet et?', speaker: 'SPEAKER_00' },
      { start: 3.4, end: 5.0, text: ' Gutt, merci.', speaker: 'SPEAKER_01' },
      { start: 5.1, end: 7.9, text: ' A wat mécht d\'Famill?', speaker: 'SPEAKER_01' },
    ],
  }));
  assert.strictEqual(d.format, 'json');
  assert.deepStrictEqual(d.locuteurs, ['SPEAKER_00', 'SPEAKER_01']);
  assert.ok(d.horodate && d.diarise);
  assert.strictEqual(d.segments[0].texte, 'Moien, wéi geet et?');
  const t = T.tours(d);
  assert.strictEqual(t.length, 2, 'deux segments consécutifs de Jos font un tour');
  assert.deepStrictEqual(t[1].indices, [1, 2]);
});

test('WhisperX : un segment dont les mots changent de locuteur est coupé', () => {
  const d = T.normaliser({
    segments: [{
      start: 0, end: 4, text: ' Jo. Nee, net haut.', speaker: 'SPEAKER_00',
      words: [
        { word: 'Jo.', start: 0.0, end: 0.4, speaker: 'SPEAKER_00' },
        { word: 'Nee,', start: 1.0, end: 1.3, speaker: 'SPEAKER_01' },
        { word: 'net', start: 1.4, end: 1.6 },
        { word: 'haut.', start: 1.7, end: 2.0, speaker: 'SPEAKER_01' },
      ],
    }],
  });
  assert.strictEqual(d.segments.length, 2);
  assert.deepStrictEqual(d.segments.map((s) => s.locuteur), ['SPEAKER_00', 'SPEAKER_01']);
  assert.strictEqual(d.segments[1].texte, 'Nee, net haut.');
  assert.strictEqual(d.segments[1].debut, 1.0);
  assert.strictEqual(d.segments[1].fin, 2.0);
});

test('Phrases : point après 2 s, virgule après 4 s, coupe franche après 8 s', () => {
  const mots = [];
  let t = 0;
  const dire = (w) => { mots.push({ word: ' ' + w, start: t, end: t + 0.4, speaker: 'A' }); t += 0.5; };
  'Jo. Nee.'.split(' ').forEach(dire);                           // trop court pour fermer
  'Dat ass eng laang Iwwerleeung iwwer de Wieder.'.split(' ').forEach(dire);
  for (let i = 0; i < 30; i++) dire('wuert' + i);                // ni point ni virgule
  const texte = mots.map((m) => m.word).join('').trim();
  const d = T.normaliser([{ start: 0, end: t, text: texte, speaker: 'A', words: mots }]);
  assert.strictEqual(d.segments[0].texte, 'Jo. Nee. Dat ass eng laang Iwwerleeung iwwer de Wieder.');
  assert.ok(d.segments.slice(1).every((s) => s.fin - s.debut <= 8.5), 'aucune phrase au-delà de 8 s');
  assert.strictEqual(d.segments.map((s) => s.texte).join(' '), texte);
});

test('Sans alignement possible, les mots sont recollés', () => {
  const d = T.normaliser([{ start: 0, end: 2, text: 'Texte sans rapport',
    words: [{ word: ' Moien', start: 0, end: 1, speaker: 'A' }, { word: ' Jos', start: 1, end: 2, speaker: 'B' }] }]);
  assert.deepStrictEqual(d.segments.map((s) => [s.locuteur, s.texte]), [['A', 'Moien'], ['B', 'Jos']]);
});

test('Variantes de clés, temps en chaînes, tableau sous « result »', () => {
  const d = T.normaliser({
    status: 'completed',
    result: { utterances: [
      { begin: '00:00:01.500', stop: '00:00:02,750', spk: 1, transcript: 'Éischten Deel' },
      { start_time: 3, end_time: 4, speaker_label: 2, words: [{ text: 'zweete' }, { text: 'Deel' }] },
    ] },
  });
  assert.deepStrictEqual(d.locuteurs, ['1', '2']);
  assert.strictEqual(d.segments[0].debut, 1.5);
  assert.strictEqual(d.segments[0].fin, 2.75);
  assert.strictEqual(d.segments[1].texte, 'zweete Deel');
});

test('Réponse multi-format : la clé « json » est dépliée, même en chaîne', () => {
  const d = T.normaliser({ json: JSON.stringify([{ start: 0, end: 1, text: 'Moien', speaker: 'A' }]), text: 'Moien' });
  assert.strictEqual(d.format, 'json');
  assert.deepStrictEqual(d.locuteurs, ['A']);
});

test('Préfixe de locuteur dans le texte du segment', () => {
  const d = T.normaliser([{ start: 0, end: 2, text: 'SPEAKER_01: Bonjour' }]);
  assert.strictEqual(d.segments[0].locuteur, 'SPEAKER_01');
  assert.strictEqual(d.segments[0].texte, 'Bonjour');
});

test('JSON sans segments mais avec un texte à préfixes', () => {
  const d = T.normaliser('{"text": "[SPEAKER_00] Moien\\n[SPEAKER_01] Salut\\nwéi geet et"}');
  assert.deepStrictEqual(d.segments.map((s) => [s.locuteur, s.texte]),
    [['SPEAKER_00', 'Moien'], ['SPEAKER_01', 'Salut wéi geet et']]);
  assert.ok(!d.horodate);
});

test('JSON de forme inconnue : on le dit, on ne devine pas', () => {
  const d = T.normaliser('{"job": "abc", "status": "completed"}');
  assert.strictEqual(d.format, 'inconnu');
  assert.strictEqual(d.segments.length, 0);
});

test('SRT avec préfixes de locuteur', () => {
  const d = T.normaliser('1\n00:00:01,000 --> 00:00:03,500\nSpeaker 1: Moien\n\n' +
                         '2\n00:00:04,000 --> 00:00:06,000\nSpeaker 2: Salut\nalleguer\n');
  assert.strictEqual(d.format, 'srt');
  assert.deepStrictEqual(d.locuteurs, ['Speaker 1', 'Speaker 2']);
  assert.strictEqual(d.segments[1].texte, 'Salut alleguer');
  assert.strictEqual(d.segments[1].debut, 4);
});

test('VTT avec balises de voix et entités', () => {
  const d = T.normaliser('WEBVTT\n\nNOTE essai\n\nc1\n00:01.000 --> 00:02.000\n<v Anna>Kaffi &amp; Kuch\n\n' +
                         '00:02.500 --> 00:04.000 align:start\n<v.loud Jos>Jo</v>\n');
  assert.strictEqual(d.format, 'vtt');
  assert.deepStrictEqual(d.segments.map((s) => [s.locuteur, s.texte, s.debut]),
    [['Anna', 'Kaffi & Kuch', 1], ['Jos', 'Jo', 2.5]]);
});

test('Texte simple sans locuteurs : un paragraphe par bloc', () => {
  const d = T.normaliser('Éischte Paragraph.\nNach eng Linn.\n\nZweete Paragraph.');
  assert.strictEqual(d.format, 'text');
  assert.ok(!d.diarise);
  assert.deepStrictEqual(d.segments.map((s) => s.texte), ['Éischte Paragraph. Nach eng Linn.', 'Zweete Paragraph.']);
});

test('Texte à la Whisper avec temps en tête de ligne', () => {
  const d = T.normaliser('[00:00.000 --> 00:02.000] SPEAKER_00: Moien\n[00:02.000 --> 00:03.500]  Merci');
  assert.deepStrictEqual(d.segments.map((s) => [s.debut, s.locuteur, s.texte]),
    [[0, 'SPEAKER_00', 'Moien'], [2, null, 'Merci']]);
});

test('Vide : aucun segment', () => {
  assert.strictEqual(T.normaliser('').segments.length, 0);
  assert.strictEqual(T.normaliser('   \n ').segments.length, 0);
  assert.strictEqual(T.normaliser('{"segments": []}').segments.length, 0);
});

test('Sans locuteurs, les tours coupent sur un silence de plus de 2 s', () => {
  const d = T.normaliser([{ start: 0, end: 1, text: 'a' }, { start: 1.5, end: 2, text: 'b' },
                          { start: 5, end: 6, text: 'c' }]);
  assert.deepStrictEqual(T.tours(d).map((t) => t.indices), [[0, 1], [2]]);
});

test('Exports TXT, SRT, VTT, JSON avec noms renommés', () => {
  const d = T.normaliser({ segments: [
    { start: 0.5, end: 3.25, text: 'Moien <alleguer>', speaker: 'SPEAKER_00' },
    { start: 65, text: 'Salut', speaker: 'SPEAKER_01' },
  ] });
  assert.strictEqual(T.versTexte(d, nom), '[0:00] Anna : Moien <alleguer>\n\n[1:05] Jos : Salut\n');
  assert.strictEqual(T.versSrt(d, nom),
    '1\n00:00:00,500 --> 00:00:03,250\nAnna: Moien <alleguer>\n\n' +
    '2\n00:01:05,000 --> 00:01:08,000\nJos: Salut\n');
  assert.strictEqual(T.versVtt(d, nom),
    'WEBVTT\n\n00:00:00.500 --> 00:00:03.250\n<v Anna>Moien &lt;alleguer&gt;\n\n' +
    '00:01:05.000 --> 00:01:08.000\n<v Jos>Salut\n');
  const j = JSON.parse(T.versJson(d, nom, 'interview.m4a'));
  assert.strictEqual(j.fichier, 'interview.m4a');
  assert.deepStrictEqual(j.locuteurs, [{ id: 'SPEAKER_00', nom: 'Anna' }, { id: 'SPEAKER_01', nom: 'Jos' }]);
  assert.strictEqual(j.segments[1].speaker, 'Jos');
});

test('Les exports relus redonnent le même document', () => {
  const d = T.normaliser({ segments: [
    { start: 0, end: 2, text: 'Moien', speaker: 'SPEAKER_00' },
    { start: 2, end: 4, text: 'Salut', speaker: 'SPEAKER_01' },
  ] });
  for (const f of [T.versTexte, T.versSrt, T.versVtt, T.versJson]) {
    const r = T.normaliser(f(d, nom));
    assert.deepStrictEqual(r.segments.map((s) => [s.locuteur, s.texte, s.debut]),
      [['Anna', 'Moien', 0], ['Jos', 'Salut', 2]], f.name);
  }
});

test('« Nom : » libre seulement s\'il est régulier dans le document', () => {
  const un = T.normaliser('1\n00:00:01,000 --> 00:00:02,000\nNo comment: just text\n\n' +
                          '2\n00:00:02,000 --> 00:00:03,000\nAn ordinary line\n\n' +
                          '3\n00:00:03,000 --> 00:00:04,000\nAnother one\n');
  assert.ok(!un.diarise, 'une ligne sur trois ne fait pas un locuteur');
  const phrase = T.normaliser('Wéi gesot. Dat ass et: Moien.');
  assert.ok(!phrase.diarise, 'une phrase qui contient deux-points n\'est pas un nom');
});

test('Horloge et type de contenu', () => {
  assert.strictEqual(T.horloge(5), '0:05');
  assert.strictEqual(T.horloge(3725.9), '1:02:05');
  assert.strictEqual(T.typeAudio({ type: 'audio/x-m4a', name: 'a.m4a' }), 'audio/mp4');
  assert.strictEqual(T.typeAudio({ type: 'video/mp4', name: 'a.mp4' }), 'video/mp4');
  assert.strictEqual(T.typeAudio({ type: '', name: 'Film.MOV' }), 'video/quicktime');
  assert.strictEqual(T.typeAudio({ type: '', name: 'Interview.OPUS' }), 'audio/ogg');
  assert.strictEqual(T.typeAudio({ type: 'audio/mpeg', name: 'a.mp3' }), 'audio/mpeg');
  assert.strictEqual(T.typeAudio({ type: 'application/pdf', name: 'a.pdf' }), null);
  assert.ok(T.estResultat({ name: 'resultat.JSON' }));
  assert.ok(!T.estResultat({ name: 'son.wav' }));
});

let echecs = 0;
for (const [n, f] of cas) {
  try { f(); console.log('ok    ' + n); }
  catch (e) { echecs++; console.log('ÉCHEC ' + n + '\n      ' + e.message.split('\n').join('\n      ')); }
}
console.log(`\n${cas.length - echecs}/${cas.length} réussis`);
process.exit(echecs ? 1 : 0);
