// Harnais de mesure de la dictée — hôte, hors APK.
//
// Il ne s'agit pas de « faire tourner whisper pour voir » : le but est de
// mesurer ce que l'application exécute, ce qui impose deux choses.
//
// 1. Les paramètres de décodage sont recopiés de whisper_jni.cpp sans exception
//    (voir PARITÉ ci-dessous). Un seul écart — temperature_inc laissé à sa
//    valeur par défaut, par exemple — et l'on mesure un autre logiciel.
// 2. Le mode `stream` rejoue la cadence de SttSession.kt sur une horloge
//    virtuelle : l'audio arrive par blocs comme le ferait AudioRecorder, mais
//    chaque passe whisper est réellement exécutée et chronométrée. Seule
//    l'arrivée de l'audio est simulée, jamais le coût du calcul.
//
// Entrée : PCM brut, float32, mono, 16 kHz (ffmpeg -f f32le). Pas de WAV à
// analyser, pas de dépendance de plus.
// Sortie : JSON Lines sur stdout, une ligne par passe.

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <chrono>
#include <string>
#include <vector>

#include "whisper.h"

namespace {

constexpr int SAMPLE_RATE = 16000;

// --- Constantes reprises de SttSession.kt / SttEngine.kt ---------------------
constexpr int MAX_UTTERANCE_SAMPLES = 30 * SAMPLE_RATE;      // fenêtre mel
constexpr int PARTIAL_STEP_SAMPLES  = (int)(0.6 * SAMPLE_RATE);
constexpr int MIN_PARTIAL_SAMPLES   = (int)(0.6 * SAMPLE_RATE);
constexpr int MIN_FINAL_SAMPLES     = (int)(0.3 * SAMPLE_RATE);
// Seuil des 100 trames de whisper_full() : en deçà, zéro segment et aucune
// erreur. SttEngine complète par du silence ; on fait pareil.
constexpr int MIN_WHISPER_SAMPLES   = (int)(1.2 * SAMPLE_RATE);

double now_ms() {
    using namespace std::chrono;
    return duration<double, std::milli>(steady_clock::now().time_since_epoch()).count();
}

std::string json_escape(const std::string &s) {
    std::string out;
    out.reserve(s.size() + 16);
    for (unsigned char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) { char b[8]; snprintf(b, sizeof b, "\\u%04x", c); out += b; }
                else out += (char) c;
        }
    }
    return out;
}

std::string trim(const std::string &s) {
    size_t a = s.find_first_not_of(" \t\n\r");
    if (a == std::string::npos) return "";
    size_t b = s.find_last_not_of(" \t\n\r");
    return s.substr(a, b - a + 1);
}

std::vector<float> read_f32(const char *path) {
    std::vector<float> pcm;
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "illisible: %s\n", path); return pcm; }
    fseek(f, 0, SEEK_END);
    long bytes = ftell(f);
    fseek(f, 0, SEEK_SET);
    pcm.resize(bytes / sizeof(float));
    if (fread(pcm.data(), 1, bytes, f) != (size_t) bytes) pcm.clear();
    fclose(f);
    return pcm;
}

// PARITÉ — chaque ligne de cette fonction a son jumeau dans whisper_jni.cpp.
// Toute divergence invalide la mesure : la modifier sans modifier l'autre est
// un bug, pas un réglage.
whisper_full_params app_params(int n_threads, bool single_segment) {
    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.greedy.best_of        = 1;
    p.beam_search.beam_size = 1;
    p.n_threads        = n_threads;
    p.language         = "lb";
    p.translate        = false;
    p.detect_language  = false;
    p.print_realtime   = false;
    p.print_progress   = false;
    p.print_timestamps = false;
    p.print_special    = false;
    p.no_timestamps    = true;
    p.single_segment   = single_segment;
    p.no_context       = true;
    p.suppress_blank   = true;
    p.suppress_nst     = true;
    p.temperature_inc  = 0.0f;   // sans quoi : jusqu'à six redécodages
    return p;
}

// Une passe, telle que SttEngine.transcribe l'exécute, remplissage compris.
// `ms` reçoit le temps mesuré, `padded` la taille réellement soumise.
std::string one_pass(whisper_context *ctx, const float *data, int n,
                     int n_threads, bool single_segment,
                     double *ms, int *padded) {
    std::vector<float> buf;
    const float *pcm = data;
    if (n < MIN_WHISPER_SAMPLES) {
        buf.assign(data, data + n);
        buf.resize(MIN_WHISPER_SAMPLES, 0.0f);   // complété par du silence
        pcm = buf.data();
        n   = MIN_WHISPER_SAMPLES;
    }
    *padded = n;

    whisper_full_params p = app_params(n_threads, single_segment);
    const double t0 = now_ms();
    const int rc = whisper_full(ctx, p, pcm, n);
    *ms = now_ms() - t0;
    if (rc != 0) return "";

    std::string out;
    const int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; ++i) out += whisper_full_get_segment_text(ctx, i);
    return trim(out);
}

const char *g_file = "";

