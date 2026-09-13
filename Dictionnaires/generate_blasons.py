# -*- coding: utf-8 -*-
"""Le blason d'une carte du carnet : son champ, et le meuble des rares.

Produit `android_keyboard/app/src/main/assets/luxemburgish_blasons.json`, lu
par `carnet/Blason.kt`.

## Ce que ce fichier décide, et ce qu'il ne décide pas

Une carte du carnet porte trois informations dans sa fenêtre, de trois sources
différentes :

- **la matière** vient du palier de rareté, qui est le rang de fréquence : rien
  ici ne la touche ;
- **la partition** vient de la nature du mot, que le luxembourgeois écrit dans
  sa majuscule : elle se calcule à l'exécution, sans donnée, et rien ici ne la
  touche non plus ;
- **le champ** — la teinte de la carte — vient du sens, et c'est *lui* que ce
  script établit, avec le meuble des quelques cartes enluminées.

## Pourquoi huit champs, et pas quatorze ni deux mille

L'exploration a mesuré ce qu'une bibliothèque de silhouettes amortit sur ce
carnet : **1,12 mot par dessin**, et 89 % des têtes de glose ne concernent
qu'un seul mot. Il n'y a pas de Pareto — la courbe de couverture colle à la
diagonale. Une image par mot est donc hors de portée, et c'était l'hypothèse
qui justifiait la piste.

Ce qui survit à la mesure, c'est l'étage du dessus. Un champ amortit *par
construction* : huit valeurs pour 2 800 emplacements, un facteur trois cent
cinquante, et pas un pixel d'actif. Huit et non quatorze parce qu'au-delà, deux
teintes voisines cessent de se distinguer sur une vignette de 160 dp — et parce
que les champs trop peu peuplés ne se voient jamais dans une grille.

## Comment le classement est fait

Deux passes, dans cet ordre, et la seconde gagne toujours :

1. **le lexique** — des mots-clés français cherchés dans la glose entière, le
   premier sens comptant triple parce que c'est celui que la carte met en
   avant. Une passe automatique ne peut pas faire mieux qu'approcher ;
2. **la main** — [MAIN], où sont tranchés les mots que le lexique manque ou
   range mal. C'est la relecture promise, et elle est *dans le fichier* plutôt
   que dans un tableur : une décision de classement doit se relire avec son
   motif.

Un mot que ni l'une ni l'autre ne rattache **n'est pas rangé de force**. Il
garde la teinte tirée de ses trois premières lettres, comme avant, et le carnet
n'y perd rien : c'est exactement l'état actuel de toutes les cartes.

## Le meuble n'est jamais attribué automatiquement

[MEUBLES] est écrit à la main, une ligne à la fois, et c'est une contrainte de
justesse et non de goût. La carte affiche **toute** la glose : « Wee — chemin,
sens, moyen ». Un dessin n'en illustre qu'un, et sur la face réponse d'un outil
de révision, une image qui tranche une polysémie est activement fausse. Chaque
entrée ci-dessous a donc été relue contre la glose complète, et les mots à
sens multiples n'en reçoivent pas — même quand le premier sens se dessinerait
très bien.

C'est le renversement que la mesure impose : on ne dessine plus ce qu'il
faudrait couvrir, on n'attribue que là où c'est juste.

Usage :
    python3 Dictionnaires/generate_blasons.py
"""

import collections
import json
import os
import re
import sys
import unicodedata

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ACTIFS = os.path.join(RACINE, 'android_keyboard', 'app', 'src', 'main', 'assets')
SORTIE = os.path.join(ACTIFS, 'luxemburgish_blasons.json')

# ---------------------------------------------------------------------------
# Les huit champs
# ---------------------------------------------------------------------------
# L'ordre départage les ex æquo : un mot qui touche autant l'économie que la
# chose publique est rangé dans le premier des deux cités. Il va donc du plus
# spécifique au plus attrape-tout.
CHAMPS = [
    'vie',         # Vie et corps
    'territoire',  # Territoire
    'mouvement',   # Mouvement
    'temps',       # Temps et mesure
    'economie',    # Économie
    'public',      # Chose publique
    'savoir',      # Savoir et parole
    'societe',     # Société
]

