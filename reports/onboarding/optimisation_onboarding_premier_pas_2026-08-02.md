# Optimiser l'onboarding jusqu'au premier mot tapé : note d'analyse, 2 août 2026

## Contexte

Play Console (relevé du 2 août 2026) : 1,91 k acquisitions d'appareils pour seulement 54 premières ouvertures et 41 appareils actifs par mois. Le pic vient de la couverture TV (Canal 10 le 24/07, Guadeloupe la 1ère le 28/07), donc d'un public grand public, pas early-adopter.

**Profil cible de cette note** : des personnes peu à l'aise avec la technologie, qui s'arrêtent au premier avertissement système, et qui s'attendent à ce que le clavier soit actif dès l'installation terminée.

Le parcours réel comporte 6 moments où ce profil peut décrocher :

1. Fin de l'installation Play Store : rien ne se passe, le clavier n'apparaît pas → « ça ne marche pas », désinstallation ou oubli. **C'est ici que se perd l'essentiel des 1,91 k.**
2. Ouverture de l'app : lecture du premier écran.
3. Saut vers les réglages système (écran inconnu, hors de l'app).
4. **L'avertissement Android** « Ce mode de saisie peut collecter tout le texte que vous saisissez, y compris les données personnelles... », suivi sur certains appareils d'un second dialogue. C'est le point d'abandon nommé par l'utilisateur.
5. Retour vers l'app (bouton Retour : pas évident pour tout le monde).
6. Sélection du clavier puis premier mot.

## Ce qui existe déjà dans le code (v10.x)

L'onboarding actuel est déjà solide, il ne s'agit pas de le refaire :

- `SettingsActivity` est l'unique activité de lancement et verrouille la navigation tant que le clavier n'est pas activé et sélectionné (`applyFirstRunMode`, SettingsActivity.kt:318).
- Clavier de démo interactif avant tout effort (SettingsActivity.kt:765) : la motivation précède la mécanique.
- Carte d'information avant l'avertissement système (SettingsActivity.kt:806) et carte « nudge » après un aller-retour infructueux dans les réglages (SettingsActivity.kt:788), qui diagnostique l'abandon au second avertissement.
- Enchaînement automatique : retour des réglages → ouverture du sélecteur ; sélection → focus sur le champ de test (`chainNextStep`, SettingsActivity.kt:3102), via `ContentObserver` sur les réglages système.
- Jalons de funnel horodatés en local (`funnel_first_open`, `funnel_demo_first_key`, `funnel_keyboard_enabled`, `funnel_keyboard_selected`, `funnel_first_word`).
- Aucune permission INTERNET : argument de confiance vérifiable, déjà mentionné dans la carte pré-avertissement.

## Contrainte indépassable

Android interdit à une application d'activer elle-même un IME : le passage par les réglages système et l'avertissement de sécurité sont imposés par l'OS, non personnalisables, identiques pour Gboard, SwiftKey et tous les autres. L'optimisation ne peut donc porter que sur trois leviers : **préparer** (l'utilisateur sait ce qu'il va voir), **accompagner** (chaque écran dit quoi faire), **rattraper** (l'échec est détecté et relancé).

## Recommandations

### A. Avant l'ouverture de l'app : la fiche Play Store (impact maximal)

Le plus gros du funnel se perd avant même que le code de l'app puisse agir. Pour un public qui s'attend à « installé = activé », la fiche doit casser cette attente **avant** l'installation :

1. **Premier screenshot de la fiche = l'instruction, pas le produit.** Un visuel « Après l'installation, appuyez sur OUVRIR : l'app vous guide en 2 minutes », avec les 3 étapes illustrées. Aujourd'hui, l'instruction n'est que dans le texte de description, que ce public ne lit pas.
2. **Vidéo promo de 30 secondes** sur la fiche (screen recording : installation → ouverture → avertissement validé → premier mot en kréyòl). Pour une audience venue de la TV, la vidéo est le seul format qui sera réellement consommé. Nécessite un hébergement YouTube (action utilisateur).
3. **Première ligne de la description courte** : « Installez, puis OUVREZ l'application : elle active le clavier avec vous. » La ligne actuelle vend le produit, elle devrait d'abord vendre le geste.

### B. Au moment critique : l'avertissement système (le décrochage nommé)

La carte pré-avertissement actuelle est textuelle, passive (posée dans le flux de l'écran, au-dessus des étapes) et en petite taille (13 sp). Le profil cible ne la lit pas, puis découvre l'avertissement sans préparation.

4. **Remplacer la carte passive par un interstitiel bloquant au tap sur « Ouvrir les paramètres »** : un écran plein ou un dialogue qui montre **la capture d'écran réelle de l'avertissement Android**, avec une annotation visuelle « Appuyez sur OK, c'est normal » et la réassurance en une phrase (« Ce message s'affiche pour tous les claviers. Klavyé Kréyòl n'a pas d'accès Internet : rien ne quitte votre téléphone. »). Un seul bouton : « J'ai compris, on y va ». Montrer l'image exacte du dialogue effrayant à l'avance est bien plus efficace que le décrire : quand il apparaît, il est déjà connu.
5. **Dans le même interstitiel, montrer l'écran des réglages avec l'interrupteur entouré.** Les écrans One UI (Samsung) et MIUI/HyperOS (Xiaomi) diffèrent du stock Android ; sélectionner la capture selon `Build.MANUFACTURER` couvrirait les deux marques dominantes localement, avec repli sur la capture stock.
6. **Illustrer le retour** : pictogramme du bouton/geste Retour (« appuyez sur ◀ pour revenir dans l'app ») à la fin de l'interstitiel. Le code rattrape déjà le retour (ouverture auto du sélecteur), mais encore faut-il que l'utilisateur revienne.
7. **Piste technique à essayer** : l'extra non documenté `:settings:fragment_args_key` avec l'ID de l'IME sur l'intent `ACTION_INPUT_METHOD_SETTINGS` fait défiler et surligner la bonne ligne sur les Settings AOSP. Non garanti selon les OEM, mais sans effet de bord si ignoré : à tester sur l'émulateur et un Samsung réel.

### C. Lisibilité pour un public peu technophile

8. **Grossir et raccourcir.** Les cartes d'onboarding sont en 13 et 14 sp avec des phrases de 2 à 3 lignes. Cible : 16 sp minimum, une seule instruction par carte, verbe d'action en tête (« Appuyez sur... », « Revenez ici »). Vérifier le rendu avec la taille de police système à 130 % (réglage fréquent chez les utilisateurs seniors).
9. **Une seule idée visible à la fois.** Aujourd'hui, l'écran de démarrage montre en même temps : héros + progression, clavier de démo, carte avertissement, 3 cartes d'étapes. Pour ce profil, envisager un mode « pas à pas » plein écran (une carte à la fois, bouton unique), en gardant l'écran riche actuel pour les retours après désélection.
10. **Guidage audio en kréyòl** (piste optionnelle, plus lourde) : un bouton « écouter les instructions » par étape toucherait les personnes qui lisent peu. Condition stricte : texte kréyòl validé par un locuteur, jamais généré (règle du projet), et enregistrement humain.

### D. Rattrapage et mesure

11. **Compter les abandons à l'avertissement.** Le nudge existe mais rien ne compte combien de fois il s'affiche. Ajouter un jalon local `funnel_settings_return_no_enable` (incrémenté quand `settings_visit_at` est posé et que le retour se fait sans activation) : c'est la seule façon, sans télémétrie (pas d'INTERNET, choix assumé), de vérifier auprès des bêta-testeurs si l'interstitiel (point 4) réduit réellement l'abandon.
12. **Relever Play Console à J+7 et J+30** : si « premières ouvertures » reste sous ~5 % des acquisitions une fois le pic TV digéré, le levier prioritaire est la fiche Play Store (bloc A), pas l'app.

## Priorisation proposée

| # | Action | Impact | Effort | Où |
|---|--------|--------|--------|-----|
| 1 | Screenshot n°1 = instruction « Ouvrez l'app » | Très fort | Faible | Fiche Play Store |
| 4 | Interstitiel avec capture réelle de l'avertissement | Fort | Moyen | App |
| 3 | Description courte : « Installez puis OUVREZ » | Fort | Très faible | Fiche Play Store |
| 8 | Textes 16 sp+, une instruction par carte | Moyen | Faible | App |
| 6 | Pictogramme Retour | Moyen | Faible | App |
| 11 | Jalon local d'abandon à l'avertissement | Moyen (mesure) | Faible | App |
| 2 | Vidéo promo 30 s | Fort | Moyen (action utilisateur) | Fiche Play Store |
| 5 | Captures par constructeur (Samsung/Xiaomi) | Moyen | Moyen | App |
| 7 | Surlignage de la ligne IME dans Settings | Faible/bonus | Faible | App |
| 9 | Mode pas à pas plein écran | Moyen | Élevé | App |
| 10 | Guidage audio kréyòl | Moyen | Élevé (validation humaine) | App |

Lecture recommandée : traiter 1, 3 et 4 d'abord. Les deux premiers ne demandent aucune ligne de code et s'attaquent au segment le plus nombreux (ceux qui n'ouvrent jamais l'app) ; le troisième cible exactement le comportement décrit (« s'arrête au premier warning »).
