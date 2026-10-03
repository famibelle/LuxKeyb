# Politique de Confidentialité - Lëtzebuergesch Clavier

**Date d'entrée en vigueur :** 19 septembre 2025  
**Dernière mise à jour :** 2 octobre 2026  
**Version :** 3.0 (dictée vocale)  
**Application :** Lëtzebuergesch Clavier  
**Nom du package :** com.potomitan.luxkeyboard

---

## 🏛️ Informations sur le Développeur

**Nom du développeur :** Famibelle Médhi  
**Adresse :** Luxembourg, Grand-Duché de Luxembourg  
**Email de contact :** medhi.famibelle@gmail.com  
**URL de la politique de confidentialité :** https://famibelle.github.io/LuxKeyb/privacy/privacy-policy.html

**Lëtzebuergesch Clavier** est un projet indépendant dédié à la préservation et à la promotion de la langue luxembourgeoise. Sa mission est de développer des outils technologiques qui facilitent l'usage et l'apprentissage des langues régionales et minoritaires, en particulier le luxembourgeois (lëtzebuergesch).

---

## 📱 À propos de l'Application

**Lëtzebuergesch Clavier** est un clavier virtuel pour appareils Android qui permet de saisir du texte en luxembourgeois avec des suggestions intelligentes et des corrections automatiques.