void emit(const char *kind, int idx, int audio_samples, int padded,
          double ms, double t_launch, double t_shown, const std::string &text) {
    printf("{\"file\":\"%s\",\"kind\":\"%s\",\"i\":%d,\"audio_s\":%.3f,\"padded_s\":%.3f,"
           "\"ms\":%.1f,\"t_launch_ms\":%.1f,\"t_shown_ms\":%.1f,\"text\":\"%s\"}\n",
           g_file, kind, idx, audio_samples / (double) SAMPLE_RATE,
           padded / (double) SAMPLE_RATE, ms, t_launch, t_shown,
           json_escape(text).c_str());
    fflush(stdout);
}

} // namespace

int main(int argc, char **argv) {
    const char *model = nullptr, *input = nullptr, *mode = "full";
    int n_threads = 3;         // ce que SttEngine.threadCount() choisit ici
    int chunk = 1024;          // bloc AudioRecorder : minBuffer/2, ~64 ms

    for (int i = 1; i < argc; ++i) {
        std::string a = argv[i];
        auto next = [&]() { return (i + 1 < argc) ? argv[++i] : ""; };
        if      (a == "--model")   model = next();
        else if (a == "--input")   input = next();
        else if (a == "--mode")    mode  = next();
        else if (a == "--threads") n_threads = atoi(next());
        else if (a == "--chunk")   chunk = atoi(next());
    }
    if (!model || !input) {
        fprintf(stderr, "usage: lux_bench --model M.bin --input X.f32 "
                        "[--mode full|stream] [--threads N] [--chunk N]\n");
        return 2;
    }

    std::vector<std::string> inputs;
    if (input[0] == '@') {           // @liste.txt : un chemin par ligne
        FILE *f = fopen(input + 1, "r");
        if (!f) { fprintf(stderr, "liste illisible: %s\n", input + 1); return 3; }
        char line[4096];
        while (fgets(line, sizeof line, f)) {
            std::string p = trim(line);
            if (!p.empty()) inputs.push_back(p);
        }
        fclose(f);
    } else {
        inputs.push_back(input);
    }

    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;                       // comme dans l'APK

    const double t0 = now_ms();
    whisper_context *ctx = whisper_init_from_file_with_params(model, cp);
    const double load_ms = now_ms() - t0;
    if (!ctx) { fprintf(stderr, "chargement du modèle échoué\n"); return 4; }

    printf("{\"kind\":\"load\",\"ms\":%.1f,\"threads\":%d,\"files\":%zu}\n",
           load_ms, n_threads, inputs.size());
    fflush(stdout);

    for (const std::string &path : inputs) {
    g_file = path.c_str();
    std::vector<float> pcm = read_f32(path.c_str());
    if (pcm.empty()) { fprintf(stderr, "vide: %s\n", path.c_str()); continue; }

    if (strcmp(mode, "full") == 0) {
        double ms; int padded;
        std::string text = one_pass(ctx, pcm.data(), (int) pcm.size(),
                                    n_threads, /*single_segment=*/false,
                                    &ms, &padded);
        emit("final", 0, (int) pcm.size(), padded, ms, 0.0, ms, text);
    } else {
        // --- Rejeu de SttSession sur horloge virtuelle -----------------------
        // t_wall : millisecondes écoulées depuis l'appui sur le micro.
        // busy_until : le worker mono-thread est pris jusque-là ; le test
        // « libre ? » reproduit exactement l'échec du compareAndSet, donc les
        // tours sautés quand une passe dure plus qu'un pas.
        const int n_total = std::min((int) pcm.size(), MAX_UTTERANCE_SAMPLES);
        const double chunk_ms = 1000.0 * chunk / SAMPLE_RATE;

        double t_wall = load_ms;      // le micro ne s'ouvre qu'après le chargement
        double busy_until = t_wall;
        int last_partial_at = 0, idx = 0, t_audio = 0;

        while (t_audio < n_total) {
            t_audio = std::min(t_audio + chunk, n_total);
            t_wall += chunk_ms;

            if (t_audio - last_partial_at < PARTIAL_STEP_SAMPLES) continue;
            if (t_audio < MIN_PARTIAL_SAMPLES) continue;   // pas non consommé
            if (t_wall < busy_until) continue;             // CAS en échec

            last_partial_at = t_audio;
            double ms; int padded;
            std::string text = one_pass(ctx, pcm.data(), t_audio, n_threads,
                                        /*single_segment=*/true, &ms, &padded);
            busy_until = t_wall + ms;
            emit("partial", idx++, t_audio, padded, ms, t_wall, t_wall + ms, text);
        }

        // stop() : abort de la passe en vol puis passe finale sur tout le tampon.
        const double t_stop = t_wall;
        double ms = 0; int padded = 0;
        std::string text;
        if (n_total >= MIN_FINAL_SAMPLES)
            text = one_pass(ctx, pcm.data(), n_total, n_threads, false, &ms, &padded);
        emit("final", idx, n_total, padded, ms, t_stop, t_stop + ms, text);
    }
    }

    whisper_free(ctx);
    return 0;
}
