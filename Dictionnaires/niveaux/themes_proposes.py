"""Le classement thématique proposé pour les séries « Niveau A1 » et « Niveau A2 ».

Une seule liste de thèmes pour les deux niveaux, pour qu'un apprenant retrouve
les mêmes pages d'un niveau à l'autre : chaque niveau ne montre que ses mots.
Les mots qu'aucun thème ne prend vont, comme avant, aux pages par nature
(verbes, adjectifs, petits mots, autres noms).

Rangé à la main par Claude (2026-11-27), à relire : c'est une proposition.
`generate_niveaux.py --classement propose` l'applique ; sans l'option, les
séries gardent les thèmes tirés des catégories du LOD.

Un mot de plusieurs mots s'écrit avec `_` à la place des espaces. Les titres en
luxembourgeois, en allemand et en portugais sont écrits par Claude, à faire
relire.
"""


def _t(fr, en, de, lb, pt):
    return dict(fr=fr, en=en, de=de, lb=lb, pt=pt)


# (identifiant, titres, mots), dans l'ordre des pages.
THEMES = [
    ('salut', _t('Saluer et se présenter', 'Greetings and introductions', 'Begrüßen und vorstellen',
                 'Begréissen a sech virstellen', 'Cumprimentar e apresentar-se'), """
        Moien Äddi Awar Awuer Bonjour Grouss Merci villmools wannechgelift gelift Pardon Excuse gläichfalls
        jo Nee ok bon majo heeschen Numm Virnumm Familljennumm Adress Alter Gebuertsdatum Nationalitéit
        Lëtzebuerger Lëtzebuergesch Englänner finnesch japanesch südlännesch Ausland friem international
        entschëllegen kenne_léieren Kontakt
    """),
    ('questions', _t('Poser des questions', 'Asking questions', 'Fragen stellen', 'Froe stellen',
                     'Fazer perguntas'), """
        wat wéi wéini wiem wien wou wuer firwat Fro froen äntweren ufroen
    """),
    ('famille', _t('Famille et couple', 'Family and couples', 'Familie und Partnerschaft', 'Famill a Koppel',
                   'Família e casal'), """
        Boma Bomi Bopa Bopi Bouf Brudder Cousin Cousine Duechter Elteren Enkel Enkelin Enkelkand Famill Fra
        Geschwëster Groussmamm Grousspapp Grousselteren Jong Kand Koseng Kusin Mamm Mamma Mann Meedchen
        Monni Neveu Niess Papp Schwëster Tatta Puppelchen Erwuessen Jugend Gebuert gebieren
        Brautpuer Hochzäit Koppel Kuss bestuet bestueden pacsen gepacst Jonggesell Jonggesellin
        Stot Relatioun familiär
    """),
    ('gens', _t('Les gens', 'People', 'Menschen', "D'Leit", 'Pessoas'), """
        Leit Persoun Mënsch Kärel Typ Madame Madamm Monsieur Här Härin Frënd Frëndin Noper Nopesch
        Awunner Awunnerin Matbewunner Matbewunnerin Client Cliente Passant Kolleeg Groussherzog
        Groussherzogin Vegetarier Vegetarierin Geck Zwerg Grupp jiddereen
    """),
    ('corps', _t('Le corps et l\'apparence', 'Body and looks', 'Körper und Aussehen', 'Kierper an Ausgesinn',
                 'Corpo e aparência'), """
        A Aarm Aperhoer Bak Bauch Been Fanger Fangernol Fouss Genéck Gesiicht Hals Hand Häerz Hëft Hënner
        Hoer Ielebou Kapp Kënn Knéi Lëps Long Mo Mond Muskel Nues Ouer Réck Schëller Stier Taille Wimper
        Wued Zant Zéif Zong kierperlech Ausgesinn ausgesinn Baart Schnauz Frisur Coiffure Hoerfaarf
        Hautfaarf Teint Piercing Tätowéierung tätowéieren Lëppestëft Figur blond korpulent muskuléis
        schlank bleech schminken raséieren kämmen krauselen coifféieren schnäizen Nuesschnappech
        Zännbiischt Zännseef duschen gesinn kucken nokucken héieren lauschteren nolauschteren richen sténken
    """),
    ('sante', _t('Santé et soins', 'Health and care', 'Gesundheit und Pflege', 'Gesondheet a Fleeg',
                 'Saúde e cuidados'), """
        Apdikt Besserung doheembleiwen Féiwer gesond Gesondheet Gripp Houscht houschten Houschtsirop
        Schnapp Erkältung Angine Allergie Ausschlag Bazill Duerchfall Entzündung entzünden Krankheet krank
        Klinick Spidol Urgence Ambulanz Accident Brëtsch Krätsch Praxis Cabinet Wartesall Visitt
        Routinekontroll Patient Patientin Medikament Rezept Ordonnance Tablett Pëll Sallef Plooschter
        Verband verbannen Vitaminn Thermometer Bluttanalys Bluttdrock Echografie IRM Test positiv negativ
        Blinddarm Operatioun operéieren oflauschteren iwwerweisen verschreiwen krankschreiwen
        Krankeschäin Desinfektiounsmëttel desinfizéieren ustiechen fleegen Hörapparat
        Kompressiounsstrëmp Varice Zell Ouerewéi wéidinn briechen verbrennen Sonnebrand verstauchen
        verspaant schwanger dronken Zigarett fëmmen stierwen
    """),
    ('caractere', _t('Le caractère', 'Personality', 'Charakter', 'De Charakter', 'O carácter'), """
        Charakter Perséinlechkeet Dugend Talent Genie Perfektionist Humor Gedold
        béis brav léif sympathesch onsympathesch seriö onseriö agebilt aggressiv arrogant charmant
        couragéiert dominant domm egoistesch egozentresch éiergäizeg éierlech exigent fein fläisseg
        flexibel frëndlech gedëlleg geckeg generéis granzeg häerzlech hëllefsbereet houfreg intelligent
        jalous knéckeg konsequent kreativ liddereg materialistesch perfektionistesch rebellesch
        reservéiert schei sensibel spontan streng stuer temperamentvoll tolerant trei uerdentlech
        virwëtzeg zouverlässeg fräiwëlleg erfollegräich originell
    """),
    ('sentiments', _t('Sentiments et humeur', 'Feelings and mood', 'Gefühle und Laune', 'Gefiller a Laun',
                      'Sentimentos e humor'), """
        Angscht Gléck Hoffnung Suerg Stress Loscht Motivatioun Ambitioun Optimismus Interessi Intressi
        frou midd doudmidd gelaunt liewensfrou nervös onroueg rosen roueg zefridden
        laachen fäerten freeën hoffen depriméieren opreegen gefalen interesséieren intresséieren
        motivéieren iwwerraschen leeddoen verdroen meckeren stéieren respektéieren fillen
    """),
    ('metiers', _t('Métiers', 'Jobs', 'Berufe', 'Beruffer', 'Profissões'), """
        Aarbechter Aarbechterin Affekot Affekotin Apdikter Apdiktesch Architekt Architektin Babysitter
        Babysitterin Bäcker Bäckesch Bauer Bauerefra Botzfra Botzmann Bréifdréier Bréifdréiesch Caissière
        Coiffer Coiffeur Coiffeuse Comptabel Déierendokter Déierendoktesch Direkter Direktesch Dokter
        Doktesch Educateur Educatrice Elektriker Elektrikerin Employé Employée Estheticien Estheticienne
        Fotograf Fotografin Infirmier Infirmière Informatiker Informatikerin Iwwersetzer Iwwersetzerin
        Joffer Journalist Journalistin Kach Kächen Kächin Keessjee Léierjong Léiermeedchen Mecanicien
        Mecanicienne Metzler Metzlesch Moniteur Monitrice Museker Musekerin Pilot Pilotin Polizist
        Polizistin Proff Receptionnist Receptionnistin Riichter Riichterin Schauspiller Schauspillerin
        Sekretär Sekretärin Sportler Sportlerin Steemetzer Steemetzesch Traiteur Traiteuse Usträicher
        Usträicherin Verkeefer Verkeeferin Zänndokter Zänndoktesch Aendokter Bassist Buschauffer Chef
        Fraendokter Garçon Gastrolog Gastrologin Hausdokter Hautdokter Kannerdokter Kiné Korrespondent
        Linguist Notaire Psychiater Psycholog Psychologin Serveuse
    """),
    ('travail', _t('Le travail', 'Work', 'Die Arbeit', "D'Aarbecht", 'O trabalho'), """
        Aarbecht Beruff Büro Firma Entreprise Carrière Chômage Kontrakt Reunioun Projet Personal
        Mataarbechter Aarbechtskolleeg Aarbechtskolleegin Déngscht Rapport Dossier Fiche Annex Kopie Kader
        Organisatioun organiséieren Presentatioun presentéieren Analys Resultat Léisung Problem Konflikt
        Schreifdësch Agenda Sekretariat Spezialitéit Mëttespaus Paus schaffen astellen engagéieren
        streiken pensionéieren produzéieren realiséieren zoustänneg
    """),
    ('logement', _t('Le logement', 'Housing', 'Die Wohnung', "D'Wunneng", 'A habitação'), """
        Haus Wunneng Appartement Studio Residence Reienhaus Zëmmer Raum Kichen Buedzëmmer Schlofzëmmer
        Kannerzëmmer Iesszëmmer Stuff Salon Gank Keller Stack Rez-de-chaussée Lift Balcon Terrass Gaart
        Garage Dier Viischt_Dier Fënster Mauer Plaffong Toilette Loyer Charge WG Wunngemeinschaft
        wunnen plënneren lounen verlounen Schlëssel Schell schellen opspären zouspären spären opmaachen
        zoumaachen zoukréien Leeder Privatsphär
    """),
    ('meubles', _t('Meubles et objets', 'Furniture and things', 'Möbel und Gegenstände', 'Miwwelen an Objeten',
                   'Móveis e objetos'), """
        Miwwel Bett Dësch Iessdësch Kaffisdësch Salonsdësch Nuetsdësch Stull Fotell Kannapee Kusch Schaf
        Kleederschaf Garderob Komoud Regal Bicherregal Tirang Gaardemiwwel Spigel Luucht Steeluucht Teppech
        Decken Vas Mantelbriet Bild Poster Buedbidden Lavabo Këscht Seel Apparat
        miwweléieren miwweléiert bequeem
    """),
    ('menage', _t('Le ménage', 'Housework', 'Der Haushalt', 'De Stot', 'As tarefas domésticas'), """
        botzen opbotzen spullen wäschen Wäsch Wäschmaschinn Spullmaschinn Spullsteen Staubsauger
        staubsaugen stëbsen strecken Streck Streckbriet Streckeisen araumen raumen Botzgeschier
        Dreckskëscht Poubelle Eemer Recycling eidel_maachen ophänken hänken anhänken Installatioun
        reparéieren nätzen knaschteg propper naass dréchen
    """),
    ('vetements', _t('Vêtements et accessoires', 'Clothes and accessories', 'Kleidung und Accessoires',
                     'Kleeder an Accessoiren', 'Roupa e acessórios'), """
        Kleed Gezei Anorak Bikini Blus Box Jeansbox Jeans Collant Foulard Hiem Hiemchen Nuetshiem
        Jackett Jupe Rack Kap Mutz Krawatt Mantel Maillot Schwammbox Pullover Pyjama Rimm Salopett
        Sandal Schlapp Turnschlapp Schong Tallekeschong Stiwwel Strëmp Ënnerwäsch Buedmantel Tailleur
        Kostüm Gilet Short Schal Händsch Brëll Liesbrëll Posch Prabbeli Sonneprabbeli Rank Accessoire
        Knäppchen Sam Col Tallek Kabinn Gréisst Stil schick elegant almoudesch
        undoen ausdoen unhunn umoossen
    """),
    ('couleurs', _t('Couleurs, formes et matières', 'Colours, shapes and materials', 'Farben, Formen und Stoffe',
                    'Faarwen, Formen a Stoffer', 'Cores, formas e materiais'), """
        blo brong giel gréng gring rout schwaarz wäiss beige gro hellblo hellbrong mof rosa Faarf faarweg
        donkel hell gesträift getëppelt karéiert unis Muster Motiv ronn oval eckeg véiereckeg Kugel
        Tëppel Plastik Material Blech Steen Woll Seid Lieder Kotteng Flanell gëllen seiden pelzen
    """),
    ('aliments', _t('Les aliments', 'Food', 'Lebensmittel', "D'Liewensmëttel", 'Os alimentos'), """
        Äppeltäsch Baguette Botter Bréitchen Brout Charcuterie Croissant Ee Fleesch Fësch Fritt Gebeess
        Gromperekichelchen Ham Hamburger Hunneg Jugurt Kachkéis Kamell Kéis Ketchup Kichelchen Knätsch
        Kuch Marmerkuch Mayonnaise Mëtsch Mettwurscht Müsli Müüsli Nuddel Paschtéit Pizza Räis Ram
        Saumon Scampi Schmier Schneck Schockela Schockelasrull Streisel Taart Thon Tomatenzopp Zalot
        Zoossiss Zopp Geméiszopp Deeg Glace Lutsch Pastill Muffin Popcorn Séissegkeet Speck Wäinzoossiss
        Mëllechprodukt Lëns säfteg séiss sauer batter réi
    """),
    ('fruits', _t('Fruits et légumes', 'Fruit and vegetables', 'Obst und Gemüse', 'Uebst a Geméis',
                  'Fruta e legumes'), """
        Uebst Geméis Äerdbier Ananas Apel Aprikos Aubergine Banann Bier Boun Champignon Choufleur Courgette
        Drauf Ënn Gromper Kiischt Kiwi Kornischong Kürbis Mango Meloun Muert Noss Orange Paprika Porrett
        Quetsch Spargel Tomat Zitroun zeideg
    """),
    ('boissons', _t('Boissons', 'Drinks', 'Getränke', 'Gedrénks', 'Bebidas'), """
        Gedrénks Waasser Spruddelwaasser Spruddel Jus Orangëjus Kaffi Café Expresso Téi Kamillentéi Mëllech
        Schockelasmëllech Sirop Wäin Rosé Béier Alkohol Aperitif Drëpp Grog Patt Fläsch Glas Taass drénken
    """),
    ('repas', _t('Repas et restaurant', 'Meals and restaurants', 'Mahlzeiten und Restaurant',
                 'Molzechten a Restaurant', 'Refeições e restaurante'), """
        Molzecht Haaptmolzecht Entrée Mëttegiessen Nuechtiessen Kaffidrénken Iessgewunnecht Liblingsiessen
        Haaptplat Plat Dessert Menü Menu Restaurant Brasserie Fastfood Reservatioun Appetit Honger
        hongereg Kalorie Kuelenhydrat Fett iessen schmaachen genéissen bestellen picknicken
    """),
    ('cuisine', _t('Cuisiner', 'Cooking', 'Kochen', 'Kachen', 'Cozinhar'), """
        kachen baken broden rappen schielen schneiden mëschen ofschmaachen plattdrécken sabbelen
        preparéieren Läffel Teller Pan Béchs Dëppchen Tüb Packung Kachmaschinn Kachplack Mikrowell Frigo
        Kaffismaschinn Ueleg Olivenueleg Salz Peffer Muskat Muskot Zocker Miel Kraut
    """),
    ('courses', _t('Courses et argent', 'Shopping and money', 'Einkaufen und Geld', 'Akafen a Suen',
                   'Compras e dinheiro'), """
        akafen kafen verkafen bezuelen kaschten shoppen ausginn Präis Su Euro Bank Bancomat Kreditkaart
        Keess Portmonni Virement Budget Finanzen Rechnung Buttek Supermarché Bäckerei Metzlerei Epicerie
        Pâtisserie Parfümerie Librairie Bicherbuttek Blummebuttek Kleederbuttek Schongbuttek
        Zeitungsbuttek Maart Floumaart Akafslëscht Solden Rayon Vitrinn Offer Commande Reklamatioun
        reklaméieren Reklamm Katalog Flyer Broschür Pak Wäert bëlleg deier
    """),
    ('ville', _t('En ville', 'In town', 'In der Stadt', 'An der Stad', 'Na cidade'), """
        Stad Haaptstad Duerf Gemeng Zentrum Strooss Haaptstrooss Gaass Boulevard Plëss Knuedler Bréck Eck
        Kräizung Park Parking Parkplaz Spillplaz Post Gare Policebüro Musée Kino Theater Philharmonie
        Conservatoire Bibliothéik Schwämm Piscine Sportshal Tankstell Gebai Palais Buerg Altersheem
        Altersheim Kiosk Coiffersalon Coiffeurssalon Coifferssalon Solarium Fitnessstudio Reesbüro
        Déierenasyl Ëffnungszäiten Guichet Plang besichen laanschtkommen
    """),
    ('pays', _t('Le pays et le monde', 'The country and the world', 'Das Land und die Welt', "D'Land an d'Welt",
                'O país e o mundo'), """
        Land Minett Regioun Géigend Ëmgéigend Lag Provënz Grenz Welt Kultur kulturell traditionell
        geografesch Politik
    """),
    ('transports', _t('Transports et circulation', 'Transport and traffic', 'Verkehrsmittel und Verkehr',
                      'Transport a Verkéier', 'Transportes e trânsito'), """
        Auto Bus Fliger Taxi Zuch Tram Metro Moto Vëlo Schëff Jeep Stau Trafick Verkéier Autobunn Tunnel
        Busarrêt Arrêt Buslinn Busverbindung Verbindung Quai Zuchbilljee Billjee Ticket Fluch Flughafen
        Fürerschäin Bensinn Diesel tanken parken fueren fortfueren erafueren heemfueren Sakgaass Schëld
        Zebrasträifen Trajet Verspéidung verpassen ukommen unhalen Depart Tut Code obligatoresch
        verbidden Verbuet oppassen Wee Sëtzplaz besetzen besat
    """),
    ('voyage', _t('Voyages et vacances', 'Travel and holidays', 'Reisen und Urlaub', 'Reesen a Vakanz',
                  'Viagens e férias'), """
        Vakanz Summervakanz Fuesvakanz Chrëschtvakanz Rees reesen Wallis Camping Zelt Roulotte Lodge Hotel
        Ausfluch Sightseeing Touristenattraktioun Attraktioun Visite_guidée Trëppeltour Tour wanderen
        trëppelen spadséieren Plage touristesch buchen reservéieren annuléieren Annulatioun Confirmatioun
        ofsoen Weekend
    """),
    ('nature', _t('Nature et animaux', 'Nature and animals', 'Natur und Tiere', 'Natur an Déieren',
                  'Natureza e animais'), """
        Natur Naturpark Landschaft Bësch Bierg Floss Séi Mier Wiss Weid Planz Blumm Buedem Rutt Hausdéier
        Hond Mupp Kaz Vugel Hunn Af Elefant Giraff Nilpäerd Ochs Päerd Schof Geess Rat Méck Schlaang Schwäin
        Tiger Zoo fidderen Sonnenopgang natierlech
    """),
    ('meteo', _t('Météo et saisons', 'Weather and seasons', 'Wetter und Jahreszeiten', 'Wieder a Joreszäiten',
                 'Tempo e estações'), """
        Wieder Joreszäit Fréijoer Summer Hierscht Wanter Loft Sonn Reen Schnéi Wand Temperatur Grad
        Celsius Fahrenheit bedeckt kal kill frësch waarm mëll niwweleg sonneg summerlech kloer däischter
        reenen schneien blëtzen donneren blosen fréieren liichten schéngen
    """),
    ('jours', _t('Jours et heures', 'Days and times', 'Tage und Uhrzeiten', 'Deeg an Zäiten', 'Dias e horas'), """
        Dag Woch Joer Mount Auer Stonn Minutt Moment Zäit Owend Nuecht Mëtteg Mëttesstonn Méindeg
        Dënschdeg Mëttwoch Donneschdeg Freideg Samschdeg Sonndeg méindes dënschdes mëttwochs donneschdes
        freides samschdes sonndes moies mëttes nomëttes owes nuets haut muer gëschter Ufank Enn Schluss
        ufänken daueren Rendez-vous pünktlech spéit
    """),
    ('journee', _t('Ma journée', 'My day', 'Mein Tag', 'Mäin Dag', 'O meu dia'), """
        Wecker erwächen opstoen opgoen Dusch schlofen aschlofen leien heemkommen
    """),
    ('quand', _t('Quand ? Souvent ?', 'When? How often?', 'Wann? Wie oft?', 'Wéini? Wéi dacks?',
                 'Quando? Com que frequência?'), """
        elo lo fréi fréier éischter geschwënn eréischt schonn ëmmer dacks oft alt heiansdo ni nach erëm
        nees endlech dunn dann dono duerno herno hautdesdaags spéitstens mat_Zäit matzäit zanter zënter
        emol eemol zweemol dräimol Mol Kéier
    """),
    ('mois', _t('Les mois', 'The months', 'Die Monate', "D'Méint", 'Os meses'), """
        Januar Februar Mäerz Abrëll Mee Juni Juli August September Oktober November Dezember
    """),
    ('fetes', _t('Fêtes et traditions', 'Holidays and traditions', 'Feste und Bräuche', 'Fester a Brauchtum',
                 'Festas e tradições'), """
        Feierdag Nationalfeierdag Chrëschtdag Chrëschtbeemchen Chrëschtmaart Kleeserchersdag Kleeschen
        Houseker Liichtmëssdag Mammendag Pappendag Ouschteren Päischtméindeg Päischtsonndeg Kiermes Cortège
        Lampion Traditioun Gebuertsdag Kaddo Käerz Loftballon Party Invitatioun invitéieren feieren
        gratuléieren Gléckwonsch wënschen Abschid Konveniat Foire
    """),
    ('ecole', _t('École et formation', 'School and learning', 'Schule und Ausbildung', 'Schoul a Formatioun',
                 'Escola e formação'), """
        Schoul Grondschoul Primärschoul Spillschoul Crèche Lycée Uni Klass Cours Sproochecours Owescours
        Weiderbildung Diplom Certificat Übung Aufgab Hausaufgab Text Wuert Saz Verb Kapitel Beispill
        Contraire Accent Sprooch Philosophie Informatik Bläistëft Bic Classeur Tafel Kantin Schüler
        Schülerin Student Studentin léieren studéieren widderhuelen fräihunn verspillen erklären verstoen
        beschreiwen schreiwen liesen notéieren opschreiwen duerchsträichen nummeréieren mëndlech Iertum
        ieren richteg falsch Reegel
    """),
    ('communication', _t('Téléphone, courrier et médias', 'Phone, post and media', 'Telefon, Post und Medien',
                         'Telefon, Post a Medien', 'Telefone, correio e media'), """
        Telefon Handy Handysnummer Telefonsnummer Virwal Nummer SMS E-Mail Message Bréif Postkaart Timber
        Porto Boîte Kaart Zeitung Zäitschrëft Radio Tëlee Internet Site Computer Maus Späicher surfen uklicken
        CD-Player Programm Profil aginn audiovisuell Noriicht Informatioun Info Renseignement Annonce Plakat Bescheed Gespréich
        Dialog schwätzen soen uruffen zeréckruffen telefonéieren ruffen schécken matschécken erreechen
        kontaktéieren informéieren mellen erzielen
    """),
    ('sport', _t('Sport', 'Sport', 'Sport', 'Sport', 'Desporto'), """
        Sport sportlech onsportlech aktiv Fussball Fussballsmatch Basket Aerobic Marathon Fitness Jogging
        joggen Minigolf schwammen Wantersport Schi Schlitt Schlittschong Schlittschung Skateboard Kajak
        Team Ekipp trainéieren gewannen verléieren decken passen Beweegung beweegen sprangen lafen matlafen
        klammen
    """),
    ('loisirs', _t('Loisirs et culture', 'Leisure and culture', 'Freizeit und Kultur', 'Fräizäit a Kultur',
                   'Lazer e cultura'), """
        Fräizäit spillen Gesellschaftsspill Draach Petzi Mickymaus bastelen sammelen Timberesammlung molen
        zeechnen sangen danzen Musek Lidd Band Concert Gei Instrument Bün Film Animatiounsfilm Filmpräis
        Gedicht Roll improviséieren Genre Héierbuch Buch Foto Aktivitéit Animatioun Disko Disco treffen
        ameséieren relaxen
        chillen entspanen meditéieren relax cool
    """),
    ('societe', _t('Démarches et société', 'Paperwork and society', 'Behörden und Gesellschaft',
                   'Demarchen a Gesellschaft', 'Burocracia e sociedade'), """
        Asyl Visa Carte_d'identité Aschreiwung aschreiwen umellen ënnerschreiwen Vertrag Recht
        Obligatioun Erlabnis erlaben stëmmen wielen Institutioun administrativ offiziell formell
        Portefeuille Memberskaart Presenz
    """),
    ('nombres', _t('Nombres et quantités', 'Numbers and amounts', 'Zahlen und Mengen', 'Zuelen a Quantitéiten',
                   'Números e quantidades'), """
        Zuel nonzeg hallef annerhallef Véierel Dosen Puer Stéck Tranche Koup Rei Rescht éischtens zweetens
        drëttens zweet vill wéineg genuch méi manner meescht bëssen puer ganz komplett total voll maximal
        minus pro per zielen Gramm Kilo Liter Pond Zentiliter Kilometer Kilometer_an_der_Stonn Meter
        Meterkaree Millimeter Quadratmeter moossen Wo
    """),
    ('directions', _t('Où ? Places et directions', 'Where? Places and directions', 'Wo? Orte und Richtungen',
                      'Wou? Plazen a Richtungen', 'Onde? Lugares e direções'), """
        hei heihinner do dohannen dohin doheem dobannen uewen hannen vir virdrun virun hannert ënnert
        iwwer iwwert nieft niewent nobäi bei laanscht ronderëm ëm tëschent vis-à-vis lénks riets
        riichtaus Richtung Norden Süden Westen Osten Säit Mëtt Mëttelpunkt Rand Plaz ënnerwee fort wäit
    """),
    ('idees', _t('Idées et choses', 'Ideas and things', 'Ideen und Dinge', 'Iddien a Saachen',
                 'Ideias e coisas'), """
        Saach Zort Iddi Ënnerscheed Differenz Verännerung Virdeel Wourecht Qualitéit Chance Zeechen Rot
        Rotschlo Bléck Geräisch Kaméidi Rou Modell Sujet Service Roulement Perte Ausstralung
    """),
]