> **En une phrase :** ce que vous tapez ne quitte jamais votre téléphone. La seule exception est la **dictée vocale** : quand vous appuyez sur le micro, votre voix est envoyée au service **LuxASR de l'Université du Luxembourg**, qui la transforme en texte sans la conserver. Si vous n'appuyez jamais sur le micro, rien ne part. Tous les détails sont dans la section [🎙️ La dictée vocale](#dictee-vocale).

### Fonctionnalités principales :
- Clavier virtuel optimisé pour le luxembourgeois
- Suggestions de mots basées sur un dictionnaire luxembourgeois (38 442 mots tirés du corpus, 123 297 formes reconnues avec le dictionnaire officiel LOD)
- Corrections orthographiques automatiques
- Support des diacritiques luxembourgeoises (ë, ä, é, ü, ö)
- Interface en français et luxembourgeois
- Dictée vocale en luxembourgeois, facultative, assurée par le service LuxASR de l'Université du Luxembourg
- Système de gamification avec 8 niveaux pour encourager l'apprentissage
- **100% gratuit :** Aucune fonctionnalité premium, aucun achat intégré
- **Open Source :** Code source disponible sur GitHub
- **Éducatif :** Promotion de la langue luxembourgeoise
- **Respect de la vie privée :** Aucune collecte de données. Le clavier fonctionne hors ligne ; seule la dictée vocale a besoin d'Internet

---

## 🔒 Résumé Data Safety Google Play

**Cette section remplit les exigences de sécurité des données du Google Play Store et fournit un aperçu complet de nos pratiques.**

### ✅ Résumé de la Collecte de Données

**Nous, éditeur de l'application, ne recevons aucune donnée personnelle.** Ce que vous tapez n'est ni collecté, ni partagé, ni transmis. **Une seule donnée quitte le téléphone : votre voix, quand vous utilisez la dictée.** Elle part directement vers l'Université du Luxembourg pour être transcrite, sans passer par nous (voir [🎙️ La dictée vocale](#dictee-vocale)).

| Catégorie de Données | Collectée ? | Partagée ? | Objectif | Détails |
|----------------------|-------------|------------|----------|---------|
| **Localisation** | ❌ Non | ❌ Non | S.O. | Aucun accès à la localisation de l'appareil |
| **Informations personnelles** (Nom, Email, Adresse, etc.) | ❌ Non | ❌ Non | S.O. | Aucun identifiant personnel collecté |
| **Informations financières** | ❌ Non | ❌ Non | S.O. | Aucune information de paiement |
| **Santé et Fitness** | ❌ Non | ❌ Non | S.O. | Aucune donnée de santé |
| **Messages** | ❌ Non | ❌ Non | S.O. | Le clavier ne stocke ni ne transmet le contenu tapé |
| **Photos et Vidéos** | ❌ Non | ❌ Non | S.O. | Aucun accès aux médias |
| **Audio : enregistrements vocaux** | ⚠️ Seulement quand vous dictez | ⚠️ Université du Luxembourg | Fonctionnalité de l'app (dictée) | Envoyée en direct, chiffrée, traitée sur le moment et non conservée ; jamais reçue par nous |
| **Fichiers et Documents** | ❌ Non | ❌ Non | S.O. | Aucun accès aux fichiers |
| **Calendrier** | ❌ Non | ❌ Non | S.O. | Aucun accès au calendrier |
| **Contacts** | ❌ Non | ❌ Non | S.O. | Aucun accès aux contacts |
| **Activité dans l'app** (Saisie, Recherche) | ❌ Non | ❌ Non | S.O. | Aucun historique de frappe ou analytics |
| **Navigation Web** | ❌ Non | ❌ Non | S.O. | Aucune donnée de navigation |
| **Infos et Performance app** | ⚠️ Automatique | ⚠️ Google Play | Stabilité | Rapports de plantage gérés par Google Play Console |
| **Identifiants appareil** | ❌ Non | ❌ Non | S.O. | Aucun suivi d'appareil |

**Note sur les rapports de plantage :** Google Play Console peut collecter automatiquement des diagnostics de plantage anonymes pour améliorer la stabilité de l'application. C'est une fonctionnalité standard de Google Play hors de notre contrôle. Nous n'avons accès à aucune information personnellement identifiable de ces rapports.

### 🔐 Pratiques de Sécurité des Données

- **Chiffrement en transit :** Oui pour la seule transmission existante : la voix dictée part en connexion chiffrée (TLS) vers `luxasr.uni.lu`
- **Chiffrement au repos :** Non applicable (aucun stockage de données sur nos serveurs)
- **Suppression des données :** Non applicable (aucune donnée collectée à supprimer)
- **Privacy by Design :** L'architecture de l'app empêche toute collecte de données par conception
- **Fonctionnement hors ligne :** La frappe, les suggestions, le correcteur et les jeux fonctionnent entièrement sans Internet. Seule la dictée vocale en a besoin
- **Aucun suivi tiers :** Pas d'analytics, pas de SDK publicitaires
- **Aucun serveur backend :** Nous n'avons aucune infrastructure collectant ou stockant des données utilisateur. La dictée est traitée par le serveur de l'Université du Luxembourg, pas par un serveur à nous
- **Sandbox Android :** L'app fonctionne dans le bac à sable Android avec permissions restreintes
- **Audit du code :** Code open source disponible pour examen de sécurité

### 🛡️ Services et SDK Tiers

**Nous n'utilisons qu'un seul service tiers, et uniquement pour la dictée vocale : LuxASR, de l'Université du Luxembourg.** Il ne reçoit que votre voix, et seulement quand vous appuyez sur le micro. Aucun autre service tiers n'est utilisé. Spécifiquement :

- ❌ Pas de services d'analytics (Google Analytics, Firebase Analytics, etc.)
- ❌ Pas de réseaux publicitaires (AdMob, Facebook Audience Network, etc.)
- ❌ Pas de services de rapports de plantage au-delà du système intégré de Google Play
- ❌ Pas de services de stockage cloud
- ❌ Pas de SDK de réseaux sociaux
- ❌ Pas de services d'authentification utilisateur
- ❌ Pas de SDK de traitement de paiement
- ❌ Pas de services de localisation

**Les seules interactions tierces sont :**
- **LuxASR (Université du Luxembourg)**, pour transcrire votre voix quand vous dictez
- **Google Play Services**, pour la distribution et les mises à jour de l'application, et les rapports de plantage automatiques (anonymes, gérés par Google)

Les rapports de plantage sont standard pour toutes les applications Google Play et échappent à notre contrôle.

### 👤 Contrôle et Droits de l'Utilisateur

Les utilisateurs ont un contrôle total sur l'application et leurs données :

- **Contrôle d'installation :** Contrôle total sur l'installation/désinstallation
- **Gestion des permissions :** Peut révoquer toute permission à tout moment via les paramètres Android
- **Réinitialisation des paramètres :** Peut effacer toutes les préférences via les paramètres Android
- **Aucun compte requis :** Aucune création de compte, connexion ou inscription nécessaire
- **Désinstaller = Suppression complète :** La désinstallation supprime toutes les données de l'app de l'appareil
- **Pas de sync cloud :** Vos données de frappe, réglages et progression ne quittent jamais votre appareil, rien n'est stocké à distance
- **Mode hors ligne :** Le clavier fonctionne sans Internet ; seule la dictée vocale en a besoin
- **Dictée sous votre contrôle :** Rien n'est envoyé tant que vous n'appuyez pas sur le micro. Vous pouvez refuser l'accès au micro à Android, le clavier fonctionne alors exactement pareil, sans dictée

---

## 🎯 Notre Engagement de Confidentialité

**Lëtzebuergesch Clavier** respecte absolument votre vie privée. Nous croyons que :

1. **Vos données vous appartiennent :** Ce que vous tapez vous appartient et reste sur votre appareil. Votre voix ne part que si vous choisissez de dicter
2. **Aucune surveillance :** Nous ne suivons, ne surveillons ni n'analysons votre comportement de frappe
3. **Pas de monétisation des données :** Nous ne vendons pas de données (car nous n'en collectons aucune)
4. **Transparence :** Le code open source permet une vérification indépendante de nos affirmations
5. **Confidentialité par défaut :** Pas besoin de se désinscrire - la confidentialité est intégrée dès le départ
6. **Respect culturel :** La langue est profondément personnelle - nous protégeons votre intimité linguistique
7. **Focus éducatif :** Notre objectif est l'apprentissage, pas l'extraction de données

**Principe fondamental : Nous ne collectons AUCUNE donnée personnelle.**

Ce n'est pas qu'une politique - c'est ainsi que nous avons construit l'application. Pas de serveurs, pas de bases de données, pas d'infrastructure de suivi. La dictée vocale ne fait pas exception : votre voix va directement de votre téléphone à l'Université du Luxembourg, jamais chez nous.

---

## 📊 Collecte de Données - EXPLICATION DÉTAILLÉE

### ✅ Ce que nous NE collectons PAS :

Nous voulons être parfaitement clairs sur ce que nous **ne collectons pas** :

- **❌ Aucun texte saisi :** Rien de ce que vous tapez avec le clavier n'est collecté, stocké ou transmis
- **❌ Aucune information personnelle :** Pas de nom, email, téléphone, adresse ou identifiant
- **❌ Aucune donnée de localisation :** Pas de GPS, adresse IP, noms de réseaux Wi-Fi ou géolocalisation
- **❌ Aucun historique de frappe :** Pas de journal de ce que vous tapez, quand vous le tapez ou dans quelles apps
- **❌ Aucune analytics d'utilisation :** Pas de statistiques sur l'utilisation de l'app, des fonctionnalités ou du comportement
- **❌ Aucune information sur l'appareil :** Pas de modèle d'appareil, version OS ou spécifications matérielles collectés par nous
- **❌ Aucun contact :** Pas d'accès à vos contacts, carnet d'adresses ou numéros de téléphone
- **❌ Aucune photo/média :** Pas d'accès aux photos, vidéos, musique ou autres fichiers média
- **❌ Aucune donnée de navigation :** Pas de sites web visités, requêtes de recherche ou activité Internet
- **❌ Aucune donnée biométrique :** Pas d'empreintes digitales ni de scans faciaux. Votre voix n'est jamais enregistrée ni conservée par l'application ; quand vous dictez, elle est transmise en direct à l'Université du Luxembourg pour être transcrite (voir [🎙️ La dictée vocale](#dictee-vocale))
- **❌ Aucune information de paiement :** Pas de cartes de crédit, comptes bancaires, PayPal ou données financières
- **❌ Aucune donnée de réseaux sociaux :** Pas de Facebook, Twitter, Instagram ou autres comptes sociaux
- **❌ Aucune donnée du presse-papiers :** Nous ne surveillons ni ne collectons le contenu du presse-papiers
- **❌ Aucune donnée d'autres apps :** Nous n'accédons pas aux données d'autres applications installées
- **❌ Aucun ID publicitaire :** Pas de Google Advertising ID ou identifiants de suivi similaires

**En bref : Nous ne collectons RIEN qui vous identifie ou suit votre comportement.**

---

<a id="dictee-vocale"></a>
## 🎙️ La dictée vocale (LuxASR, Université du Luxembourg)

La dictée est la seule fonction de l'application qui envoie quelque chose hors du téléphone. Voici exactement quoi, quand, à qui, et ce qu'il en advient.

### Ce qui est envoyé

- **Uniquement votre voix**, sous forme de son (16 000 échantillons par seconde), pendant que le micro est ouvert.
- **Rien d'autre :** ni ce que vous avez tapé, ni le texte du champ, ni l'application dans laquelle vous écrivez, ni vos contacts, ni un identifiant de votre téléphone ou de votre compte.

### Quand

- **Seulement après un appui sur le micro** du clavier. Avant cet appui, le micro n'est pas ouvert et rien ne part.
- **Au premier appui, un écran explique où part votre voix**, avant même qu'Android ne demande l'accès au micro. Tant que vous n'avez pas répondu « J'accepte », rien n'est envoyé.
- **Jamais dans un champ de mot de passe :** le micro y refuse de s'ouvrir.
- Le micro se ferme de lui-même quand vous vous arrêtez de parler, ou quand vous appuyez à nouveau dessus. Une dictée dure au plus 90 secondes.
- Pendant toute la dictée, le clavier affiche « 🌐 LuxASR » : vous savez toujours quand votre voix est envoyée.
- Sans connexion Internet, le micro apparaît barré et rien n'est tenté.

### À qui

- Au service **LuxASR** de l'**Université du Luxembourg** (https://luxasr.uni.lu), qui transforme la parole luxembourgeoise en texte.
- **L'Université du Luxembourg est responsable de ce traitement.** Elle l'effectue dans le cadre de sa mission d'intérêt public (article 6, paragraphe 1, point e) du RGPD), sur un serveur situé dans son centre de calcul, au Luxembourg.
- La voix part **directement** de votre téléphone vers l'Université, en connexion chiffrée. **Nous ne la recevons jamais**, et le texte transcrit revient directement dans votre clavier.

### Ce qu'il en advient

Selon la notice d'information de l'Université du Luxembourg (https://luxasr.uni.lu) :

- l'audio est **traité sur le moment puis effacé** automatiquement, aucun fichier n'est conservé ;
- il n'est **pas utilisé pour entraîner** des modèles d'intelligence artificielle ;
- il n'est **transmis à aucun tiers** ;
- comme pour tout visiteur de ses services, la **date, l'heure et l'adresse IP** de la connexion sont journalisées par le service informatique de l'Université.

Sur votre téléphone, le clavier ne garde **ni le son ni une copie du texte** : le texte transcrit est simplement inséré dans le champ où vous écrivez, comme si vous l'aviez tapé.

### Comment ne pas utiliser la dictée

- **Ne touchez pas au micro :** rien n'est envoyé, le clavier reste entièrement hors ligne.
- **Répondez « Non merci »** à l'écran d'information du premier appui : rien n'est envoyé.
- **Refusez l'accès au micro** quand Android le demande au premier appui, ou retirez-le plus tard dans Paramètres > Applications > Lëtzebuergesch Clavier > Autorisations. Le clavier fonctionne alors exactement pareil, sans dictée.

### Vos droits sur la dictée

Comme l'Université du Luxembourg est responsable du traitement de la voix, c'est auprès d'elle que s'exercent les droits qui le concernent : déléguée à la protection des données de l'Université du Luxembourg, **dpo@uni.lu**, Maison du Savoir, 2, place de l'Université, L-4365 Esch-sur-Alzette. Nous restons joignables pour toute question : medhi.famibelle@gmail.com.

### 📱 Ce qui Reste sur Votre Appareil (Local Uniquement) :

Les données suivantes existent **uniquement sur votre appareil** et ne sont **jamais transmises à nous ou à quiconque** :

1. **Paramètres/Préférences de l'App :**
   - Votre choix de thème du clavier (schéma de couleurs)
   - Préférences de son et vibration
   - Personnalisations de la disposition
   - Paramètres de gamification
   - Stockés localement dans Android SharedPreferences (stockage Android standard)
   - Ne quittent jamais votre appareil

2. **Progression de Gamification :**
   - Nombre de mots appris (combien de mots du dictionnaire vous avez tapés)
   - Niveau actuel (Ufänker, Klengen, Fléisseg, Geschéit, Renert, Roude Léiw, Sproochenkënner ou Sproochenmeeschter)
   - Récompenses débloquées (ex: "100 premiers mots tapés")
   - Stockés localement dans la base de données de l'app
   - Non synchronisés avec le cloud ou d'autres appareils
   - Utilisés uniquement pour les fonctionnalités motivationnelles

3. **Utilisation de l'App (Local Uniquement) :**
   - Dernière fois que vous avez ouvert les paramètres de l'app
   - Si vous avez terminé le tutoriel d'intégration
   - Statistiques locales pour affichage dans l'app (ex: "Vous avez appris 250 mots !")
   - Jamais envoyées nulle part

**Toutes ces données sont automatiquement supprimées lorsque vous désinstallez l'app.** Il n'y a pas de données résiduelles sur nos serveurs car nous n'avons pas de serveurs. Aucune donnée de dictée n'est gardée sur le téléphone.

---

## 🔐 Protection et Sécurité des Données

### Comment Nous Protégeons Votre Vie Privée :

1. **Internet Seulement pour la Dictée :**
   - Le clavier fonctionne hors ligne - vous pouvez taper en mode avion
   - La seule connexion réseau de l'application est celle de la dictée, vers `luxasr.uni.lu`, ouverte quand vous appuyez sur le micro et fermée à la fin de la dictée
   - Aucune autre transmission de données à des tiers
   - Vous pouvez le vérifier : dans Paramètres > Applications > Lëtzebuergesch Clavier > Données mobiles et Wi-Fi, la consommation reste à zéro tant que vous ne dictez pas (le nom exact du menu varie selon les téléphones)

2. **Aucune Infrastructure Backend :**
   - Nous n'avons pas de serveurs pour stocker des données
   - Pas de bases de données collectant des informations
   - Pas de comptes de stockage cloud
   - Pas de points d'API à nous recevant des données

3. **Traitement Local Uniquement :**
   - Toutes les suggestions de mots sont générées localement sur votre appareil (seule la dictée est transcrite à distance, par l'Université du Luxembourg)
   - Le dictionnaire est stocké dans les ressources de l'app (intégré dans l'APK)
   - Les algorithmes d'autocorrection s'exécutent sur l'appareil
   - Aucun appel API externe pour les prédictions

4. **Sécurité Android :**
   - L'app fonctionne dans le bac à sable de sécurité Android (isolée des autres apps)
   - Suit les meilleures pratiques de sécurité Android
   - Mises à jour de sécurité régulières via Google Play
   - Aucune permission au-delà de ce qu'exigent le clavier et la dictée (voir ci-dessous)

5. **Transparence Open Source :**
   - Code source disponible sur GitHub : https://github.com/famibelle/LuxKeyb
   - N'importe qui peut vérifier nos affirmations de confidentialité en examinant le code
   - La communauté peut auditer pour des vulnérabilités de sécurité
   - Pas de code de suivi caché

6. **Aucune Journalisation des Frappes :**
   - Nous n'enregistrons pas les frappes (préoccupation courante avec les claviers)
   - Le texte tapé est immédiatement transmis à l'app cible et oublié
   - Aucun stockage temporaire du contenu tapé

### Politique de Conservation des Données :

**Période de conservation : 0 jour**

Puisque nous ne collectons aucune donnée au départ, il n'y a rien à conserver sur nos serveurs. La voix dictée, elle, est effacée par l'Université du Luxembourg après transcription.

**Conservation des Données Locales :**
- Préférences de l'app : Persistent jusqu'à effacement des données de l'app ou désinstallation
- Progression de gamification : Persiste jusqu'à effacement des données de l'app ou désinstallation
- **Pas d'expiration automatique** car les données ne quittent jamais votre appareil

---

## 📜 Autorisations Android Expliquées

Notre app demande les autorisations Android suivantes. Voici exactement pourquoi nous avons besoin de chacune et ce qu'elle **ne peut pas** faire :

### 1️⃣ BIND_INPUT_METHOD (Obligatoire)

**Ce qu'elle fait :** Permet à l'app de fonctionner comme méthode de saisie (clavier)  
**Pourquoi nous en avons besoin :** Sans cette autorisation de base, l'app ne peut pas fonctionner comme clavier  
**Impact sur la vie privée :** Aucun - c'est une autorisation au niveau système requise pour toutes les apps de clavier  
**Ce qu'elle NE PEUT PAS faire :** Cette autorisation ne donne PAS accès à votre contenu tapé  
**Contrôle utilisateur :** Vous activez ceci en sélectionnant notre clavier dans les paramètres Android (Paramètres > Système > Langues et saisie > Clavier à l'écran)

### 2️⃣ POST_NOTIFICATIONS (Optionnel, Android 13+)

**Ce qu'elle fait :** Permet d'afficher une notification locale quand vous passez un niveau de progression  
**Pourquoi nous en avons besoin :** Uniquement pour vous signaler une montée de niveau (ex : « Vous êtes maintenant Fléisseg ! »)  
**Impact sur la vie privée :** Aucun - la notification est générée sur l'appareil, sans connexion réseau ni notification push  
**Ce qu'elle NE PEUT PAS faire :** Ne peut pas lire vos autres notifications, ni recevoir de messages depuis un serveur  
**Contrôle utilisateur :** Peut être refusée à l'invite système ou révoquée dans Infos app > Notifications ; la gamification continue de fonctionner sans  
**Emplacement des données :** Aucune - rien n'est stocké ni transmis

### 3️⃣ VIBRATE (Automatique, sans invite)

**Ce qu'elle fait :** Permet de faire vibrer le téléphone avec une force et un timbre choisis  
**Pourquoi nous en avons besoin :** Pour qu'on sente le relief d'une carte du carnet en y passant le doigt : une montée et une descente ne vibrent pas de la même façon. Le clavier lui-même n'en a pas besoin et ne s'en sert pas  
**Impact sur la vie privée :** Aucun - elle ne donne accès à aucune donnée  
**Ce qu'elle NE PEUT PAS faire :** Ne peut rien lire ni rien transmettre ; elle ne fait que commander le vibreur  
**Contrôle utilisateur :** Le réglage « Vibration à la frappe » de l'application coupe toute vibration, clavier et carnet compris  
**Emplacement des données :** Aucune - rien n'est stocké ni transmis

### 4️⃣ RECORD_AUDIO (Demandée au premier appui sur le micro)

**Ce qu'elle fait :** Permet d'entendre votre voix pendant la dictée  
**Pourquoi nous en avons besoin :** Pour la dictée vocale, et pour rien d'autre  
**Impact sur la vie privée :** Le micro ne s'ouvre qu'après un appui sur le bouton micro du clavier, jamais dans un champ de mot de passe, et se referme à la fin de la dictée. Depuis Android 12, le système affiche en plus son propre témoin vert tant que le micro est ouvert  
**Ce qu'elle NE PEUT PAS faire :** Écouter en arrière-plan : le clavier n'ouvre le micro que pendant une dictée que vous avez lancée  
**Contrôle utilisateur :** Refusez-la à l'invite, ou retirez-la dans Infos app > Autorisations ; le clavier fonctionne alors sans dictée  
**Emplacement des données :** La voix est envoyée en direct à l'Université du Luxembourg pour transcription, jamais stockée sur le téléphone

### 5️⃣ INTERNET et ACCESS_NETWORK_STATE (Automatiques, sans invite)

**Ce qu'elles font :** INTERNET permet d'ouvrir la connexion de la dictée ; ACCESS_NETWORK_STATE permet de savoir si un réseau est disponible  
**Pourquoi nous en avons besoin :** Uniquement pour la dictée vocale. Sans réseau, le micro apparaît barré au lieu de vous faire attendre pour rien  
**Impact sur la vie privée :** La seule connexion ouverte est celle de la dictée, vers `luxasr.uni.lu`, quand vous appuyez sur le micro. Ni la frappe, ni les suggestions, ni le correcteur, ni les jeux n'utilisent le réseau  
**Ce qu'elles NE PEUVENT PAS faire :** Envoyer ce que vous tapez : le code qui gère la frappe ne contient aucun envoi réseau, ce que le code source public permet de vérifier  
**Contrôle utilisateur :** Ne pas utiliser la dictée suffit : aucune connexion n'est alors ouverte  
**Emplacement des données :** Aucune donnée n'est envoyée en dehors de la voix dictée

### ❌ Autorisations que Nous NE Demandons PAS :

Nous ne demandons **intentionnellement pas** les autorisations courantes suivantes :

- **ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION :** Pas de suivi de localisation
- **CAMERA :** Pas de capture photo/vidéo
- **WRITE_USER_DICTIONARY :** Pas d'écriture dans le dictionnaire personnel Android
- **READ_CONTACTS / WRITE_CONTACTS :** Pas d'accès aux contacts
- **READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE :** Pas d'accès aux fichiers
- **READ_PHONE_STATE :** Pas de collecte d'ID d'appareil
- **GET_ACCOUNTS :** Pas d'informations de compte
- **READ_SMS / SEND_SMS :** Pas d'accès aux SMS
- **READ_CALL_LOG :** Pas d'historique d'appels
- **BLUETOOTH / NFC :** Pas de collecte de données sans fil

**À propos de l'autorisation INTERNET :** les versions précédentes ne la demandaient pas du tout. Elle n'est arrivée qu'avec la dictée vocale, et ne sert qu'à elle : ce que vous tapez ne passe jamais par le réseau.

---

## 🤝 Partage de Données et Tiers

### Partage avec des Tiers :

**Nous partageons ZÉRO donnée avec des tiers** car nous collectons zéro donnée. La seule chose qui quitte votre téléphone est votre voix, quand vous dictez, et elle va directement à l'Université du Luxembourg pour être transcrite (voir [🎙️ La dictée vocale](#dictee-vocale)).

Soyons explicites sur ce que nous **ne faisons pas** :

- ❌ **Aucune donnée vendue aux annonceurs** (pas de publicités dans l'app)
- ❌ **Aucune donnée partagée avec des entreprises d'analytics** (pas de SDK d'analytics)
- ❌ **Aucune donnée fournie aux courtiers en données**
- ❌ **Aucune donnée partagée avec des plateformes de réseaux sociaux** (pas de SDK Facebook, etc.)
- ❌ **Aucune donnée envoyée à des services cloud** (pas de Firebase, AWS, etc.)
- ❌ **Aucune donnée partagée avec d'autres apps** sur votre appareil
- ❌ **Aucune donnée partagée avec une société mère** (nous sommes indépendants)
- ❌ **Aucune donnée partagée à des fins de recherche** (l'Université du Luxembourg déclare ne pas utiliser les voix dictées pour entraîner ses modèles)
- ❌ **Aucune donnée agrégée/anonymisée partagée** (nous n'avons pas de données à agréger)

### Transferts d'Entreprise :

Dans le cas improbable où l&#39;application serait cédée à une autre organisation ou fusionnerait avec elle :
- Nous n'aurions toujours aucune donnée utilisateur à transférer
- Le nouveau propriétaire serait lié par cette politique de confidentialité
- Les utilisateurs seraient notifiés de tout changement de politique

### Demandes Légales :

**Nous ne pouvons pas nous conformer aux demandes de données car nous n'avons pas de données à fournir.**

- Assignations gouvernementales : Nous n'avons pas de données utilisateur à remettre
- Ordonnances judiciaires : Nous ne pouvons pas fournir ce que nous ne collectons pas
- Demandes des forces de l'ordre : Aucune donnée utilisateur n'existe dans nos systèmes
- La voix dictée n'est pas conservée par l'Université du Luxembourg ; ses journaux de connexion relèvent de sa propre politique

Nous notifierions les utilisateurs si nous changions un jour cette architecture (ce que nous ne prévoyons pas de faire).

---

## 👶 Âge Minimum et Vie Privée des Mineurs

**Lëtzebuergesch Clavier s'adresse aux personnes de 16 ans et plus.**

La raison tient à la dictée vocale : quand on l'utilise, la voix est transmise à un tiers, l'Université du Luxembourg. Seize ans est l'âge à partir duquel la loi luxembourgeoise permet à un mineur de consentir seul à un service en ligne (article 8 du RGPD). Le reste de l'application ne collecte rien, à aucun âge.

- **Aucune publicité :** Pas de pubs, pas de marketing ciblé, pas de profilage comportemental
- **Aucun achat intégré :** Complètement gratuit sans invite de paiement
- **Aucune création de compte :** Pas d'email, nom d'utilisateur ou mot de passe requis
- **Aucune fonction sociale :** Pas de chat, de forum ni de classement en ligne

### Pour les Parents :

Si un mineur de moins de 16 ans utilise le téléphone, vous pouvez lui laisser le clavier en retirant l'accès au micro dans Paramètres > Applications > Lëtzebuergesch Clavier > Autorisations : la dictée est alors impossible et plus rien ne quitte le téléphone.

---

## 🌍 Transferts Internationaux de Données

**Aucun transfert hors de l'Union européenne.** Nous ne collectons aucune donnée. La voix dictée est traitée par l'Université du Luxembourg sur son propre serveur, au Luxembourg.

Pour la transparence légale :
- **Localisation du développeur :** Luxembourg (État membre de l'Union européenne, le RGPD s'applique)  
- **Public visé :** Luxembourg (Union européenne)
- **App distribuée via :** Google Play Store (distribution mondiale)
- **Localisation des données utilisateur :** Uniquement sur l'appareil de l'utilisateur ; la voix dictée est traitée au Luxembourg, puis effacée
- **Conformité RGPD :** Entièrement conforme au Règlement Général sur la Protection des Données de l'UE
- **Conformité CCPA :** Entièrement conforme à la loi californienne sur la protection de la vie privée des consommateurs
- **Autres réglementations :** Conforme aux lois de confidentialité dans le monde (LGPD, PIPEDA, POPIA, PDPA, etc.)

La dictée vocale est la seule fonction qui passe par un serveur. Elle a été introduite avec les engagements que cette politique prenait d'avance :
- Les utilisateurs en sont informés, ici et dans l'application
- Cette politique de confidentialité a été mise à jour avant sa publication
- Rien n'est envoyé sans un geste de votre part (l'appui sur le micro)
- Le serveur est dans l'Union européenne, au Luxembourg, chez un établissement public soumis au RGPD
- La connexion est chiffrée

---

## ⏱️ Conservation et Suppression des Données

### Politique de Conservation :

**Nous ne conservons pas les données utilisateur**

Puisqu'aucune donnée n'est collectée sur nos serveurs, il n'y a rien à conserver, sauvegarder ou archiver.

**Dictée vocale :** la voix est effacée par l'Université du Luxembourg aussitôt transcrite. La date, l'heure et l'adresse IP de la connexion figurent dans les journaux de son service informatique, selon ses propres règles de conservation.

### Données Locales sur Votre Appareil :

Les données suivantes persistent sur votre appareil tant que l'app est installée :

- **Préférences de l'App :** Stockées jusqu'à ce que vous effaciez les données de l'app ou désinstalliez
- **Progression de Gamification :** Stockée jusqu'à ce que vous effaciez les données de l'app ou désinstalliez

### Comment Supprimer Vos Données :

**Option 1 : Effacer les Préférences de l'App (Garder l'App Installée)**
1. Allez dans : Paramètres Android > Apps
2. Sélectionnez : Lëtzebuergesch Clavier
3. Appuyez sur : Stockage > Effacer les données
4. Confirmez la suppression
5. Ceci réinitialise tous les paramètres et la progression de gamification

**Option 2 : Suppression Complète (Désinstaller l'App)**
1. Allez dans : Paramètres Android > Apps > Lëtzebuergesch Clavier
2. Appuyez sur : Désinstaller
3. Ou : Google Play Store > Installé > Lëtzebuergesch Clavier > Désinstaller
4. Toutes les données de l'app sont immédiatement supprimées de votre appareil

**Option 3 : Réinitialisation d'Usine de l'Appareil**
- Option nucléaire : Réinitialise l'appareil entier et supprime toutes les apps

**Note sur les Sauvegardes Cloud :**
Si vous avez activé la sauvegarde Android (Paramètres > Google > Sauvegarde), Google peut sauvegarder :
- Les préférences de l'app
Ceci est contrôlé par la politique de confidentialité de Google, pas la nôtre. Pour empêcher cela :
- Paramètres > Google > Sauvegarde > Désactiver "Sauvegarder sur Google Drive"

**Note :** Il n'y a pas de compte ou de données cloud à supprimer puisque nous n'utilisons pas de stockage cloud.

---

## 🛡️ Vos Droits (RGPD, CCPA et Autres Lois sur la Vie Privée)

Même si nous ne collectons aucune donnée, nous respectons tous les droits à la vie privée et voulons que vous sachiez ce qu'ils sont. **Pour la voix dictée, dont l'Université du Luxembourg est responsable, ces droits s'exercent auprès de sa déléguée à la protection des données : dpo@uni.lu.**

### Selon le RGPD (Règlement Général sur la Protection des Données de l'UE) :

**Applicable à :** Résidents de l'UE et utilisateurs dans les territoires de l'UE (dont le Luxembourg)

1. **Droit d'Accès (Article 15) :** 
   - Vous pouvez demander l'accès à vos données personnelles
   - **Notre réponse :** Nous n'avons pas de données personnelles à fournir

2. **Droit de Rectification (Article 16) :** 
   - Vous pouvez corriger des données inexactes
   - **Notre réponse :** Pas de données à corriger ; vous pouvez modifier les données locales sur votre appareil

3. **Droit à l'Effacement / "Droit à l'Oubli" (Article 17) :** 
   - Vous pouvez demander la suppression de vos données
   - **Notre réponse :** Désinstallez l'app pour supprimer les données locales

4. **Droit à la Portabilité des Données (Article 20) :** 
   - Vous pouvez exporter vos données dans un format lisible par machine
   - **Notre réponse :** Aucune donnée personnelle à exporter ; vos préférences et votre progression restent locales sur l'appareil

5. **Droit d'Opposition (Article 21) :** 
   - Vous pouvez vous opposer au traitement des données
   - **Notre réponse :** Aucun traitement n'a lieu

6. **Droit de Retirer le Consentement (Article 7) :** 
   - Vous pouvez retirer le consentement à tout moment
   - **Notre réponse :** Révoquez les autorisations dans les paramètres Android ou désinstallez

7. **Droit de Déposer une Plainte (Article 77) :** 
   - Vous pouvez vous plaindre auprès d'une autorité de surveillance
   - **Contact :** CNPD (Luxembourg) : cnpd.public.lu

### Selon la CCPA (California Consumer Privacy Act) :

**Applicable à :** Résidents de Californie

1. **Droit de Savoir :** 
   - Vous pouvez demander quelles informations personnelles nous collectons
   - **Notre divulgation :** Nous ne collectons AUCUNE information personnelle

2. **Droit de Supprimer :** 
   - Vous pouvez demander la suppression des informations personnelles
   - **Notre réponse :** Désinstallez l'app (pas de données sur nos serveurs)

3. **Droit de Refuser la Vente :** 
   - Vous pouvez refuser la vente de données à des tiers
   - **Notre réponse :** Nous ne vendons pas de données (nous n'avons pas de données à vendre)

4. **Droit à la Non-Discrimination :** 
   - Nous ne discriminerons pas pour l'exercice de droits
   - **Notre réponse :** Non applicable (pas de collecte de données, pas de niveaux tarifaires)

5. **Droit de Corriger :**
   - Vous pouvez demander la correction de données inexactes
   - **Notre réponse :** Pas de données à corriger

### Selon d'Autres Lois sur la Vie Privée :

Nous nous conformons aux réglementations de confidentialité dans le monde, incluant :

- **LGPD** (Brésil - Lei Geral de Proteção de Dados)
- **PIPEDA** (Canada - Personal Information Protection and Electronic Documents Act)
- **Privacy Act 1988** (Australie)
- **POPIA** (Afrique du Sud - Protection of Personal Information Act)
- **PDPA** (Singapour, Thaïlande - Personal Data Protection Act)
- **KVKK** (Turquie - Personal Data Protection Law)
- **DPA** (Royaume-Uni - Data Protection Act 2018)

### Comment Exercer Vos Droits :

**Contactez-nous à :** medhi.famibelle@gmail.com  
**Objet :** "Demande de Droits à la Vie Privée - Lëtzebuergesch Clavier"  
**Inclure :** Votre type de demande (accès, suppression, etc.) et tous détails pertinents  
**Temps de réponse :** Dans les 30 jours (exigence RGPD) ou 45 jours (exigence CCPA)  

**Note :** Puisque nous ne collectons aucune donnée, la plupart des demandes recevront une réponse confirmant que nous n'avons pas de données vous concernant.

---

## 🎮 Gamification et Fonctionnalités d'Apprentissage

Notre app inclut des fonctionnalités de gamification optionnelles pour rendre l'apprentissage du luxembourgeois amusant et motivant :

### Comment Fonctionne la Gamification :

- **8 Niveaux :** Ufänker → Klengen → Fléisseg → Geschéit → Renert → Roude Léiw → Sproochenkënner → Sproochenmeeschter
- **Suivi de Progression :** Compte combien de mots luxembourgeois uniques vous avez tapés
- **Récompenses :** Débloque des badges pour des jalons (ex: "100 premiers mots")

### Quelles Données Sont Utilisées :

- **Nombre de Mots Appris :** Nombre de mots du dictionnaire uniques que vous avez tapés
  - Stocké : Localement sur votre appareil uniquement
  - Utilisé pour : Calculer votre niveau actuel
  - Partagé : Jamais

- **Niveau Actuel :** Votre progression à travers les 8 niveaux
  - Stocké : Localement dans la base de données de l'app
  - Utilisé pour : Afficher votre réussite dans les paramètres
  - Partagé : Jamais

- **Récompenses :** Badges que vous avez débloqués
  - Stocké : Localement comme drapeaux booléens
  - Utilisé pour : Affichage motivationnel
  - Partagé : Jamais

- **Date de Dernière Activité :** Quand vous avez utilisé le clavier pour la dernière fois
  - Stocké : Localement
  - Utilisé pour : gamification interne uniquement
  - Partagé : Jamais

### Garanties de Confidentialité :

- ❌ **Pas de classements** (pas de partage de données avec d'autres utilisateurs)
- ❌ **Pas de compétition en ligne** (pas de fonctionnalités multijoueur)
- ❌ **Pas de fonctionnalités sociales** nécessitant un téléchargement de données
- ❌ **Pas de partage sur les réseaux sociaux**
- ✅ **Suivi de progression 100% local**
- ✅ **Aucun compte requis**
- ✅ **La progression se réinitialise si vous effacez les données de l'app** (pas de sauvegarde cloud)


---

## 📞 Informations de Contact et Support

### Pour les Questions sur la Vie Privée :

**Contact Principal :**  
**Email :** medhi.famibelle@gmail.com  
**Objet :** "Question Vie Privée - Lëtzebuergesch Clavier"  


**Délégué à la Protection des Données (DPO) :**  
**Email :** medhi.famibelle@gmail.com  
**Basé à :** Luxembourg, Grand-Duché de Luxembourg (UE)  
**Responsable de :** Conformité RGPD, demandes de confidentialité

### Pour le Support Général :

**Email :** medhi.famibelle@gmail.com  
**GitHub Issues :** https://github.com/famibelle/LuxKeyb/issues  
**Documentation :** https://github.com/famibelle/LuxKeyb/blob/main/README.md

### Pour les Demandes de Fonctionnalités :

**GitHub Discussions :** https://github.com/famibelle/LuxKeyb/discussions  
**Email :** medhi.famibelle@gmail.com avec objet "Demande de Fonctionnalité"

### Support Linguistique :

Nous répondons aux demandes en :
- **Français**
- **Lëtzebuergesch** (Luxembourgeois)
- **English** (Anglais)

### Procédure de Plainte :

Si vous avez une préoccupation de confidentialité :
1. **Contactez-nous d'abord :** medhi.famibelle@gmail.com (nous répondrons dans les 7 jours)
2. **Escaladez à l'autorité de surveillance** si insatisfait :
   - **Résidents UE :** CNPD (Luxembourg) - cnpd.public.lu
   - **Résidents US :** FTC - www.ftc.gov
   - **Autres :** Votre autorité locale de protection des données

---

## 🔄 Modifications de Cette Politique de Confidentialité

### Comment Nous Mettons à Jour Cette Politique :

Nous pouvons mettre à jour cette politique pour refléter :
- Changements dans les exigences légales
- Nouvelles fonctionnalités (si elles affectent la confidentialité)
- Retours d'utilisateurs et améliorations de clarté
- Mises à jour de services tiers (si jamais ajoutés)

### Contrôle de Version :

1. **Numéro de Version :** Chaque mise à jour incrémente la version (Actuelle : **3.0**)
2. **Date d'Entrée en Vigueur :** Mise à jour en haut de ce document
3. **Historique des Changements :** Disponible sur notre dépôt GitHub
4. **Changements Importants :** Seront mis en évidence dans les annonces de mise à jour

### Notification des Changements :

**Pour des changements mineurs (fautes de frappe, clarifications) :**
- Politique de confidentialité mise à jour publiée sur le site web
- Numéro de version incrémenté
- Aucune notification active

**Pour des changements importants (nouvelle collecte de données, nouvelles fonctionnalités) :**
- Notification dans l'app au prochain lancement
- Email aux utilisateurs (si nous avions des emails - nous n'en avons pas !)
- Préavis de 30 jours avant que les changements prennent effet
- Option de réviser les changements avant d'accepter

### Historique des Changements :

**Version 3.0 (2 octobre 2026) :**
- Arrivée de la dictée vocale en luxembourgeois, assurée par le service LuxASR de l'Université du Luxembourg
- Nouvelle section « La dictée vocale » : ce qui est envoyé, quand, à qui, ce qu'il en advient, comment s'en passer
- Nouvelles autorisations documentées : RECORD_AUDIO, INTERNET, ACCESS_NETWORK_STATE, toutes réservées à la dictée
- Tableau Data Safety : enregistrements vocaux, transmis à l'Université du Luxembourg seulement pendant une dictée
- Âge minimum fixé à 16 ans
- La promesse est précisée : ce que vous tapez ne quitte jamais le téléphone ; seule la voix dictée part, et seulement quand vous dictez
- S'applique à l'application depuis la version 33.0.0, la première version publique à embarquer la dictée

**Version 2.1 (25 août 2026) :**
- Identité du développeur : Famibelle Médhi, établi au Luxembourg
- Retrait de la marque et des coordonnées de l'ancien éditeur
- Autorité de contrôle et juridiction alignées sur le Luxembourg (CNPD)
- Mise à jour des niveaux de gamification (8 rangs luxembourgeois)
- Retrait du dictionnaire utilisateur : la fonctionnalité n'existe plus
- Permissions WRITE_USER_DICTIONARY et READ_EXTERNAL_STORAGE retirées de l'app
- Documentation de POST_NOTIFICATIONS (notifications de montée de niveau)

**Version 2.0 (10 novembre 2025) :**
- Réécriture complète pour la conformité Data Safety de Google Play
- Ajout du tableau récapitulatif complet de Data Safety
- Sections étendues sur les droits RGPD/CCPA/lois sur la vie privée
- Ajout de la divulgation détaillée des services tiers (aucun utilisé)
- Section améliorée sur la vie privée des enfants (COPPA/RGPD-K)
- Ajout des procédures détaillées de conservation et suppression des données
- Clarification de toutes les autorisations Android avec langage convivial
- Ajout des pratiques de sécurité des données et détails de chiffrement
- Expansion des informations de contact et procédures de plainte
- Ajout de la section sur la confidentialité de la gamification
- Amélioration de la lisibilité avec icônes emoji et tableaux
- Traduction des sections principales en français tout en maintenant la compatibilité
- Ajout de la section juridiction légale et résolution des litiges

**Version 1.0 (19 septembre 2025) :**
- Publication initiale de la politique de confidentialité
- Engagement principal : Zéro collecte de données
- Conformité RGPD de base
- Français uniquement

### S'Abonner aux Mises à Jour :

**Option : GitHub Watch**
- Visitez : https://github.com/famibelle/LuxKeyb
- Cliquez : Watch > Custom > Releases
- Soyez notifié de toutes les mises à jour

**Option : Flux RSS**
- Surveillez notre page de versions GitHub

---

## ⚖️ Juridiction Légale et Résolution des Litiges

### Loi Applicable :

Cette politique de confidentialité et tout litige en découlant sont régis par :

- **Droit Luxembourgeois :**
  - Code de la consommation
  - Loi du 1er août 2018 sur la protection des données
  - RGPD (mise en œuvre luxembourgeoise)

- **Droit de l'Union Européenne :**
  - RGPD (Règlement 2016/679)
  - Directive ePrivacy
  - Directive sur les droits des consommateurs

- **Réglementations Locales :**
  - Lois luxembourgeoises de protection des consommateurs

### Processus de Résolution des Litiges :

**Étape 1 : Résolution Informelle**
- Contactez-nous à : medhi.famibelle@gmail.com
- Nous travaillerons avec vous pour résoudre le problème
- Réponse dans les 7 jours ouvrables
- Effort de bonne foi pour trouver une solution

**Étape 2 : Médiation (résidents UE)**
- Utilisez la plateforme de résolution des litiges en ligne de l'UE : https://ec.europa.eu/consumers/odr
- Ou saisissez le Service national du Médiateur de la consommation (Luxembourg)

**Étape 3 : Plainte auprès de l'Autorité de Surveillance**
- **Luxembourg :** CNPD (Commission nationale pour la protection des données)
  - Site web : cnpd.public.lu
  - Adresse : 15, boulevard du Jazz, L-4370 Belvaux
  - Téléphone : +352 26 10 60 1

- **UE :** Contactez votre Autorité de Protection des Données locale
  - Liste : https://edpb.europa.eu/about-edpb/board/members_en

- **US :** Federal Trade Commission (FTC)
  - Site web : www.ftc.gov/complaint
  - Téléphone : 1-877-FTC-HELP

**Étape 4 : Action Légale**
- **Juridiction :** Tribunaux luxembourgeois
- **Lieu :** Luxembourg, Grand-Duché de Luxembourg
- **Loi Applicable :** Droit luxembourgeois et européen
- **Droit de Poursuivre :** Les utilisateurs conservent tous les droits légaux

### Limitation de Responsabilité :

Dans la mesure permise par la loi :
- Nous ne sommes pas responsables des violations de données (nous n'avons pas de données à violer)
- Le traitement de la voix dictée relève de l'Université du Luxembourg, qui en est responsable
- Responsabilité maximale limitée au montant payé pour l'app (0€, car gratuite)

Cela n'affecte pas vos droits statutaires en tant que consommateur.

---

## 🏆 Certifications et Conformité

Nous nous engageons aux plus hauts standards de confidentialité :

### Cadres de Confidentialité :

- ✅ **Conforme RGPD** (Règlement Général sur la Protection des Données de l'UE)
- ✅ **Conforme CCPA** (California Consumer Privacy Act)
- ✅ **Conforme LGPD** (loi brésilienne de protection des données)
- ✅ Principes de **Privacy by Design**
- ✅ Implémentation **Privacy by Default**

### Meilleures Pratiques de l'Industrie :

- ✅ Directives **OWASP Mobile Security**
- ✅ Conforme à la **Politique des Développeurs Google Play**
- ✅ **Meilleures Pratiques de Sécurité Android**
- ✅ **Transparence Open Source**

### Vérification Tierce :

- **Code Open Source :** Publiquement auditable sur GitHub
- **Aucun SDK Tiers :** Aucune dépendance à des services de suivi externes
- **Examen Communautaire :** Code examiné par la communauté open source

### Certifications Futures (Prévues) :

- Envisager de postuler pour le cadre successeur de **Privacy Shield** si pertinent
- Explorer la certification **ISO 27001** pour la sécurité des données (si nous agrandissons notre équipe)

---

## 📋 Résumé de la Politique de Confidentialité (TL;DR)

**Pour ceux qui veulent la version rapide :**

### Les Bases :
- 🔒 **Zéro collecte de données :** Nous ne collectons, ne stockons ni ne partageons AUCUNE donnée personnelle
- 📱 **Frappe 100% hors ligne :** Ce que vous tapez ne quitte jamais votre téléphone
- 🎙️ **Dictée vocale facultative :** Quand vous appuyez sur le micro, votre voix part à l'Université du Luxembourg pour être transcrite, puis est effacée
- 🚫 **Aucun suivi :** Pas d'analytics, pas de pubs, pas de SDK tiers
- 🎓 **App éducative :** Construite pour promouvoir l'apprentissage du luxembourgeois
- 🆓 **Complètement gratuite :** Pas d'achats intégrés, pas de fonctionnalités premium

### Ce Qui Arrive à Vos Données :
- ✅ **Ce que vous tapez :** Reste sur votre appareil, jamais envoyé
- ✅ **Votre voix :** Envoyée seulement quand vous dictez, transcrite par l'Université du Luxembourg, jamais conservée
- ✅ **Vos paramètres :** Stockés localement, jamais synchronisés
- ✅ **Votre progression :** Suivie localement pour la gamification, jamais partagée

### Vos Droits :
- ✅ **Contrôle total :** Gérez les autorisations, supprimez les données à tout moment
- ✅ **Aucun compte nécessaire :** Pas de connexion, inscription ou email requis
- ✅ **Suppression facile :** Désinstaller l'app = toutes les données disparues

### Conformité Légale :
- ✅ **Conforme RGPD** (loi de confidentialité UE)
- ✅ **Conforme CCPA** (loi de confidentialité Californie)
- ✅ **Âge minimum :** 16 ans
- ✅ **Open source :** Code publiquement vérifiable

### Contact :
- 📧 **Email :** medhi.famibelle@gmail.com
- 💻 **GitHub :** github.com/famibelle/LuxKeyb

**En résumé :** C'est le clavier le plus respectueux de la vie privée que vous pouvez utiliser. Nous l'avons construit ainsi intentionnellement.

---

## 📚 Ressources Supplémentaires

### En Savoir Plus :

- **Politique de Confidentialité Complète :** Vous la lisez !
- **Code Source de l'App :** https://github.com/famibelle/LuxKeyb
- **Guide Utilisateur :** https://github.com/famibelle/LuxKeyb/blob/main/README.md
- **FAQ :** https://github.com/famibelle/LuxKeyb/wiki/FAQ

### Éducation à la Vie Privée :

- **Site Officiel RGPD :** https://gdpr.eu
- **Site Officiel CNPD :** https://cnpd.public.lu
- **Privacy Rights Clearinghouse :** https://privacyrights.org
- **Electronic Frontier Foundation :** https://www.eff.org/issues/privacy

### Signaler des Problèmes de Sécurité :

Si vous découvrez une vulnérabilité de sécurité :
- **NE PAS** publier publiquement sur GitHub
- **Email :** medhi.famibelle@gmail.com avec objet "SÉCURITÉ"
- **Inclure :** Description détaillée, étapes pour reproduire, évaluation de l'impact
- **Réponse :** Dans les 48 heures
- **Divulgation :** Divulgation responsable coordonnée après correction

---

## ✅ Reconnaissance de l'Utilisateur

En installant et utilisant **Lëtzebuergesch Clavier**, vous reconnaissez que :

1. Vous avez lu et compris cette Politique de Confidentialité
2. Vous consentez aux pratiques décrites ici (qui sont essentiellement : nous ne faisons rien avec vos données)
3. Vous comprenez que le clavier fonctionne hors ligne et ne collecte aucune donnée personnelle, et que la dictée vocale, quand vous l'utilisez, envoie votre voix à l'Université du Luxembourg pour la transcrire
4. Vous pouvez révoquer les autorisations ou désinstaller à tout moment
5. Vous comprenez que cette politique peut être mise à jour, et vous serez notifié des changements importants

**Aucune signature ou consentement explicite requis** - utiliser l'app constitue l'acceptation.

---

## 🎯 Notre Promesse

Nous avons construit **Lëtzebuergesch Clavier** parce que nous aimons notre langue et notre culture - pas pour gagner de l'argent avec vos données.

**Notre engagement :**
- Ce que vous tapez ne quittera **JAMAIS** votre téléphone
- Nous ne collecterons **JAMAIS** vos données personnelles
- Nous ne vendrons **JAMAIS** de données utilisateur
- Nous n'ajouterons **JAMAIS** de publicités intrusives
- Nous garderons **TOUJOURS** l'app gratuite
- Nous respecterons **TOUJOURS** votre vie privée
- Nous fonctionnerons **TOUJOURS** de manière transparente

**Ce n'est pas qu'une politique - c'est notre mission.**

La langue est profondément personnelle et culturelle. Vous devriez pouvoir taper en luxembourgeois sans vous soucier de la surveillance, du suivi ou de l'exploitation des données.

C'est pourquoi nous avons construit ce clavier de la bonne manière : **La confidentialité d'abord, toujours.**

---

**Merci de soutenir la préservation du lëtzebuergesch avec Lëtzebuergesch Clavier** 🇱🇺

---

**FIN DE LA POLITIQUE DE CONFIDENTIALITÉ**
