package com.example.kreyolkeyboard

import android.content.Context
import androidx.annotation.StringRes

/**
 * Un texte d'interface qui n'est pas encore écrit : une ressource et ses
 * arguments, résolus dans la langue du téléphone au moment de l'afficher.
 *
 * Il sert aux modèles sans `Context` (les leurres de Zuelwuert, par exemple)
 * qui expliquent leurs réponses : ils restent testables hors appareil, et le
 * test compare la ressource choisie plutôt qu'une phrase française.
 */
class TexteRessource(@StringRes val id: Int, vararg val args: Any) {

    fun texte(context: Context): String = context.getString(id, *args)

    override fun equals(other: Any?): Boolean =
        other is TexteRessource && other.id == id && other.args.contentEquals(args)

    override fun hashCode(): Int = 31 * id + args.contentHashCode()

    override fun toString(): String = "TexteRessource($id, ${args.toList()})"
}