# Le thème voisin d'un thème qui n'a pas trois mots dans un niveau.
REPLI = {
    'questions': 'salut',
    'sentiments': 'caractere',
    'vetements': 'corps',
    'journee': 'jours',
    'mois': 'jours',
    'fetes': 'jours',
    'nature': 'meteo',
    'idees': 'travail',
}

# L'ordre des pages A2 : celui des chapitres du manuel « Schwätzt Dir
# Lëtzebuergesch? – A2 » de l'INLL, d'après les mots de chaque thème que les
# corrigés des huit chapitres emploient le plus (relevé du 2026-11-28, voir
# corriges_a2.md). Le chapitre 8 prépare l'examen et n'a pas de thème à lui.
# Un thème absent de cette liste garde sa place, après les chapitres.
CHAPITRES_A2 = [
    (0, 'Pour commencer', ['salut', 'questions', 'nombres']),
    (1, 'An der Stad', ['ville', 'transports', 'courses', 'societe', 'repas', 'boissons', 'aliments', 'cuisine',
                        'jours']),
    (2, 'Wunnen', ['logement', 'meubles', 'menage']),
    (3, 'Mäin Dag', ['journee', 'quand', 'ecole', 'loisirs', 'sport']),
    (4, 'Kleeder a Charakter', ['vetements', 'couleurs', 'corps', 'caractere', 'sentiments', 'fetes']),
    (5, 'Vakanz a Weekend', ['voyage', 'meteo', 'nature', 'famille', 'mois']),
    (6, 'Am Quartier', ['directions', 'gens', 'pays']),
    (7, 'Gesondheet a Beruff', ['sante', 'communication', 'metiers', 'travail']),
]
