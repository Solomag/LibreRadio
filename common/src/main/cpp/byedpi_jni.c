/*
 * Runs ByeDPI (byedpi/, https://github.com/hufrea/byedpi, MIT) inside the app: a local SOCKS5 proxy that
 * splits and disguises the first packets of each connection, so a network that blocks or cuts connections
 * by what it reads in them lets them through. Kotlin side: shared/model/net/ByeDpi.kt.
 *
 * byedpi's main() is compiled as byedpi_main() (see CMakeLists.txt). It parses the arguments, listens and
 * serves until its listening socket is shut down. Only one runs at a time.
 */
#include <jni.h>
#include <getopt.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>

#include "byedpi/params.h"

int byedpi_main(int argc, char **argv);

/* Listening socket of the running proxy (proxy.c). */
extern int server_fd;

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static struct params defaults;
static int have_defaults = 0;
static int running = 0;

JNIEXPORT jint JNICALL
Java_com_yuriy_openradio_shared_model_net_ByeDpi_nativeRun(JNIEnv *env, jclass cls, jobjectArray args)
{
    pthread_mutex_lock(&lock);
    if (running) {
        pthread_mutex_unlock(&lock);
        return -2;
    }
    running = 1;
    // byedpi keeps its settings in a global: start each run from the values it had before the first one.
    if (!have_defaults) {
        defaults = params;
        have_defaults = 1;
    } else {
        params = defaults;
    }
    server_fd = -1;
    optind = 0;
    pthread_mutex_unlock(&lock);

    int count = (*env)->GetArrayLength(env, args);
    char **argv = calloc(count + 2, sizeof(char *));
    argv[0] = strdup("ciadpi");
    for (int i = 0; i < count; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *chars = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i + 1] = strdup(chars);
        (*env)->ReleaseStringUTFChars(env, arg, chars);
        (*env)->DeleteLocalRef(env, arg);
    }

    int result = byedpi_main(count + 1, argv);

    for (int i = 0; i <= count; i++) {
        free(argv[i]);
    }
    free(argv);

    pthread_mutex_lock(&lock);
    running = 0;
    server_fd = -1;
    pthread_mutex_unlock(&lock);
    return result;
}

JNIEXPORT void JNICALL
Java_com_yuriy_openradio_shared_model_net_ByeDpi_nativeStop(JNIEnv *env, jclass cls)
{
    pthread_mutex_lock(&lock);
    if (running && server_fd >= 0) {
        shutdown(server_fd, SHUT_RDWR);
    }
    pthread_mutex_unlock(&lock);
}
