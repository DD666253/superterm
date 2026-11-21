package com.mucre.superterm;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class TermSession {

    static {
        System.load(new File(System.getProperty("superterm.lib") , "libpty.so").getAbsolutePath());
    }

    private int masterFd = -1;
    private int pid = -1;
    private FileInputStream in;
    private FileOutputStream out;
    private FileDescriptor fd;

    public static TermSession create(String[] argv, String[] envp, String cwd, int rows, int cols) throws IOException {
        int[] res = nativeCreate(argv, envp, cwd, rows, cols);
        if (res == null || res.length < 2 || res[0] < 0) throw new IOException("pty create failed");
        TermSession s = new TermSession();
        s.masterFd = res[0];
        s.pid = res[1];
        ParcelFileDescriptor pfd = ParcelFileDescriptor.adoptFd(res[0]);
        s.fd = pfd.getFileDescriptor();
        s.in = new FileInputStream(s.fd);
        s.out = new FileOutputStream(s.fd);
        return s;
    }

    public FileInputStream getInput() { return in; }
    public FileOutputStream getOutput() { return out; }
    public int getPid() { return pid; }

    public void write(String text) {
        try { out.write(text.getBytes("UTF-8")); out.flush(); } catch (Exception ignored) {}
    }

    public void write(byte[] data) {
        try { out.write(data); out.flush(); } catch (Exception ignored) {}
    }

    public void resize(int rows, int cols) {
        if (masterFd >= 0) nativeResize(masterFd, rows, cols, pid);
    }

    public int waitFor() { return nativeWait(pid); }

    public void close() {
        try { if (masterFd >= 0) nativeClose(masterFd); } catch (Exception ignored) {}
        masterFd = -1;
    }

    private static native int[] nativeCreate(String[] argv, String[] envp, String cwd, int rows, int cols);
    private static native void nativeResize(int fd, int rows, int cols, int pid);
    private static native int nativeWait(int pid);
    private static native void nativeClose(int fd);
}