# Chaque champ : les mots français cherchés dans la glose. Un mot-clé peut
# être un nom, un verbe à l'infinitif ou un adjectif — les gloses du LOD sont
# des trois natures, et le champ est un domaine de sens, pas une catégorie
# grammaticale (celle-là est dans la partition, et elle est gratuite).
LEXIQUE = {

    'vie': '''
    corps tête main mains pied pieds bras jambe jambes doigt doigts oeil yeux
    oreille oreilles bouche nez dent dents cheveu cheveux coeur sang os peau dos
    ventre visage figure gorge langue épaule genou poumon estomac cerveau muscle
    nerf pouce lèvre lèvres menton front joue cou poitrine hanche coude cheville
    poignet ongle barbe crâne sein reins foie rate veine artère squelette
    santé malade maladie maladies hôpital clinique médecin docteur infirmier
    infirmière soin soins remède médicament traitement opération blessure blessé
    blesser accident douleur mal souffrir souffrance virus épidémie pandémie
    vaccin vaccination pharmacie thérapie handicap guérir guérison fièvre grippe
    cancer diagnostic patient chirurgie ordonnance symptôme contagion
    vie vivre vivant mort mourir décès naître naissance né enfance vieillesse
    âge âgé jeune vieux mortel survivre survie funérailles enterrement tombe
    corps cadavre respirer respiration dormir sommeil rêve rêver fatigue fatigué
    manger boire nourriture aliment repas faim soif pain viande lait beurre
    fromage soupe gâteau tarte biscuit sucre sel poivre farine riz pâtes pomme
    poire cerise fraise raisin prune noix légume légumes carotte chou salade
    oignon miel confiture chocolat bonbon saucisse jambon boisson bière vin café
    thé jus déjeuner dîner souper cuisiner cuire goût goûter savoureux
    animal animaux chien chienne chat chatte cheval vache boeuf mouton chèvre
    cochon porc poule coq oiseau poisson insecte abeille papillon araignée
    souris rat loup renard ours cerf lièvre lapin canard oie pigeon corbeau
    serpent grenouille mouche fourmi ver taureau veau agneau poulet gibier
    bétail troupeau race museau patte aile plume queue nid
    arbre arbres plante plantes fleur fleurs herbe feuille feuilles branche
    racine graine semence bois buisson haie chêne hêtre sapin pin pommier vigne
    blé seigle avoine orge maïs foin paille champignon mousse rose tulipe
    pousser fleurir mûr récolte semer planter
    ''',

    'territoire': '''
    maison maisons bâtiment bâtiments immeuble logement logements appartement
    chambre cuisine salon cave grenier toit mur murs porte fenêtre escalier
    étage jardin cour clôture ferme grange étable village villages ville villes
    quartier rue rues place places église chapelle château tour pont hôtel
    magasin boutique école hôpital gare usine bureau bureaux salle atelier
    garage cabane hall couloir terrasse balcon cheminée plancher plafond seuil
    habiter habitation résidence loger domicile foyer adresse
    pays région commune communal cantón canton frontière frontalier territoire
    terrain site zone secteur espace lieu endroit emplacement position situer
    campagne banlieue agglomération arrondissement circonscription district
    quartier province état parcelle propriété domaine
    montagne colline vallée plaine champ champs pré prairie rivière fleuve
    ruisseau lac étang mer océan plage île rocher pierre sable terre sol boue
    caillou grotte source rive côte falaise forêt bois nature paysage horizon
    ciel étoile lune soleil planète monde univers
    nord sud est ouest droite gauche haut bas dessus dessous devant derrière
    intérieur extérieur dehors dedans centre bord côté milieu coin
    pluie neige vent orage tempête nuage brouillard gel glace grêle météo
    climat température froid chaud chaleur éclair tonnerre averse dégel
    ''',

    'mouvement': '''
    aller venir partir arriver entrer sortir monter descendre passer traverser
    avancer reculer courir marcher sauter tomber glisser voler nager rouler
    tourner retourner revenir rentrer emmener amener apporter porter emporter
    envoyer expédier tirer pousser lever soulever poser jeter lancer attraper
    prendre saisir déplacer bouger remuer secouer trembler tirer traîner
    mouvement déplacement circulation trafic passage arrivée départ retour
    aller-retour trajet voyage voyager tour promenade excursion randonnée
    étape parcours itinéraire direction sens virage détour vitesse
    voiture auto camion bus autobus train tram vélo bicyclette moto avion
    bateau navire barque véhicule tracteur remorque wagon métro taxi ambulance
    roue moteur volant frein pneu carburant essence conduire conducteur
    chauffeur passager transport transporter livraison livrer route autoroute
    chemin sentier piste rail aéroport port station arrêt quai embouteillage
    ouvrir fermer ouverture fermeture tirer pousser franchir contourner suivre
    poursuivre fuir échapper chasser rattraper rejoindre approcher éloigner
    ''',

    'temps': '''
    heure heures minute seconde jour jours journée nuit matin soir midi minuit
    semaine mois année année an ans saison printemps été automne hiver siècle
    date calendrier horaire moment instant durée délai période époque ère
    aujourd'hui hier demain veille lendemain lundi mardi mercredi jeudi
    vendredi samedi dimanche janvier février mars avril mai juin juillet août
    septembre octobre novembre décembre weekend
    début commencement départ fin bout terme achèvement suite prochain dernier
    premier deuxième précédent suivant avant après pendant durant tôt tard
    bientôt longtemps toujours jamais souvent rare fréquent parfois encore déjà
    tempo rythme fréquence répétition tour fois
    nombre numéro chiffre compte compter calcul calculer total somme quantité
    montant nombreux plusieurs beaucoup peu moitié quart tiers double triple
    pourcentage pour-cent degré taux part partie portion morceau reste
    mesure mesurer taille dimension longueur largeur hauteur profondeur
    épaisseur poids peser kilo gramme tonne mètre kilomètre litre surface
    volume distance écart niveau échelle grandeur petit grand long court haut
    bas large étroit épais mince lourd léger plein vide augmenter diminuer
    ''',

    'economie': '''
    argent monnaie euro franc prix coût coûter salaire paie revenu gain
    bénéfice perte dépense recette impôt taxe dette crédit emprunt banque
    compte facture paiement payer achat acheter vente vendre marché commerce
    magasin client fournisseur richesse riche pauvre pauvreté trésor pièce
    billet caisse budget financement financier investissement bourse action
    capital économie économique
    travail travailler emploi employeur employé salarié ouvrier ouvrière
    entreprise société firme patron chef direction directeur cadre bureau
    métier profession poste fonction carrière chômage chômeur embauche
    licenciement grève syndicat contrat collègue équipe atelier chantier usine
    industrie production produire produit fabrication fabriquer machine outil
    marteau clou vis tournevis scie hache pelle bêche pioche râteau charrue
    faux échelle appareil pompe engrenage tuyau câble matériel matériau
    agriculture agriculteur paysan fermier récolte cultiver culture élevage
    laiterie vignoble
    construction construire bâtir maçon charpentier réparation réparer entretien
    service prestation clientèle commande livraison stock magasinage
    ''',

    'public': '''
    état gouvernement ministre ministère parlement député chambre sénat conseil
    conseiller mairie bourgmestre échevin commune communal administration
    fonctionnaire autorité pouvoir public officiel politique parti élection
    élire élu vote voter électeur campagne majorité opposition coalition
    référendum démocratie république monarchie royal grand-duc grand-duché
    duc duchesse nation national pays européen europe union traité ambassade
    ambassadeur diplomatie président chancelier roi reine souverain couronne
    loi législation légal illégal droit droits règle règlement décret arrêté
    décision décider justice tribunal juge jugement juger procès avocat plainte
    accusé accusation témoin témoignage enquête enquêter police policier
    gendarme commissariat crime criminel délit vol voler voleur auteur victime
    amende prison peine condamnation condamner sanction contrôle contrôler
    surveillance sécurité protection interdire interdiction autoriser permis
    licence obligation obligatoire devoir responsabilité responsable
    guerre armée soldat militaire arme paix conflit attaque défense frontière
    pompier secours urgence catastrophe alerte
    ''',

    'savoir': '''
    mot mots parole parler dire langue langage phrase texte lettre courrier
    question demander demande réponse répondre nom appeler appellation titre
    histoire conte récit roman poème chanson chant musique musicien instrument
    livre lecture lire écriture écrire écrivain journal presse journaliste
    article reportage nouvelle nouvelles information informer communication
    communiquer message annonce annoncer publier publication radio télévision
    télé émission film cinéma internet site web réseau ordinateur téléphone
    appel discours conversation dialogue discussion discuter débat entretien
    interview déclaration déclarer expliquer explication signifier signification
    sens traduire traduction dictionnaire alphabet orthographe grammaire
    école élève écolier étudiant étudier professeur enseignant enseignement
    cours leçon classe étude formation apprendre apprentissage stage diplôme
    examen épreuve université lycée collège institut recherche chercheur
    science scientifique savoir connaissance connaître expérience essai
    bibliothèque bibliothécaire manuel cahier crayon stylo papier page
    pensée penser idée avis opinion croire croyance jugement raisonnement
    mémoire souvenir oublier comprendre compréhension exemple modèle méthode
    théorie preuve démonstration résultat conclusion problème solution résoudre
    ''',

    'societe': '''
    homme femme enfant enfants garçon fille bébé père mère parent parents fils
    frère soeur grand-père grand-mère oncle tante cousin cousine neveu nièce
    famille familial mariage marier époux épouse mari couple divorce amour aimer
    ami amie amitié voisin voisine gens personne personnes monsieur madame
    habitant citoyen population société social communauté groupe équipe membre
    association club organisation réunion assemblée rencontre visite invité
    fête fêter anniversaire noël pâques carnaval cérémonie tradition coutume
    culture culturel art artiste peinture tableau théâtre acteur spectacle
    concert exposition musée danse danser jeu jouer joueur sport match équipe
    football course compétition championnat entraînement loisir vacances repos
    religion église prêtre curé prière prier dieu saint messe foi croyant
    bonheur heureux malheur triste tristesse joie colère peur crainte espoir
    espérer confiance honte fierté fier plaisir envie désir sentiment émotion
    aide aider soutien soutenir solidarité charité bénévole don donner partager
    accueil accueillir hospitalité respect politesse gentil méchant honnête
    ''',
}


