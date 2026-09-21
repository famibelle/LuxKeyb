import SwiftUI

/// App conteneur : iOS n'installe pas un clavier tout seul, il faut l'app pour
/// le livrer. Elle explique l'activation et offre un champ pour l'essayer.
/// Les jeux, le carnet et les réglages viendront ici, pas dans l'extension
/// (qui est plafonnée en mémoire).
struct ContentView: View {
    @State private var essai = ""

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Activer le clavier")) {
                    Text("1. Réglages › Général › Clavier › Claviers › Ajouter un clavier…")
                    Text("2. Choisissez « Lëtzebuergesch Clavier ».")
                    Text("3. Dans un champ de texte, touchez le globe pour passer d'un clavier à l'autre.")
                }
                Section(header: Text("Essayer")) {
                    TextField("Schreift hei …", text: $essai)
                }
            }
            .navigationTitle("Lëtzebuergesch Clavier")
        }
        .navigationViewStyle(.stack)
    }
}
