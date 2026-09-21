import Foundation

/// Ce que fait une touche. Les lettres et symboles sont insérés tels quels
/// (la casse est appliquée par le contrôleur), le reste est une commande.
enum Action: Equatable {
    case texte(String)
    case majuscule
    case effacer
    case page(Page)
    case globe
    case espace
    case entree
}

enum Page {
    case lettres
    case chiffres
}

struct Touche {
    let libelle: String
    let action: Action
    /// Largeur relative : une rangée de lettres totalise 10, comme sur Android.
    let poids: CGFloat
    /// Touche de commande (fond plus sombre) plutôt que caractère.
    let speciale: Bool

    static func lettre(_ s: String) -> Touche {
        Touche(libelle: s, action: .texte(s), poids: 1, speciale: false)
    }
}

/// Dispositions recopiées de `createAlphabeticLayout()` et
/// `createNumericLayout()` (KeyboardLayoutManager.kt) : QWERTZ, é fermant la
/// rangée d'accueil, ä ë ' dédiés autour de la barre d'espace. Les comptages qui
/// justifient ces choix sont dans CLAUDE.md, section « Keyboard layout » ; si le
/// corpus change, on corrige d'abord Android, puis ici.
///
/// Écarts assumés avec Android :
/// - la touche EMOJI devient le globe : iOS ne laisse pas une extension appeler
///   le sélecteur d'emojis système, le globe y mène ;
/// - ü, ö, à, è, ê, ç (appui long sur Android) ne sont pas encore accessibles.
enum Disposition {
    static func rangees(page: Page, avecGlobe: Bool) -> [[Touche]] {
        switch page {
        case .lettres: return lettres(avecGlobe: avecGlobe)
        case .chiffres: return chiffres(avecGlobe: avecGlobe)
        }
    }

    private static func lignes(_ s: String) -> [Touche] {
        s.map { Touche.lettre(String($0)) }
    }

    private static func globe() -> Touche {
        Touche(libelle: "🌐", action: .globe, poids: 1, speciale: true)
    }

    private static func lettres(avecGlobe: Bool) -> [[Touche]] {
        let r1 = lignes("qwertzuiop")
        let r2 = lignes("asdfghjklé")
        let r3 = [Touche(libelle: "⇧", action: .majuscule, poids: 1.5, speciale: true)]
            + lignes("yxcvbnm")
            + [Touche(libelle: "⌫", action: .effacer, poids: 1.5, speciale: true)]
        var r4: [Touche] = [
            Touche(libelle: "123", action: .page(.chiffres), poids: 1.5, speciale: true),
            .lettre(","), .lettre("ä"),
            Touche(libelle: "Lëtzebuergesch", action: .espace, poids: 4, speciale: false),
            .lettre("ë"), .lettre("'"), .lettre("."),
        ]
        if avecGlobe { r4.append(globe()) }
        r4.append(Touche(libelle: "⏎", action: .entree, poids: 1.5, speciale: true))
        return [r1, r2, r3, r4]
    }

    private static func chiffres(avecGlobe: Bool) -> [[Touche]] {
        let r1 = lignes("1234567890")
        let r2 = lignes("-/:;()€&@\"")
        let r3 = lignes("=.,?!'+*#")
            + [Touche(libelle: "⌫", action: .effacer, poids: 1, speciale: true)]
        var r4: [Touche] = [
            Touche(libelle: "ABC", action: .page(.lettres), poids: 1.5, speciale: true),
        ]
        if avecGlobe { r4.append(globe()) }
        r4.append(Touche(libelle: "Lëtzebuergesch", action: .espace, poids: 6, speciale: false))
        r4.append(Touche(libelle: "⏎", action: .entree, poids: 1.5, speciale: true))
        return [r1, r2, r3, r4]
    }
}