# La seconde vague, écrite en relisant les 825 substantifs que la première
# laissait sans champ. Elle est séparée pour qu'on voie ce que la relecture a
# coûté — et parce que ces mots-là ne sont pas du vocabulaire de base : ce sont
# ceux d'un corpus de presse luxembourgeoise, qu'aucune liste écrite d'avance
# n'aurait devinés.
RELECTURE = {

    'vie': '''
    alcool cannabis marijuana héroïne drogue dose surdose stupéfiant tabac
    cigarette pilule éthylotest infection inflammation coronavirus virus test
    quarantaine confinement autopsie intervention psychiatre psychologue
    secouriste ambulancier nourrisson adulte bébé poing pouls odeur odorat
    vue regard toucher ouïe sexe alimentaire denrée fruits fruit légume pizza
    bouillon kirsch quiche boeuf abruti lion chevreuil merle laie sanglier
    chasse gibier arbuste vendange raisin déchets ordures pesticide amiante
    poussière saleté euthanasie greffe don-du-sang canicule
    oeuf œuf lunettes lunette organe branchie nageur natation habits robe
    tenue vestimentaire vêtement vêtements jean casquette chaussure poche
    ''',

    'territoire': '''
    luxembourg belgique france allemagne suisse espagne portugal italie
    angleterre grande-bretagne russie ukraine israël iran syrie chypre égypte
    érythrée australie finlande singapour vietnam états-unis amérique europe
    bruxelles paris londres strasbourg metz trèves varsovie bâle
    esch-sur-alzette differdange dudelange ettelbruck diekirch echternach
    mersch bettembourg pétange mondercange schifflange hesperange bertrange
    mamer capellen clervaux wiltz remich vianden rodange belvaux niederkorn
    kirchberg belair hollerich gasperich howald findel merl cessange bonnevoie
    dommeldange beggen eich weimerskirch neudorf hamm cents itzig sandweiler
    munsbach schuttrange betzdorf grevenmacher wormeldange mertert wasserbillig
    redange useldange bissen colmar-berg mersch lintgen lorentzweiler walferdange
    steinsel heisdorf helmsange bereldange strassen kopstal kehlen olm keispelt
    roeser crauthem bivange berchem leudelange reckange mondorf-les-bains
    dalheim dahlem wellenstein schengen troisvierges hosingen reisdorf schieren
    roost minette sûre alzette moselle ardenne oesling gutland
    ville-ville quartier commune village bourg cité lieu-dit hameau
    fossé digue talus eaux rivière cours-d'eau bassin étang barrage
    façade halle gymnase piscine palais monument pavillon parquet parking
    tunnel avenue boulevard carrefour croisement voie chaussée trottoir
    campus crèche garderie restaurant bistrot brasserie boulangerie casino
    cabaret hôtellerie studio local siège tente cabinet toilettes réservoir
    tuyau robinet ascenseur chauffage miroir moulin banc tiroir coffre-fort
    lampe lumière phares table chaise lit porte fenêtre couteau lame bouteille
    biberon verre bocal assiette casserole poêle armoire meuble rideau tapis
    clé serrure trou poteau pilier grue échafaudage toiture patrimoine
    immobilier logement loyer locataire riverain résident ménage
    golfe continent capitale patrie casemate ardoise plaque tas cercle
    scène coque enceinte pôle
    ''',

    'mouvement': '''
    automobiliste cycliste motocycliste piéton passant conducteur chauffeur
    pilote capitaine camionnette minibus van quad hélicoptère embarcation
    atterrissage décollage collision accrochage patrouille radar circuit tracé
    sprint course-poursuite fuite poursuite trajet destination accès entrée
    sortie issue passage zigzag pas démarche retard avance transfert relance
    reprise come-back rentrée manœuvre déplacement livraison expédition
    station-service gazole mazout carburant essence pneu klaxon casque
    conduite panne stationnement ticket passeport laissez-passer piste
    toboggan glissoire fauteuil-roulant halte stop
    ''',

    'temps': '''
    agenda quotidien après-midi pause récréation entracte phase trimestre
    semestre étape échéance délai douzaine hectare cent milliard million
    maximum minimum moyenne limite plafond seuil tranche part portion
    proportion pourcentage indice index taux différence différend écart
    contraire opposé format forme volet détail élément composant ampleur
    excédent trop-plein manque défaut déficit désavantage avantage avance
    efficience qualité stabilité flexibilité présence existence subsistance
    hasard occasion chance exception norme standard statistique record
    rappel série tendance état statut condition modalité version variante
    changement renouvellement génération origine origines perspective
    priorité répartition adaptation attente suite reste
    ''',

    'economie': '''
    actionnaire assurance audit bilan comptable comptabilité concurrence
    consommation consommateur vendeur acheteur locataire loyer prêt emprunt
    placement profit bénéfice marge faillite déficit hausse baisse inflation
    subvention allocation prime forfait charge frais soldes marchandise
    fortune patrimoine ressource dépôt guichet agence filiale succursale
    compagnie airline brasserie boulangerie restauration gastronomie tourisme
    touriste hôtellerie grue acier mazout gazole pétrole gaz huile courant
    électricité énergie batterie pile réservoir station-service parc-automobile
    salarié salariée employée personnel collaborateur associé partenaire
    patronat gérant gérante cheffe management direction promoteur propriétaire
    maître-d'ouvrage exportation importation commande stock chantier
    boulot job mission poste candidature recrutement bénévolat placement
    bail cash garantie gestion finances bijou congé dédommagement
    développement aménagement cliente centrale nucléaire extraction
    ''',

    'public': '''
    pétition procédure directive dispositif motion scrutin vote recours
    résolution verdict acquittement infraction sursis condamnation procureur
    parquet perquisition mandat commission convention institution instance
    législature gouverneur commissaire douane douanier gendarmerie patrouille
    munitions meurtre assassin cambrioleur cambriolage attentat agression
    agresseur complice tromperie abus soupçon serment protocole norme
    réforme politicien socialiste candidat candidate délégation délégué
    déléguée adjoint nomination manifestation protestation immigration
    migration adoption euthanasie autonomie antisémitisme scandale sondage
    liste électorale confédération fédération pouvoir danger risque alerte
    secours pompier sécurité surveillance contrôle interdiction sanction
    amende poursuite plainte témoin enquête audition garde-à-vue
    acte lutte incendie explosion démission exigence drapeau constat
    pirate mineur médiateur monopole révolution crise barreau asile
    ''',

    'savoir': '''
    analyse aspect rapport description mode-d'emploi brochure briefing
    communiqué document donnée données dossier édition exposé feed-back
    fichier formulaire forum photo photographe intelligence chapitre catalogue
    catégorie concept contexte copie critique critère label lecteur
    littérature streaming logique mail média micro notion note philosophie
    pratique présentation principe profil rédaction publicité orateur rumeur
    section série vue point-de-vue slogan statistique studio sujet thématique
    thème son ton voix partition citation signe comparaison version vidéo
    secret technologie application plateforme portable smartphone utilisateur
    hacker lien antenne caméra master étudiante éducateur éducation historien
    juriste expert expertise concours exercice matière spécialité casier
    formation apprentissage stage diplôme examen conférence colloque
    accent archives base approche introduction échange réserve fait
    design confirmation choix guide système impression imprimé style
    tabou marque image innovation initiative compétence conséquence
    contact possibilité proposition réaction vision succès échec plan
    planification projet stratégie transparence réalité alternative
    ''',

    'societe': '''
    ambiance soirée festival foire casino cabaret show star chanteur joueuse
    sportif sportive entraîneur coach arbitre judo karaté golf rock tournoi
    finale victoire ligue leader fan junior senior gars type soeur ménage
    bénévolat militant fédération jury congrès duo ange archevêque saint
    haine courage panique stress souci misère chagrin souhait voeu passion
    remerciement reproche dispute bagarre combat duel agitation troubles
    entente accord reconnaissance dignité tolérance intention comportement
    fréquentation réputation surprise inquiétude tracas doute
    américain belge italien luxembourgeoise allemand français portugais
    événement cérémonie invitation entrevue rendez-vous réception visiteur
    réunion assemblée sommet délégation compagnon
    affaire influence activité effort engagement force caractère chaos
    dynamisme dynamique faveur fondation gagnant gagnante remplaçant
    substitut camp classique champion défi challenge silence calme
    ''',
}


