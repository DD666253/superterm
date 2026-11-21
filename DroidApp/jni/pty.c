#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <signal.h>
#include <errno.h>

#ifndef TIOCSCTTY
#define TIOCSCTTY 0x540E
#endif

static void set_winsize(int fd, int rows, int cols) {
    struct winsize ws;
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jintArray JNICALL
Java_com_mucre_superterm_TermSession_nativeCreate(JNIEnv *env, jclass cls,
        jobjectArray cmdArr, jobjectArray envArr, jstring cwdStr,
        jint rows, jint cols) {
    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) return NULL;
    if (grantpt(master) != 0 || unlockpt(master) != 0) { close(master); return NULL; }
    char *slaveName = ptsname(master);
    if (!slaveName) { close(master); return NULL; }
    int slave = open(slaveName, O_RDWR | O_NOCTTY);
    if (slave < 0) { close(master); return NULL; }
    set_winsize(slave, rows, cols);

    int argc = (*env)->GetArrayLength(env, cmdArr);
    char **argv = (char **) calloc(argc + 1, sizeof(char *));
    int i;
    for (i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, cmdArr, i);
        argv[i] = (char *) (*env)->GetStringUTFChars(env, s, NULL);
    }
    argv[argc] = NULL;

    int envc = envArr ? (*env)->GetArrayLength(env, envArr) : 0;
    char **envp = (char **) calloc(envc + 1, sizeof(char *));
    for (i = 0; i < envc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, envArr, i);
        envp[i] = (char *) (*env)->GetStringUTFChars(env, s, NULL);
    }
    envp[envc] = NULL;
    const char *cwd = cwdStr ? (*env)->GetStringUTFChars(env, cwdStr, NULL) : "/";

    pid_t pid = fork();
    if (pid == 0) {
        close(master);
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        if (slave > 2) close(slave);
        if (cwd) chdir(cwd);
        execve(argv[0], argv, envp);
        _exit(127);
    }
    close(slave);
    for (i = 0; i < argc; i++) (*env)->ReleaseStringUTFChars(env, (jstring) (*env)->GetObjectArrayElement(env, cmdArr, i), argv[i]);
    for (i = 0; i < envc; i++) (*env)->ReleaseStringUTFChars(env, (jstring) (*env)->GetObjectArrayElement(env, envArr, i), envp[i]);
    if (cwd) (*env)->ReleaseStringUTFChars(env, cwdStr, cwd);
    free(argv); free(envp);
    if (pid <= 0) { close(master); return NULL; }

    jintArray out = (*env)->NewIntArray(env, 2);
    jint vals[2];
    vals[0] = master;
    vals[1] = (jint) pid;
    (*env)->SetIntArrayRegion(env, out, 0, 2, vals);
    return out;
}

JNIEXPORT void JNICALL
Java_com_mucre_superterm_TermSession_nativeResize(JNIEnv *env, jclass cls, jint fd, jint rows, jint cols, jint pid) {
    set_winsize(fd, rows, cols);
    if (pid > 0) kill((pid_t) pid, SIGWINCH);
}

JNIEXPORT jint JNICALL
Java_com_mucre_superterm_TermSession_nativeWait(JNIEnv *env, jclass cls, jint pid) {
    int status = 0;
    if (pid > 0) waitpid((pid_t) pid, &status, 0);
    return status;
}

JNIEXPORT void JNICALL
Java_com_mucre_superterm_TermSession_nativeClose(JNIEnv *env, jclass cls, jint fd) {
    if (fd >= 0) close(fd);
}
