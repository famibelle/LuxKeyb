---
title: "Lëtzebuergesch Clavier : le clavier luxembourgeois pour Android"
description: "Clavier Android gratuit pour écrire en lëtzebuergesch : suggestions de mots, touches ë ä é, correcteur orthographique, 100 % hors ligne et sans publicité."
lang: fr
---

<nav class="site">
  <strong>🏠 Accueil</strong> ·
  <a href="guide.html">📘 Guide</a> ·
  <a href="privacy/privacy-policy.html">🔒 Confidentialité</a> ·
  <a href="feedbacks_form.html">💬 Retours</a> ·
  <a href="https://github.com/famibelle/LuxKeyb/releases/latest">📲 Télécharger</a> ·
  <a href="https://github.com/famibelle/LuxKeyb">💻 GitHub</a> ·
  <button type="button" class="theme-toggle" aria-label="Changer de thème">🌙</button>
</nav>

# Lëtzebuergesch Clavier, le clavier luxembourgeois pour Android

**Écrire en lëtzebuergesch sur son téléphone, sans se battre contre le clavier.**
Lëtzebuergesch Clavier est un clavier Android **gratuit, open source, sans
publicité et entièrement hors ligne**, qui propose des suggestions de mots en
luxembourgeois pendant la frappe.

Plus besoin de chercher un `ë` dans un menu d'accents : les trois diacritiques
qui portent la langue — **é**, **ä** et **ë** — ont chacune leur touche.

<div style="display:flex;justify-content:center;align-items:center;gap:32px;
            flex-wrap:wrap;margin:24px 0;">
  <a href="https://github.com/famibelle/LuxKeyb/releases/latest/download/LetzebuergeschClavier-latest.apk"
     style="display:inline-block;padding:14px 28px;background:#ED2939;color:#fff;
            border-radius:8px;font-weight:bold;text-decoration:none;font-size:1.1em;">
    📲 Télécharger l'APK (version 10.9.2)
  </a>
  <figure style="margin:0;text-align:center;">
    <img src="assets/qr-luxkeyb-apk.png"
         alt="QR code téléchargeant directement l'APK de la dernière version de Lëtzebuergesch Clavier"
         width="160" height="160"
         style="display:block;width:160px;height:160px;background:#fff;
                border-radius:8px;padding:6px;box-sizing:border-box;">
    <figcaption style="margin-top:8px;font-size:0.9em;opacity:0.8;">
      Scannez : le téléchargement démarre
    </figcaption>
  </figure>
</div>

<p align="center"><em>Android 5.0 ou plus récent · environ 3 Mo · aucune permission réseau</em></p>

## Le clavier en action

<div align="center" style="display:flex;justify-content:center;gap:12px;flex-wrap:wrap;">
   <img src="Screenshots/lux_suggestions.png" alt="Suggestions de mots en luxembourgeois et en français pendant la frappe" width="24%">
   <img src="Screenshots/lux_accents.png" alt="Appui long sur la touche e affichant é, ë, è et ê" width="24%">
   <img src="Screenshots/lux_numerique.png" alt="Mode chiffres et symboles du clavier" width="24%">
   <img src="Screenshots/lux_onboarding.png" alt="Parcours d'installation guidé de l'application" width="24%">
</div>

Le clavier reprend les trois couleurs du drapeau : le blanc pour les lettres, le
rouge pour ce qui agit — Entrée, changement de mode — et le bleu ciel pour la
barre d'espace et la ponctuation.

## Ce qu'il sait faire

### Il vous souffle les mots

Le dictionnaire compte **8 792 mots** et **23 169 contextes** de prédiction.
Après un espace, le clavier propose la suite probable de votre phrase d'après
les deux mots que vous venez d'écrire, pas seulement le dernier.

Les suggestions luxembourgeoises passent en premier ; le français prend le
relais à partir de trois lettres si aucun mot luxembourgeois ne correspond.

### Il pardonne les fautes de frappe

Une lettre oubliée, une lettre en trop, une touche voisine : les suggestions
arrivent quand même. Et vous pouvez écrire sans diacritiques — tapez
« letzebuergesch », le clavier vous propose « lëtzebuergesch ».

La casse est respectée, et les mots que vous employez souvent remontent d'eux-mêmes.

### Il corrige partout, pas seulement dans le clavier

Un correcteur orthographique système est fourni : une fois activé, vos mots
luxembourgeois cessent d'être soulignés en rouge dans Messages, Notes ou votre
messagerie.

### Il vous fait progresser

Chaque mot que vous employez fait avancer votre niveau, d'**Ufänker** à
**Sproochenmeeschter**, selon la part du dictionnaire que vous avez déjà
utilisée. Trois jeux de vocabulaire complètent le parcours : **Wuertsich**
(mots mêlés), **Wuertmix** (mots mélangés) et **Wuertriet**, où il faut deviner
un mot de cinq lettres en six essais.

### Il ne sait rien de vous

Le clavier **n'a aucun accès à Internet**. Rien de ce que vous tapez ne quitte
votre téléphone.

Seuls les mots déjà présents dans le dictionnaire sont comptés pour la
progression : un mot de passe, un nom propre ou un numéro n'y figurent pas et ne
sont donc jamais enregistrés. Le clavier se désactive de lui-même dans les
champs de mot de passe. Voir la [politique de confidentialité](privacy/privacy-policy.html).

## D'où viennent les suggestions

Le dictionnaire et les n-grammes sont générés à partir d'un corpus de
luxembourgeois contemporain, publié en jeu de données ouvert :
[POTOMITAN/luxembourgish-corpus](https://huggingface.co/datasets/POTOMITAN/luxembourgish-corpus).

Le pipeline est relancé à chaque publication, ce qui fait évoluer les
suggestions avec l'usage réel de la langue plutôt qu'avec une liste figée.

## Installer

1. [Téléchargez l'APK](https://github.com/famibelle/LuxKeyb/releases/latest/download/LetzebuergeschClavier-latest.apk),
   ou scannez le QR code en haut de page depuis votre téléphone : dans les deux
   cas le téléchargement démarre directement. Toutes les versions restent
   listées sur la [page des releases](https://github.com/famibelle/LuxKeyb/releases).
2. Autorisez l'installation depuis cette source, si Android le demande.
3. Ouvrez l'application : elle vous guide en trois étapes pour activer le
   clavier, le sélectionner, puis l'essayer.

Android affiche au passage un avertissement générique, montré pour **tout**
clavier tiers, sur la capture éventuelle de ce que vous tapez. Il est normal, et
l'onglet Guide de l'application explique pourquoi ce clavier-ci ne peut rien
envoyer nulle part.

## Contribuer

Le code est sur [GitHub](https://github.com/famibelle/LuxKeyb) sous licence MIT.

Un mot manque, une suggestion tombe à côté, une touche vous gêne ? Ouvrez une
[issue](https://github.com/famibelle/LuxKeyb/issues) ou passez par le
[formulaire de retours](feedbacks_form.html). Le dictionnaire progresse surtout
grâce à ces signalements.

---

<p align="center"><em>Fait au Luxembourg avec ❤️ — « Mir wëlle bleiwe wat mir sinn »</em></p>
