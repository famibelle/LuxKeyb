package com.example.kreyolkeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.Log

/**
 * Palette du clavier, en clair et en sombre.
 *
 * Elle existe parce que quatre surfaces se partagent les mêmes couleurs — les
 * touches ([KeyboardLayoutManager]), la barre de suggestions (le service), la popup
 * d'appui long ([AccentHandler]) et le panneau emoji ([EmojiPickerView]) — et
 * qu'elles les écrivaient chacune en littéral. Un thème n'est pas ajoutable tant
 * qu'une couleur peut être décidée à son point d'usage.
 *
 * ### Le drapeau ne s'inverse pas
 *
 * Le rouge Pantone 032 et le bleu ciel Pantone 299 **sont identiques dans les deux
 * thèmes**. Ce sont eux qui rendent ce clavier reconnaissable ; les assombrir
 * donnerait un clavier générique. Ils n'ont pas besoin de bouger : blanc sur ce
 * rouge donne 4,22:1 et encre sombre sur ce bleu 5,93:1, des rapports qui ne
 * dépendent pas de ce qui les entoure.
 *
 * Ce qui bascule, c'est donc uniquement le **blanc des touches de lettres**, qui
 * devient anthracite, et tout ce qui en découle : l'encre, la bordure, le fond du
 * clavier, la popup. Le clavier sombre reste rouge et bleu.
 *
 * ### Contrastes mesurés (WCAG, texte sur fond)
 *
 * Chaque valeur sombre égale ou dépasse son homologue claire :
 *
 * | | clair | sombre |
 * |---|---|---|
 * | lettre sur sa touche | 17,40:1 | 12,42:1 |
 * | majuscule enclenchée | 4,35:1 | 4,62:1 |
 * | glyphe sur le rouge | 4,22:1 | 4,22:1 |
 * | glyphe sur le bleu | 5,93:1 | 5,93:1 |
 * | aperçu d'appui long | 3,45:1 | 5,16:1 |
 * | accent dans la popup | 12,63:1 | 11,09:1 |
 *
 * Les valeurs claires sont celles d'avant ce thème, reprises telles quelles : le
 * clavier clair ne change pas d'un pixel.
 */
object KeyboardTheme {

    private const val TAG = "KeyboardTheme"

    /** Ce que l'utilisateur choisit dans l'écran de réglages. */
    enum class Mode(val cle: String, val libelle: String) {
        SYSTEME("systeme", "Comme le téléphone"),
        CLAIR("clair", "Toujours clair"),
        SOMBRE("sombre", "Toujours sombre");

        companion object {
            /** Tolérante : une clé inconnue (préférence d'une version future,
             *  fichier recopié à la main) retombe sur le défaut plutôt que de jeter. */
            fun depuisCle(cle: String?): Mode =
                entries.firstOrNull { it.cle == cle } ?: SYSTEME
        }
    }

    /**
     * Les couleurs dont les quatre surfaces ont besoin, résolues.
     *
     * Des `Int` et non des chaînes hexadécimales : c'est la forme qu'attendent
     * `setColor`, `setTextColor` et `setStroke`, et les touches sont restylées à
     * chaque changement d'état du shift — autant ne pas reparser un littéral à
     * chaque fois.
     */
    data class Palette(
        /** Rouge du drapeau : entrée, changements de mode. Identique en clair et sombre. */
        val accent: Int,
        /** Bleu ciel du drapeau : barre d'espace et ponctuation. Identique aussi. */
        val secondaire: Int,
        /** Fond d'une touche de lettre. C'est lui qui bascule. */
        val touche: Int,
        /** Fond de la touche majuscule quand elle est enclenchée. */
        val toucheActive: Int,
        /** Contour d'une touche, qui la détache du fond du clavier. */
        val bordure: Int,
        /** Encre d'un glyphe posé sur [touche]. */
        val encre: Int,
        /** Encre atténuée : majuscule enclenchée, libellés secondaires. */
        val encreAttenuee: Int,
        /** Encre d'un glyphe posé sur [accent]. */
        val encreSurAccent: Int,
        /** Encre d'un glyphe posé sur [secondaire]. */
        val encreSurSecondaire: Int,
        /** Aperçu d'appui long dans le coin d'une touche claire. */
        val apercu: Int,
        /** Le même, sur une touche rouge. */
        val apercuSurAccent: Int,
        /** Ombre portée sous un glyphe posé sur [touche]. */
        val ombreSurTouche: Int,
        /** Ombre portée sous un glyphe posé sur [accent]. */
        val ombreSurAccent: Int,
        /** Fond derrière les touches. */
        val fondClavier: Int,
        /** Fond de la barre de suggestions. */
        val fondSuggestions: Int,
        /** Popup d'appui long : dégradé du haut, dégradé du bas, contour. */
        val popupHaut: Int,
        val popupBas: Int,
        val popupBordure: Int,
        /** Bouton d'accent de la popup : dégradé haut, bas, contour, encre. */
        val popupAccentHaut: Int,
        val popupAccentBas: Int,
        val popupAccentBordure: Int,
        val popupAccentEncre: Int,
        /** Bouton du caractère de base, volontairement en retrait. */
        val popupBaseHaut: Int,
        val popupBaseBas: Int,
        val popupBaseBordure: Int,
        val popupBaseEncre: Int,
        /** Fond de l'onglet actif du panneau emoji. */
        val emojiOngletActif: Int
    )