def sans_accent(s):
    """Range é et e sous la même clé : les gloses accentuent, pas les clés."""
    return ''.join(c for c in unicodedata.normalize('NFD', s.lower())
                   if unicodedata.category(c) != 'Mn')


def index_lexique():
    """mot-clé → liste des champs qui le revendiquent.

    Un mot-clé partagé (« chemin » est un lieu et un trajet, « bureau » un
    local et un employeur) n'est pas une erreur à corriger : il vote pour
    plusieurs champs, et c'est le total des votes qui tranche.
    """
    idx = collections.defaultdict(set)
    for table in (LEXIQUE, RELECTURE):
        for champ, mots in table.items():
            for m in mots.split():
                idx[sans_accent(m)].add(champ)
    return idx


JETON = re.compile(r"[a-zàâäéèêëîïôöùûüçœ'\-]+")


def jetons(texte):
    for m in JETON.findall(texte.lower()):
        j = sans_accent(m)
        yield j
        # Le pluriel des gloses est le seul accident morphologique fréquent ;
        # on ne va pas plus loin, une racinisation du français ferait plus de
        # dégâts qu'elle n'en réparerait sur des listes écrites à la main.
        if len(j) > 3 and j.endswith('s'):
            yield j[:-1]


# Un nom propre en tête de glose : une majuscule, puis des minuscules, et pas
# un sigle ponctué — « Bettembourg » et « États-Unis » passent, « T.V.A. » non.
NOM_PROPRE = re.compile(r"^[A-ZÀ-ÝŒ][a-zà-ÿœ'’\-]{2,}")


