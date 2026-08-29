package com.example.kreyolkeyboard.stt

/**
 * Ce que l'IME attend d'une dictée, quelle qu'en soit la mise en œuvre.
 *
 * Deux existent : [SttSession], qui fait tourner whisper sur l'appareil, et
 * [LuxAsrSession], qui délègue au service en ligne de l'Université du
 * Luxembourg. Elles ne se valent pas — l'une préserve la promesse que l'audio
 * ne quitte jamais l'appareil, l'autre la rompt en échange d'une qualité que le
 * matériel mobile ne permet pas d'atteindre — et le choix entre les deux est un
 * arbitrage de politique, pas d'implémentation. Cette interface existe pour que
 * l'arbitrage tienne en une ligne au point de construction, et non pour laisser
 * entendre qu'elles sont interchangeables.
 */
interface DictationSession {
    /** Le micro est ouvert, ou sur le point de l'être : un appui doit arrêter. */
    val isActive: Boolean

    /** La session n'est pas revenue au repos, finalisation comprise. */
    val isBusy: Boolean

    fun start()
    fun stop()
    fun cancel()

    /** Libère ce qui peut l'être dès que le clavier quitte l'écran. */
    fun releaseModel()

    fun shutdown()
}
