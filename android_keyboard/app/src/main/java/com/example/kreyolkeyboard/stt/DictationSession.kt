package com.example.kreyolkeyboard.stt

/**
 * Ce que l'IME attend d'une dictée.
 *
 * Une seule mise en œuvre depuis le 2 octobre 2026 : [LuxAsrSession], qui
 * délègue au service en ligne de l'Université du Luxembourg. La dictée
 * embarquée (whisper.cpp dans le téléphone) a été retirée, voir [SttSession].
 * L'interface reste, pour que le clavier ne dépende que de ce contrat et
 * qu'une autre source de transcription tienne en une ligne au point de
 * construction.
 */
interface DictationSession {
    /** Le micro est ouvert, ou sur le point de l'être : un appui doit arrêter. */
    val isActive: Boolean

    /** La session n'est pas revenue au repos, finalisation comprise. */
    val isBusy: Boolean

    fun start()
    fun stop()
    fun cancel()

    fun shutdown()
}