def champ_de(glose, idx):
    """Le champ d'une glose, ou None si rien ne se prononce.

    Le premier sens pèse trois fois : c'est celui que la carte met en tête, et
    sur « Wee — chemin, sens, moyen » c'est le seul qui décide de la couleur.

    Faute de mot-clé, un dernier recours : **un nom propre est un lieu**. Sur
    ce carnet la règle est sûre parce qu'elle est tardive — les gentilés
    (« Américain », « Belge ») sont déjà pris par le lexique, et ce qui arrive
    ici est de la géographie, communes du pays comprises. Elle vaut une
    soixantaine de cartes qu'aucune liste de mots-clés n'aurait attrapées, et
    c'est le seul endroit du script où la forme d'un mot décide de son sens.
    """
    score = collections.Counter()
    tete = glose.split(',')[0]
    for poids, texte in ((3, tete), (1, glose)):
        for j in jetons(texte):
            for c in idx.get(j, ()):
                score[c] += poids
    if not score:
        return 'territoire' if NOM_PROPRE.match(tete.strip()) else None
    haut = max(score.values())
    for c in CHAMPS:                       # l'ordre de CHAMPS départage
        if score[c] == haut:
            return c
    return None


# ---------------------------------------------------------------------------
# La relecture : ce que le lexique manque ou range mal
# ---------------------------------------------------------------------------
# Écrit à la main, lemme par lemme, contre la glose entière. Ces entrées
# gagnent toujours contre le lexique. Elles sont ici et non dans un fichier à
# part pour qu'une décision de classement se relise avec son motif.
MAIN = {
    # — mots très tirés que le lexique laissait sans champ ————————————
    'Enn': 'temps',            # fin, bout — la fin est un moment
    'Deel': 'temps',           # partie, part d'héritage — une quantité
    'Lag': 'territoire',       # site, situation — le sens spatial d'abord
    'Fall': 'public',          # cas, affaire policière
    'Fro': 'savoir',           # question
    'Beispill': 'savoir',      # exemple
    'Asaz': 'economie',        # engagement, opération, emploi
    'Betrib': 'economie',      # entreprise, animation
    'Situatioun': 'temps',     # situation, situation professionnelle — un état
    'Zukunft': 'temps',        # avenir
    'Richtung': 'mouvement',   # direction
    'Kéier': 'mouvement',      # virage, demi-tour, fois
    'Moment': 'temps',         # élément décisif
    'Ufank': 'temps',          # début, commencement
    'Meenung': 'savoir',       # avis
    'Säit': 'savoir',          # côté, page, page web — la page l'emporte
    'Prozent': 'temps',        # pour cent, degré, remise — une mesure
    'Millioun': 'temps',       # million
    'Problem': 'savoir',       # problème
    'Rei': 'temps',            # rangée, tour, ligne — un ordre
    'Zil': 'mouvement',        # but, destination
    'Wee': 'mouvement',        # chemin, sens, moyen
    'Plaz': 'territoire',      # place, endroit
    'Aart': 'savoir',          # sorte, manière
    'Grond': 'savoir',         # raison, motif, fond
    'Effet': 'savoir',         # effet
    'Ursaach': 'savoir',       # cause
    'Zoustand': 'temps',       # état
    'Wäert': 'economie',       # valeur
    'Chance': 'temps',         # chance, occasion
    'Risiko': 'public',        # risque
    'Kader': 'economie',       # cadre, sélection, cadre de travail

    # — fonctions et institutions que le lexique rate ————————————————
    'Direkter': 'economie',    # directeur
    'Minister': 'public',      # ministre
    'Regierung': 'public',     # gouvernement
    'Partei': 'public',        # parti, partie
    'Gemeng': 'public',        # commune, conseil communal, mairie
    'Police': 'public',        # policier
    'Geriicht': 'public',      # tribunal
    'Gesetz': 'public',        # loi
    'Decisioun': 'public',     # décision
    'Kontroll': 'public',      # contrôle
    'Affer': 'public',         # victime, offrande
    'Täter': 'public',         # auteur (d'un délit)
    'Chauffer': 'mouvement',   # chauffeur
    'Schneider': 'economie',   # tailleur, faucheux, tipule — le métier d'abord
    'Aarbecht': 'economie',    # travail
    'Dir': None,               # vous — un pronom n'a pas de domaine
}

