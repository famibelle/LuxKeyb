# Céder une carte : de la main à la main, par deux codes QR

Note du 10 octobre 2026, écrite avant le code, comme
[`GAMIFICATION-CARNET.md`](GAMIFICATION-CARNET.md) et pour la même raison : à la
racine, elle ne déclenche pas le build APK.

---

## 1. Ce que veut le propriétaire

Une carte doit pouvoir **se céder** : elle quitte le carnet du donneur et
arrive dans celui d'un autre téléphone, comme une carte physique qui passe
d'une main à l'autre. Le partage en image (`PartageCarte`) montre une carte ;
la cession la **donne**.

## 2. Pourquoi de la main à la main, et pas par un lien

Une carte du carnet est une ligne de données, et une donnée se copie. Céder,
c'est garantir qu'elle ne se retrouve pas en double.

- **À distance (WhatsApp, lien)**, il faut un tiers qui garde la carte pendant
  le trajet et refuse un second retrait : sans lui, le même lien ouvert sur
  trois téléphones fait trois cartes. Ce tiers est un serveur, donc un
  hébergement, une politique de confidentialité réécrite (« rien ne quitte le
  téléphone »), une fiche de sécurité des données refaite et des questions
  RGPD. Écarté.
- **De la main à la main**, les deux téléphones sont côte à côte et peuvent
  se confirmer l'échange l'un à l'autre. Pas de serveur, rien sur Internet.
  Retenu par le propriétaire le 10 octobre 2026.

## 3. Le protocole : deux codes QR

1. Le **donneur** choisit « Céder » sur une carte ouverte, confirme, et son
   téléphone affiche le **code d'offre**.
2. Le **receveur** touche « Recevoir une carte » dans son carnet et scanne
   l'offre. La carte entre dans son carnet, et son téléphone affiche le
   **code de réception**.
3. Le donneur scanne la réception. La carte **quitte** son carnet.

Les deux codes sont du texte court, préfixé pour qu'aucun autre code QR ne soit
pris pour une carte :

```
LUXKEYB:O:1:<jeton>:<échéance>:<forme>[:<nombre>]     offre
LUXKEYB:R:1:<jeton>                                  réception
```

- `<jeton>` : 16 caractères hexadécimaux tirés au hasard par le donneur. C'est
  lui qui lie la réception à l'offre : le donneur ne retire la carte que sur
  la réception de **son** offre.
- `<échéance>` : l'heure (en secondes) après laquelle l'offre ne s'accepte
  plus, dix minutes après sa création. Elle empêche qu'une capture d'écran de
  l'offre serve plus tard.
- `<forme>` : le mot, et `<nombre>` pour un numéral de Zuelwuert, sans quoi la
  carte arriverait sans sa rareté.

### Ce que le protocole garantit, et ce qu'il ne garantit pas

- **Le receveur n'importe pas deux fois la même offre** : il garde les jetons
  déjà reçus.
- **Le donneur ne perd sa carte que sur une réception qui la concerne.**
- **Un échange interrompu entre les étapes 2 et 3** laisse la carte un instant
  des deux côtés : la carte a été tendue mais pas lâchée. Le donneur peut
  rescanner la réception tant que son écran d'offre est ouvert ; s'il le
  ferme, il garde sa carte. Accepté : c'est un jeu d'apprentissage, et le seul
  « gain » d'une triche est un mot de plus dans un carnet.
- **Une offre peut être fabriquée** par qui lit cette note : rien ne la signe,
  et une clé livrée dans l'APK se lirait. Le receveur refuse toutefois toute
  forme qui ne serait pas une carte possible (sans glose, ou écartée par
  `MotsEcartes`). Même raisonnement : l'enjeu ne vaut pas une infrastructure.

## 4. Ce qui voyage avec la carte

- **Le mot**, et la valeur du numéral pour Zuelwuert.
- **Rien de l'histoire du donneur** : ni le sceau, ni la plume, ni la boîte de
  révision, ni le compteur de rencontres. Apprendre un mot et l'avoir écrit
  appartiennent à celui qui l'a fait. Le receveur part de zéro sur ce mot.
- **Une provenance « Cadeau »** (🎁), qui s'ajoute aux sept jeux sans compter
  dans la série des sept jeux, comme la carte de bienvenue. Aucun nom : rien ne
  dit qui a donné la carte.

Si le receveur a **déjà** la carte, elle devient un doublon : son compteur de
rencontres monte et la provenance « Cadeau » s'ajoute. Le donneur la perd
quand même : c'est un cadeau.

## 5. Ce qui change dans le carnet

- **Le carnet ne fait plus que grandir.** C'était un invariant écrit ; il tombe
  par décision du propriétaire. Céder une carte de série rouvre la case chez le
  donneur, et c'est ce qui donne sa valeur à l'échange.
- **Le numéro de série devient stocké.** Il était dérivé de la position dans la
  liste ; retirer une carte aurait renuméroté toutes les suivantes. Il est
  écrit dans le blob (`k`), et les cartes déjà là reçoivent à la lecture le
  numéro qu'elles affichaient. Une carte reçue prend le numéro suivant du
  carnet du receveur : c'est son ordre d'entrée chez lui.
- **La plume d'une carte cédée est effacée** de `carnet_plumes.json` : si le mot
  revient un jour, il devra la regagner.

## 6. Lire et dessiner les codes

- **Dessiner** : `com.google.zxing:core`, l'encodeur seul, en pur Java.
- **Lire** : `com.journeyapps:zxing-android-embedded`, qui décode sur
  l'appareil. **L'appareil photo n'est demandé qu'au moment du scan**, jamais à
  l'installation. Le scanner de Google (ML Kit) éviterait cette autorisation,
  mais il contacte les serveurs de Google pour ses modèles et ses statistiques
  d'usage, ce que la politique de confidentialité exclut.
- **Pour tester sur l'émulateur**, qui n'a pas de vraie caméra, un build de
  debug lit le contenu à scanner dans `files/scan_debug.txt` s'il existe, au
  lieu d'ouvrir la caméra. Jamais en release (`BuildConfig.DEBUG`).

## 7. Ce qu'il reste à faire hors code

- **La politique de confidentialité** doit mentionner l'appareil photo :
  utilisé uniquement pour lire le code d'une carte, aucune image conservée ni
  envoyée.
- **La fiche Play Store** : l'autorisation caméra apparaît dans la liste des
  autorisations.
