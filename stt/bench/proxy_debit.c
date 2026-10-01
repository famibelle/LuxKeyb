/*
 * Proxy HTTP CONNECT qui bride le sens montant, à faire tourner **sur le
 * téléphone** (adb shell), pour éprouver la dictée en ligne sous un débit
 * choisi.
 *
 * Pourquoi sur le téléphone. Brider depuis le poste ne marche pas : la pile
 * réseau de l'émulateur, comme le tunnel `adb reverse`, accuse réception de
 * tout ce qu'on lui donne puis bride en aval, et l'application ne voit jamais
 * de lenteur (près de 2 Mo absorbés en 70 s, mesuré le 1er octobre 2026). Ici
 * l'application parle à ce proxy sur la boucle locale ; il lit lentement, avec
 * un tampon de réception minuscule, si bien que le contrôle de flux TCP fait
 * remonter la lenteur jusqu'à la file d'envoi d'OkHttp, comme un vrai lien
 * saturé.
 *
 * Le débit (octets par seconde, sens montant) est relu dans un fichier toutes
 * les 200 ms : on peut le changer en pleine dictée.
 *
 *   proxy_debit PORT FICHIER_DEBIT JOURNAL
 *
 * Compilation : NDK, aarch64-linux-android21-clang -O2 -o proxy_debit proxy_debit.c
 */
#include <errno.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static const char *fichier_debit;
static FILE *journal;

static double maintenant(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec + t.tv_nsec / 1e9;
}

static long horloge_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_REALTIME, &t);
    return t.tv_sec * 1000L + t.tv_nsec / 1000000L;
}

static long lire_debit(void) {
    FILE *f = fopen(fichier_debit, "r");
    long d = 0;
    if (f) { if (fscanf(f, "%ld", &d) != 1) d = 0; fclose(f); }
    return d > 0 ? d : 1000000000L;   /* fichier absent ou 0 : pas de bridage */
}

static int ecrire_tout(int fd, const char *b, ssize_t n) {
    while (n > 0) {
        ssize_t k = write(fd, b, n);
        if (k < 0) { if (errno == EINTR) continue; return -1; }
        b += k; n -= k;
    }
    return 0;
}

static void servir(int cli) {
    char req[4096];
    ssize_t n = 0;
    /* En-tête CONNECT, octet par octet jusqu'à la ligne vide : on ne doit pas
       avaler le début du flux TLS qui suit. */
    while (n < (ssize_t)sizeof req - 1) {
        ssize_t k = read(cli, req + n, 1);
        if (k <= 0) return;
        n += k;
        if (n >= 4 && memcmp(req + n - 4, "\r\n\r\n", 4) == 0) break;
    }
    req[n] = 0;
    char hote[256], port[16];
    if (sscanf(req, "CONNECT %255[^:]:%15s", hote, port) != 2) return;

    struct addrinfo h = {0}, *r;
    h.ai_family = AF_UNSPEC;
    h.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(hote, port, &h, &r) != 0) return;
    int up = socket(r->ai_family, SOCK_STREAM, 0);
    if (up < 0 || connect(up, r->ai_addr, r->ai_addrlen) < 0) return;
    freeaddrinfo(r);
    int un = 1;
    setsockopt(up, IPPROTO_TCP, TCP_NODELAY, &un, sizeof un);
    const char *ok = "HTTP/1.1 200 Connection established\r\n\r\n";
    ecrire_tout(cli, ok, strlen(ok));
    fprintf(journal, "%ld CONNECT %s:%s\n", horloge_ms(), hote, port);
    fflush(journal);

    double jetons = 0, t_prec = maintenant(), t_debit = 0, t_log = maintenant();
    long debit = lire_debit(), monte = 0, monte_log = 0;
    char buf[65536];
    for (;;) {
        double t = maintenant();
        if (t - t_debit > 0.2) { debit = lire_debit(); t_debit = t; }
        jetons += (t - t_prec) * debit;
        t_prec = t;
        if (jetons > debit * 0.2) jetons = debit * 0.2;   /* rafale ≤ 200 ms */

        struct pollfd p[2] = {{cli, jetons >= 1 ? POLLIN : 0, 0}, {up, POLLIN, 0}};
        if (poll(p, 2, 20) < 0 && errno != EINTR) break;

        if (p[0].revents & (POLLIN | POLLHUP | POLLERR)) {
            size_t max = jetons < sizeof buf ? (size_t)jetons : sizeof buf;
            ssize_t k = read(cli, buf, max > 0 ? max : 1);
            if (k <= 0) break;
            if (ecrire_tout(up, buf, k) < 0) break;
            jetons -= k;
            monte += k;
        }
        if (p[1].revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t k = read(up, buf, sizeof buf);
            if (k <= 0) break;
            if (ecrire_tout(cli, buf, k) < 0) break;
        }
        if (t - t_log >= 1.0) {
            fprintf(journal, "%ld debit=%ld monte=%ld (+%ld/s)\n",
                    horloge_ms(), debit, monte, monte - monte_log);
            fflush(journal);
            monte_log = monte;
            t_log = t;
        }
    }
    fprintf(journal, "%ld FIN monte=%ld\n", horloge_ms(), monte);
    fflush(journal);
    close(up);
}

int main(int argc, char **argv) {
    if (argc != 4) { fprintf(stderr, "usage: %s PORT FICHIER_DEBIT JOURNAL\n", argv[0]); return 2; }
    fichier_debit = argv[2];
    journal = fopen(argv[3], "a");
    if (!journal) return 1;
    signal(SIGCHLD, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);

    int s = socket(AF_INET, SOCK_STREAM, 0);
    int un = 1, petit = 4096;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &un, sizeof un);
    /* Avant listen() : les connexions acceptées en héritent, et une taille
       fixée désactive l'ajustement automatique du noyau. */
    setsockopt(s, SOL_SOCKET, SO_RCVBUF, &petit, sizeof petit);
    struct sockaddr_in a = {0};
    a.sin_family = AF_INET;
    a.sin_port = htons(atoi(argv[1]));
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(s, (struct sockaddr *)&a, sizeof a) < 0 || listen(s, 8) < 0) { perror("bind"); return 1; }
    fprintf(journal, "%ld écoute 127.0.0.1:%s\n", horloge_ms(), argv[1]);
    fflush(journal);
    for (;;) {
        int c = accept(s, NULL, NULL);
        if (c < 0) continue;
        if (fork() == 0) { close(s); servir(c); _exit(0); }
        close(c);
    }
}