# ---------------------------------------------------------------------------
# Les enluminures
# ---------------------------------------------------------------------------
# Un meuble par entrée, attribué à la main contre la glose entière. Le nom du
# meuble est celui d'une silhouette de `carnet/Meubles.kt` ; le générateur
# refuse de sortir si l'un d'eux n'y existe pas.
#
# Règle unique, et elle exclut beaucoup de mots faciles : le dessin ne doit
# contredire aucun des sens de la glose. « Gas — gaz, gazinière, accélérateur »
# n'est pas enluminé, « Bierg — montagne, côte, garant » non plus.
MEUBLES = {
    # la maison et ce qu'elle contient
    'Haus': 'maison',           # maison
    'Dier': 'porte',            # porte, embrasure, porte d'entrée
    'Fënster': 'fenetre',       # fenêtre, vitrine, rebord de fenêtre
    'Dësch': 'table',           # table
    'Bänk': 'banc',             # banc, banc d'école
    'Bett': 'lit',              # lit
    'Tirang': 'tiroir',         # tiroir
    'Safe': 'coffre',           # coffre-fort
    'Spigel': 'miroir',         # miroir
    'Krunn': 'robinet',         # robinet
    'Keel': 'quille',           # quille
    # ce qu'on bâtit
    'Duerf': 'village',         # village
    'Stad': 'ville',            # ville
    'Kierch': 'eglise',         # Église, église — les deux sens sont l'édifice
    'Schoul': 'ecole',          # école
    'Pilier': 'pilier',         # pilier
    'Potto': 'poteau',          # poteau
    'Tunnel': 'tunnel',         # tunnel
    'Kran': 'grue',             # grue
    'Millen': 'moulin',         # moulin
    'Zelt': 'tente',            # tente
    # la table
    'Brout': 'pain',            # pain
    'Kéis': 'fromage',          # fromage, bêtises — le second est figuré
    'Kuch': 'gateau',           # gâteau
    'Pizza': 'pizza',           # pizza
    'Uebst': 'fruits',          # fruits
    'Mëllech': 'lait',          # lait
    'Fläsch': 'bouteille',      # bouteille, biberon — deux bouteilles
    'Wäin': 'vin',              # vin, vin rouge, vin blanc
    'Kaffi': 'cafe',            # café, (tasse de) café, petit-déjeuner
    'Messer': 'couteau',        # couteau, lame
    'Waasser': 'eau',           # eau, (verre d')eau, urine — trois liquides
    # le vivant
    'Bam': 'arbre',             # arbre
    'Häerz': 'coeur',           # cœur
    'Fësch': 'poisson',         # poisson
    'Kaz': 'chat',              # chat, chatte, chat sauvage
    'Hond': 'chien',            # chien, voyou — le second est figuré
    'Léiw': 'lion',             # lion
    'Päerd': 'cheval',          # cheval, cavalier
    'Ochs': 'boeuf',            # bœuf, abruti — le second est figuré
    'Réi': 'chevreuil',         # chevreuil
    'Märel': 'merle',           # merle (noir)
    'Hand': 'main',             # main
    'Fouss': 'pied',            # pied, coup de pied
    'Kapp': 'tete',             # tête, tête (pensante)
    'Zant': 'dent',             # dent
    'Engel': 'ange',            # ange
    # le ciel
    'Sonn': 'soleil',           # soleil
    'Mound': 'lune',            # lune
    'Reen': 'pluie',            # pluie
    # ce qui roule et ce qui vole
    'Auto': 'voiture',          # voiture
    'Vëlo': 'velo',             # vélo
    'Fliger': 'avion',          # avion
    'Helikopter': 'helicoptere',  # hélicoptère
    'Schëff': 'bateau',         # navire, nef
    # ce qui se lit et ce qui se dit
    'Buch': 'livre',            # livre, livre de comptes
    'Bréif': 'lettre',          # lettre
    'Kaart': 'carte',           # carte, carte (d'accès), carte de visite
    'Foto': 'photo',            # photo
    'Ticket': 'ticket',         # ticket
    'Lupp': 'loupe',            # loupe
    'Brëll': 'lunettes',        # (paire de) lunettes, lunette
    'Mikro': 'micro',           # micro
    'Kamera': 'camera',         # caméra
    'Handy': 'telephone',       # portable
    'Smartphone': 'telephone',  # smartphone — le seul dessin partagé
    'Antenn': 'antenne',        # antenne
    'Fändel': 'drapeau',        # drapeau
    'Auer': 'horloge',          # heure, montre, compteur — un cadran les tient
    # l'outil, le métal, l'habit
    'Hummer': 'marteau',        # marteau
    'Gold': 'or',               # or
    'Helm': 'casque',           # casque
    'Jeans': 'jean',            # jean
    'Schong': 'chaussure',      # chaussure
    'Zigarett': 'cigarette',    # cigarette
}