    // Le rouge et le bleu du drapeau, les deux seules couleurs communes aux deux
    // thèmes. Nommées ici pour que leur unicité soit visible dans le code, et non
    // seulement dans un commentaire.
    private val ROUGE = Color.parseColor("#ED2939")
    private val BLEU = Color.parseColor("#00A1DE")
    private const val OMBRE_CLAIRE = "#40FFFFFF"
    private const val OMBRE_SOMBRE = "#40000000"

    private val CLAIR = Palette(
        accent = ROUGE,
        secondaire = BLEU,
        touche = Color.parseColor("#FFFFFF"),
        toucheActive = Color.parseColor("#E0E0E0"),
        bordure = Color.parseColor("#D0D0D0"),
        encre = Color.parseColor("#1A1A1A"),
        encreAttenuee = Color.parseColor("#666666"),
        encreSurAccent = Color.parseColor("#FFFFFF"),
        encreSurSecondaire = Color.parseColor("#1A1A1A"),
        apercu = Color.parseColor("#99333333"),
        apercuSurAccent = Color.parseColor("#CCFFFFFF"),
        ombreSurTouche = Color.parseColor(OMBRE_SOMBRE),
        ombreSurAccent = Color.parseColor(OMBRE_CLAIRE),
        fondClavier = Color.parseColor("#F5F5F5"),
        fondSuggestions = Color.parseColor("#FFFFFF"),
        popupHaut = Color.parseColor("#FFFFFF"),
        popupBas = Color.parseColor("#F8F8F8"),
        popupBordure = Color.parseColor("#E0E0E0"),
        popupAccentHaut = Color.parseColor("#FFFFFF"),
        popupAccentBas = Color.parseColor("#F0F0F0"),
        popupAccentBordure = Color.parseColor("#C0C0C0"),
        popupAccentEncre = Color.parseColor("#333333"),
        popupBaseHaut = Color.parseColor("#F5F5F5"),
        popupBaseBas = Color.parseColor("#E8E8E8"),
        popupBaseBordure = Color.parseColor("#D0D0D0"),
        popupBaseEncre = Color.parseColor("#666666"),
        emojiOngletActif = Color.parseColor("#E0F2E9")
    )

