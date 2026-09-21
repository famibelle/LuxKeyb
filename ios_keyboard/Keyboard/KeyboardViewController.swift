import UIKit

/// Point d'entrée de l'extension clavier (NSExtensionPrincipalClass).
///
/// Squelette : il valide la chaîne Xcode → signature → TestFlight → iPhone et
/// tape des lettres, rien de plus. Pas de suggestions, pas d'appui long, pas de
/// panneau emoji. Le moteur viendra après, et il devra tenir dans le plafond
/// mémoire d'une extension (de l'ordre de 50 à 70 Mo), d'où des assets binaires
/// plutôt que le JSON d'Android.
final class KeyboardViewController: UIInputViewController {

    private enum Maj { case off, une, verrou }

    private var page: Page = .lettres
    private var maj: Maj = .off
    private var dernierAppuiMaj = Date.distantPast
    private var repetition: Timer?
    private var attenteRepetition: Timer?

    private let conteneur = UIStackView()

    // MARK: - Cycle de vie

    override func viewDidLoad() {
        super.viewDidLoad()

        // Hauteur d'un clavier iPhone en portrait. Priorité 999 : le système
        // garde la main quand il doit contraindre (paysage, iPad).
        let hauteur = view.heightAnchor.constraint(equalToConstant: 216)
        hauteur.priority = UILayoutPriority(999)
        hauteur.isActive = true

        conteneur.axis = .vertical
        conteneur.distribution = .fillEqually
        conteneur.spacing = 8
        conteneur.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(conteneur)
        NSLayoutConstraint.activate([
            conteneur.topAnchor.constraint(equalTo: view.topAnchor, constant: 8),
            conteneur.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -4),
            conteneur.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 3),
            conteneur.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -3),
        ])
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        majusculeAutomatique()
        reconstruire()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        let avant = maj
        majusculeAutomatique()
        if maj != avant { reconstruire() }
    }

    // MARK: - Construction

    /// Sur les iPhone sans bouton d'accueil, le système dessine lui-même le
    /// globe sous le clavier et `needsInputModeSwitchKey` vaut false : une
    /// seconde touche globe serait un doublon.
    private func reconstruire() {
        let avecGlobe = needsInputModeSwitchKey

        conteneur.arrangedSubviews.forEach { $0.removeFromSuperview() }
        for rangee in Disposition.rangees(page: page, avecGlobe: avecGlobe) {
            let ligne = RangeeView()
            ligne.poids = rangee.map(\.poids)
            for touche in rangee { ligne.addSubview(bouton(pour: touche)) }
            conteneur.addArrangedSubview(ligne)
        }
    }

    private func bouton(pour touche: Touche) -> UIButton {
        let b = UIButton(type: .custom)
        var libelle = touche.libelle
        if case .texte = touche.action, maj != .off { libelle = libelle.uppercased() }
        b.setTitle(libelle, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: touche.speciale || libelle.count > 1 ? 16 : 22)
        b.setTitleColor(.label, for: .normal)
        b.backgroundColor = touche.speciale ? Self.fondSpecial : Self.fondTouche
        b.layer.cornerRadius = 5
        b.layer.shadowColor = UIColor.black.cgColor
        b.layer.shadowOffset = CGSize(width: 0, height: 1)
        b.layer.shadowOpacity = 0.3
        b.layer.shadowRadius = 0

        if touche.action == .majuscule {
            // Allumée = fond de touche ordinaire (blanc), éteinte = fond sombre
            // des commandes ; le verrou change de glyphe.
            b.setTitle(maj == .verrou ? "⇪" : "⇧", for: .normal)
            if maj != .off { b.backgroundColor = Self.fondTouche }
        }

        switch touche.action {
        case .globe:
            // Le motif d'Apple : c'est le système qui bascule, sur toucher long
            // il propose la liste des claviers.
            b.addTarget(self, action: #selector(handleInputModeList(from:with:)),
                        for: .allTouchEvents)
        case .effacer:
            b.addTarget(self, action: #selector(effacerDebut), for: .touchDown)
            b.addTarget(self, action: #selector(effacerFin),
                        for: [.touchUpInside, .touchUpOutside, .touchCancel])
        default:
            b.addAction(UIAction { [weak self] _ in self?.appuyer(touche.action) },
                        for: .touchUpInside)
        }
        return b
    }

    // MARK: - Frappe

    private func appuyer(_ action: Action) {
        let proxy = textDocumentProxy
        let avant = maj
        // Rebâtir 40 boutons à chaque lettre serait du gaspillage : on ne le
        // fait que si l'affichage (majuscule/minuscule) doit changer.
        func apresFrappe() {
            majusculeAutomatique()
            if maj != avant { reconstruire() }
        }
        switch action {
        case .texte(let s):
            proxy.insertText(maj == .off ? s : s.uppercased())
            if maj == .une { maj = .off }
            apresFrappe()
        case .espace:
            proxy.insertText(" ")
            apresFrappe()
        case .entree:
            proxy.insertText("\n")
            apresFrappe()
        case .majuscule:
            // Deux appuis rapprochés = verrouillage, comme partout.
            let maintenant = Date()
            if maintenant.timeIntervalSince(dernierAppuiMaj) < 0.35 {
                maj = .verrou
            } else {
                maj = (maj == .off) ? .une : .off
            }
            dernierAppuiMaj = maintenant
            reconstruire()
        case .page(let p):
            page = p
            reconstruire()
        case .effacer, .globe:
            break // câblés à part (répétition, sélecteur système)
        }
    }

    // MARK: - Effacement avec répétition

    @objc private func effacerDebut() {
        textDocumentProxy.deleteBackward()
        attenteRepetition = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: false) { [weak self] _ in
            self?.repetition = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in
                self?.textDocumentProxy.deleteBackward()
            }
        }
    }

    @objc private func effacerFin() {
        attenteRepetition?.invalidate()
        repetition?.invalidate()
        attenteRepetition = nil
        repetition = nil
        let avant = maj
        majusculeAutomatique()
        if maj != avant { reconstruire() }
    }

    // MARK: - Majuscule automatique

    /// Majuscule en début de phrase, sauf si le champ ne veut pas de
    /// capitalisation (mot de passe, adresse, etc.).
    private func majusculeAutomatique() {
        guard maj != .verrou else { return }
        let proxy = textDocumentProxy
        guard proxy.autocapitalizationType != UITextAutocapitalizationType.none else {
            if maj == .une { maj = .off }
            return
        }
        let avant = proxy.documentContextBeforeInput ?? ""
        let debutDePhrase: Bool
        if avant.isEmpty || avant.hasSuffix("\n") {
            debutDePhrase = true
        } else if avant.hasSuffix(" ") {
            let sansEspaces = avant.trimmingCharacters(in: .whitespaces)
            debutDePhrase = [".", "!", "?"].contains(sansEspaces.last.map(String.init) ?? "")
        } else {
            debutDePhrase = false
        }
        maj = debutDePhrase ? .une : .off
    }

    // MARK: - Apparence

    private static let fondTouche = UIColor { t in
        t.userInterfaceStyle == .dark ? UIColor(white: 0.36, alpha: 1) : .white
    }
    private static let fondSpecial = UIColor { t in
        t.userInterfaceStyle == .dark ? UIColor(white: 0.22, alpha: 1) : UIColor(white: 0.68, alpha: 1)
    }
}

/// Une rangée de touches de largeur proportionnelle à leur poids. Un
/// UIStackView ne sait pas répartir ainsi sans une contrainte par touche.
private final class RangeeView: UIView {
    var poids: [CGFloat] = []
    private let ecart: CGFloat = 5

    override func layoutSubviews() {
        super.layoutSubviews()
        guard !subviews.isEmpty, poids.count == subviews.count else { return }
        let total = poids.reduce(0, +)
        let libre = bounds.width - ecart * CGFloat(subviews.count - 1)
        var x: CGFloat = 0
        for (vue, p) in zip(subviews, poids) {
            let largeur = libre * p / total
            vue.frame = CGRect(x: x, y: 0, width: largeur, height: bounds.height)
            x += largeur + ecart
        }
    }
}