def charger_corpus():
    """Les emplacements du carnet : lemme → (forme rencontrée, glose).

    Un mot n'entre au carnet que s'il peut sortir d'un jeu, et les grilles
    portent déjà leur glose. Le lemme est le représentant de la famille, parce
    que c'est ce que `TranslationDictionary.fiche` rend à l'exécution : la
    carte de « Männer » doit trouver le blason de « Mann ».
    """
    def actif(nom):
        with open(os.path.join(ACTIFS, nom), encoding='utf-8') as f:
            return json.load(f)

    familles = actif('luxemburgish_familles.json')['familles']
    representant = {}
    for tete, formes in familles.items():
        representant[tete] = tete
        for f in str(formes).split():
            representant.setdefault(f, tete)

    def tete_de(mot):
        return representant.get(mot) or representant.get(mot.lower()) or mot

    slots = {}
    for nom in ('luxemburgish_crossword.json', 'luxemburgish_chassecroise.json'):
        for grille in actif(nom)['grilles']:
            for m in grille['mots']:
                t = tete_de(m['f'])
                if t not in slots or (m.get('g') and not slots[t][1]):
                    slots[t] = (m['f'], m.get('g', ''))

    traductions = actif('luxemburgish_translations.json')['translations']
    for item in actif('luxemburgish_cloze.json')['items']:
        for mot in [item['a']] + item.get('d', []):
            t = tete_de(mot)
            if t not in slots:
                slots[t] = (mot, traductions.get(mot) or traductions.get(t) or '')
    return slots


