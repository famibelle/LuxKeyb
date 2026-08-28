// Pont JNI entre SttEngine.kt et whisper.cpp.
//
// Le contrat est volontairement minuscule — ouvrir, transcrire, fermer — parce
// que tout ce qui touche au découpage temps réel (fenêtre glissante, VAD,
// résultats partiels) vit côté Kotlin, où il est testable sans NDK.

#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <atomic>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "LuxSTT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

// Drapeau d'annulation partagé avec whisper_full() via abort_callback. Une
// transcription de 30 s de tampon peut durer plusieurs secondes sur un mobile
// d'entrée de gamme ; sans ce drapeau, relâcher le micro n'aurait aucun effet
// avant la fin du décodage en cours.
std::atomic<bool> g_abort{false};

bool abort_requested(void * /*user_data*/) {
    return g_abort.load(std::memory_order_relaxed);
}

} // namespace

extern "C" {

// Charge le modèle ggml depuis les assets de l'APK, sans copie sur disque.
//
// L'asset est déclaré noCompress dans build.gradle : AAsset_getBuffer() renvoie
// alors un pointeur mmap'é sur la région de l'APK, là où un asset compressé
// forcerait une décompression intégrale en RAM. whisper_init_from_buffer_*
// recopie les poids dans ses propres tenseurs, donc l'asset peut être refermé
// dès l'initialisation faite.
JNIEXPORT jlong JNICALL
Java_com_example_kreyolkeyboard_stt_SttEngine_nativeInitFromAsset(
        JNIEnv *env, jobject /*thiz*/, jobject assetManager, jstring assetPath) {

    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("AAssetManager_fromJava a renvoyé null");
        return 0;
    }

    const char *path = env->GetStringUTFChars(assetPath, nullptr);
    AAsset *asset = AAssetManager_open(mgr, path, AASSET_MODE_BUFFER);
    env->ReleaseStringUTFChars(assetPath, path);

    if (asset == nullptr) {
        LOGE("modèle introuvable dans les assets");
        return 0;
    }

    const void *buffer = AAsset_getBuffer(asset);
    const off_t size = AAsset_getLength(asset);
    if (buffer == nullptr || size <= 0) {
        LOGE("asset illisible (taille=%ld)", (long) size);
        AAsset_close(asset);
        return 0;
    }

    whisper_context_params cparams = whisper_context_default_params();
    // Pas de délégué GPU : l'IME doit démarrer vite et de façon identique sur
    // tous les appareils, et tiny quantifié tient largement le temps réel en CPU.
    cparams.use_gpu = false;

    whisper_context *ctx = whisper_init_from_buffer_with_params(
            const_cast<void *>(buffer), (size_t) size, cparams);

    AAsset_close(asset);

    if (ctx == nullptr) {
        LOGE("whisper_init_from_buffer_with_params a échoué");
        return 0;
    }

    LOGI("modèle chargé (%ld octets)", (long) size);
    return reinterpret_cast<jlong>(ctx);
}

// Transcrit un tampon PCM mono 16 kHz normalisé dans [-1, 1].
//
// `singleSegment` sert aux hypothèses intermédiaires : il force whisper à ne
// rendre qu'un segment, ce qui évite qu'une fenêtre glissante re-découpe la
// phrase à chaque passe et fasse clignoter le texte en composition.
JNIEXPORT jstring JNICALL
Java_com_example_kreyolkeyboard_stt_SttEngine_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong ctxPtr, jfloatArray audio,
        jint nThreads, jboolean singleSegment) {

    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    const jsize nSamples = env->GetArrayLength(audio);
    jfloat *pcm = env->GetFloatArrayElements(audio, nullptr);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    // Décodage glouton : le beam search multiplie le temps de décodage par ~4
    // pour un gain marginal sur des énoncés courts, et le temps réel est ici
    // la contrainte dure.
    wparams.greedy.best_of      = 1;
    wparams.beam_search.beam_size = 1;

    wparams.n_threads        = nThreads;
    wparams.language         = "lb";
    wparams.translate        = false;
    wparams.detect_language  = false;
    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = false;
    wparams.print_special    = false;
    wparams.no_timestamps    = true;
    wparams.single_segment   = singleSegment == JNI_TRUE;
    // Chaque passe repart du signal brut : réutiliser la transcription
    // précédente comme amorce fait diverger le texte quand la fenêtre glisse,
    // le modèle s'accrochant à une hypothèse que l'audio ne soutient plus.
    wparams.no_context       = true;
    wparams.suppress_blank   = true;
    wparams.suppress_nst     = true;

    // Pas de repli en température. Par défaut whisper rejoue le décodage
    // jusqu'à six fois, en montant la température, quand l'entropie ou la
    // log-probabilité du résultat sortent des seuils — ce qui arrive
    // systématiquement sur du silence ou du bruit de fond. Pour un clavier,
    // c'est une latence non bornée après l'appui sur « stop » : chaque repli
    // recommence un décodage complet. Une seule passe gloutonne rend le temps
    // de réponse prévisible, au prix de quelques transcriptions bancales sur
    // les passages inaudibles — que l'utilisateur voit et peut refaire.
    wparams.temperature_inc = 0.0f;

    wparams.abort_callback           = abort_requested;
    wparams.abort_callback_user_data = nullptr;

    g_abort.store(false, std::memory_order_relaxed);

    const int rc = whisper_full(ctx, wparams, pcm, nSamples);

    env->ReleaseFloatArrayElements(audio, pcm, JNI_ABORT); // lecture seule

    if (rc != 0) {
        LOGE("whisper_full a échoué (rc=%d)", rc);
        return env->NewStringUTF("");
    }

    std::string out;
    const int nSegments = whisper_full_n_segments(ctx);
    for (int i = 0; i < nSegments; ++i) {
        out += whisper_full_get_segment_text(ctx, i);
    }

    return env->NewStringUTF(out.c_str());
}

// Demande l'interruption de la transcription en cours. Sans effet si aucune
// n'est en vol : le drapeau est remis à zéro au début de chaque passe.
JNIEXPORT void JNICALL
Java_com_example_kreyolkeyboard_stt_SttEngine_nativeAbort(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    g_abort.store(true, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_example_kreyolkeyboard_stt_SttEngine_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("contexte libéré");
    }
}

} // extern "C"