    // Le fond du clavier est plus sombre que les touches, et non l'inverse comme en
    // clair : c'est ce qui fait lire les touches comme posées dessus. Une touche
    // plus sombre que son fond se lirait comme un trou.
    private val SOMBRE = Palette(
        accent = ROUGE,
        secondaire = BLEU,
        touche = Color.parseColor("#2B2B2B"),
        toucheActive = Color.parseColor("#454545"),
        bordure = Color.parseColor("#4A4A4A"),
        encre = Color.parseColor("#F0F0F0"),
        encreAttenuee = Color.parseColor("#B4B4B4"),
        encreSurAccent = Color.parseColor("#FFFFFF"),
        encreSurSecondaire = Color.parseColor("#1A1A1A"),
        apercu = Color.parseColor("#99E8E8E8"),
        apercuSurAccent = Color.parseColor("#CCFFFFFF"),
        ombreSurTouche = Color.parseColor(OMBRE_SOMBRE),
        ombreSurAccent = Color.parseColor(OMBRE_CLAIRE),
        fondClavier = Color.parseColor("#131313"),
        fondSuggestions = Color.parseColor("#202020"),
        popupHaut = Color.parseColor("#2B2B2B"),
        popupBas = Color.parseColor("#242424"),
        popupBordure = Color.parseColor("#454545"),
        popupAccentHaut = Color.parseColor("#333333"),
        popupAccentBas = Color.parseColor("#2B2B2B"),
        popupAccentBordure = Color.parseColor("#4A4A4A"),
        popupAccentEncre = Color.parseColor("#F0F0F0"),
        popupBaseHaut = Color.parseColor("#232323"),
        popupBaseBas = Color.parseColor("#1E1E1E"),
        popupBaseBordure = Color.parseColor("#3A3A3A"),
        popupBaseEncre = Color.parseColor("#909090"),
        emojiOngletActif = Color.parseColor("#26382D")
    )

    // Résolue une fois par prise de focus plutôt qu'à chaque touche : le service
    // survit au passage dans l'écran de réglages, donc la valeur ne peut pas être
    // figée au démarrage, mais elle n'a pas non plus à relire les préférences
    // soixante fois par reconstruction du clavier. Même politique que KeyFeedback.
    @Volatile
    private var courante: Palette = CLAIR

    /** La palette en vigueur. Sûre à appeler depuis le dessin d'une touche. */
    fun palette(): Palette = courante

    /**
     * Relit le réglage et la configuration système.
     *
     * Ne dit délibérément **pas** si la palette a changé. Une première version
     * renvoyait ce booléen et le service s'en servait pour décider de reconstruire
     * sa vue ; le clavier restait sombre après un passage en clair. L'écran de
     * réglages vit dans le même processus que le service, donc
     * [KeyboardPreferences.setThemeMode] appelait déjà cette méthode au moment du
     * clic : elle consommait le changement, et le `refresh` suivant, celui du
     * service, ne voyait plus rien à signaler.
     *
     * La question « faut-il reconstruire ? » ne se pose pas ici : elle se pose par
     * rapport à la palette avec laquelle les vues **ont été construites**, que seul
     * le service connaît. Il compare donc [palette] à celle qu'il a mémorisée.
     */
    fun refresh(context: Context) {
        val avant = courante
        courante = resoudre(KeyboardPreferences.themeMode(context), systemeEnSombre(context))
        if (avant !== courante) {
            Log.d(TAG, "Palette : ${if (courante === SOMBRE) "sombre" else "claire"}")
        }
    }

    /**
     * La table de décision, séparée de la lecture du contexte pour être
     * vérifiable hors appareil (voir `KeyboardThemeTest`).
     *
     * Un test JVM ne peut ni fabriquer un `Context` ni obtenir de vraies couleurs
     * (`android.graphics.Color` n'est pas implémenté hors appareil, et
     * `unitTests.returnDefaultValues` fait renvoyer 0 à `parseColor`) : les deux
     * palettes y sont donc indiscernables champ par champ. Elles restent deux
     * objets distincts, et c'est leur **identité** que le test compare, ce qui
     * suffit à couvrir les six cases de cette table.
     */
    internal fun resoudre(mode: Mode, systemeEnSombre: Boolean): Palette =
        when (mode) {
            Mode.CLAIR -> CLAIR
            Mode.SOMBRE -> SOMBRE
            Mode.SYSTEME -> if (systemeEnSombre) SOMBRE else CLAIR
        }

    /**
     * Le téléphone est-il en mode sombre ?
     *
     * `UI_MODE_NIGHT_UNDEFINED` compte comme clair : c'est ce que renvoient les
     * appareils antérieurs au mode sombre système (minSdk 21, le mode nuit n'est
     * arrivé qu'en 29), pour lesquels « comme le téléphone » ne peut vouloir dire
     * que clair.
     */
    private fun systemeEnSombre(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
}