def meubles_declares():
    """Les silhouettes que Meubles.kt sait tracer, lues dans le Kotlin.

    Le script et le fichier de dessins doivent tomber d'accord, et le seul
    accord qui tienne dans le temps est celui qu'on vérifie. Un meuble
    attribué ici mais jamais dessiné produirait une carte muette.
    """
    chemin = os.path.join(RACINE, 'android_keyboard', 'app', 'src', 'main',
                          'java', 'com', 'example', 'kreyolkeyboard', 'carnet',
                          'Meubles.kt')
    if not os.path.exists(chemin):
        return None
    with open(chemin, encoding='utf-8') as f:
        source = f.read()
    return set(re.findall(r'^\s*"([a-z_]+)" to ', source, re.M))


def main():
    slots = charger_corpus()
    idx = index_lexique()

    champs = {}
    for lemme, (_forme, glose) in slots.items():
        c = MAIN[lemme] if lemme in MAIN else (champ_de(glose, idx) if glose else None)
        if c:
            champs[lemme] = c

    connus = meubles_declares()
    if connus is not None:
        manquants = sorted(set(MEUBLES.values()) - connus)
        if manquants:
            sys.exit('meubles attribués mais non dessinés dans Meubles.kt : '
                     + ', '.join(manquants))
    absents = sorted(m for m in MEUBLES if m not in slots)
    if absents:
        print('avertissement — enluminés hors du carnet (aucune carte ne les '
              'portera) : ' + ', '.join(absents))

    blasons = {}
    for lemme in sorted(set(champs) | set(MEUBLES)):
        entree = {}
        if lemme in champs:
            entree['c'] = champs[lemme]
        if lemme in MEUBLES:
            entree['m'] = MEUBLES[lemme]
        blasons[lemme] = entree

    sortie = {
        'version': 1,
        'champs': CHAMPS,
        'blasons': blasons,
    }
    with open(SORTIE, 'w', encoding='utf-8') as f:
        json.dump(sortie, f, ensure_ascii=False, separators=(',', ':'),
                  sort_keys=True)

    n = len(slots)
    glosés = sum(1 for _f, g in slots.values() if g)
    print(f'emplacements du carnet : {n}  (glosés : {glosés})')
    print(f'rattachés à un champ   : {len(champs)}  '
          f'({100 * len(champs) / n:.0f} % du carnet, '
          f'{100 * len(champs) / glosés:.0f} % des glosés)')
    print(f'enluminés              : {len(MEUBLES)}  '
          f'({100 * len(MEUBLES) / n:.1f} %)')
    print(f'poids de l\'actif       : {os.path.getsize(SORTIE) / 1024:.0f} ko\n')
    for c, k in collections.Counter(champs.values()).most_common():
        print(f'{k:6}  {c}')

    reste = [(l, g) for l, (_f, g) in slots.items() if l not in champs and g]
    print(f'\nsans champ, mais glosés : {len(reste)} — échantillon :')
    for l, g in reste[:25]:
        print(f'  {l:20} {g[:52]}')


if __name__ == '__main__':
    main()
