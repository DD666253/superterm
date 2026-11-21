package com.mucre.superterm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Handles the on-device layout and payload extraction for superterm. */
public final class Installer {

    public static final String PREFS = "superterm";

    private Installer() {}

    public static File homeDir(Context c) {
        return c.getFilesDir().getParentFile();
    }

    public static File driveDir(Context c) { return new File(homeDir(c), "drive"); }
    public static File usrDir(Context c) { return new File(driveDir(c), "usr"); }
    public static File binDir(Context c) { return new File(usrDir(c), "bin"); }
    public static File libDir(Context c) { return new File(usrDir(c), "lib"); }
    public static File etcDir(Context c) { return new File(usrDir(c), "etc"); }
    public static File shareDir(Context c) { return new File(usrDir(c), "share"); }
    public static File tmpDir(Context c) { return new File(usrDir(c), "tmp"); }
    public static File varDir(Context c) { return new File(usrDir(c), "var"); }
    public static File homeUserDir(Context c) { return new File(driveDir(c), "home"); }
    public static String prefix(Context c) { return usrDir(c).getAbsolutePath(); }
    public static String drivePath(Context c) { return driveDir(c).getAbsolutePath(); }

    public static String abiTag() {
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
        if (abi.startsWith("arm64")) return "arm64";
        if (abi.startsWith("armeabi")) return "arm";
        if (abi.startsWith("x86_64")) return "x86_64";
        if (abi.startsWith("x86")) return "x86";
        return "arm64";
    }

    public static String unameArch() {
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
        if (abi.equals("arm64-v8a")) return "aarch64";
        if (abi.startsWith("armeabi")) return "armv7l";
        if (abi.startsWith("x86_64")) return "x86_64";
        if (abi.startsWith("x86")) return "i686";
        return "aarch64";
    }

    public static boolean isInstalled(Context c) {
        return new File(binDir(c), "sh").exists() && new File(binDir(c), "fish").exists();
    }

    public static void install(Context c) throws IOException {
        File drive = driveDir(c);
        File usr = usrDir(c);
        mkdirs(binDir(c));
        mkdirs(new File(usr, "lib"));
        mkdirs(etcDir(c));
        mkdirs(shareDir(c));
        mkdirs(tmpDir(c));
        mkdirs(new File(usr, "var/pkg"));
        mkdirs(homeUserDir(c));

        String abi = abiTag();
        String payload = "payload/" + abi + ".zip";
        unzipAsset(c, payload, drive);

        // native pty bridge
        copyAsset(c, "lib/" + abi + ".so", new File(libDir(c), "libpty.so"));

        // skeleton config files
        copyAsset(c, "skel/bashrc", new File(etcDir(c), "bashrc"));
        copyAsset(c, "skel/config.fish", new File(etcDir(c), "fish/config.fish"));
        copyAsset(c, "skel/eggtool", new File(binDir(c), "eggtool"));
        copyAsset(c, "skel/pkg", new File(binDir(c), "pkg"));

        // OSDR is written here so it is readable without a shell, but bashrc recomputes it live
        String osdr = "Android-" + Build.VERSION.RELEASE + "-" + unameArch() + "-" + (hasRoot() ? "Root" : "DATA");
        writeText(new File(etcDir(c), "osdr"), osdr + "\n");

        chmodBinaries(binDir(c));
        new File(binDir(c), "eggtool").setExecutable(true, false);
        new File(binDir(c), "pkg").setExecutable(true, false);
        linkBusyboxApplets(c);

        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putBoolean("installed", true).putString("abi", abi).putString("osdr", osdr).apply();
    }

    private static void linkBusyboxApplets(Context c) {
        File bb = new File(binDir(c), "busybox");
        if (!bb.exists()) return;
        try {
            Process p = new ProcessBuilder(bb.getAbsolutePath(), "--list").redirectErrorStream(true).start();
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.contains("/")) continue;
                File link = new File(binDir(c), name);
                if (link.exists()) continue;
                try {
                    android.system.Os.symlink("busybox", link.getAbsolutePath());
                } catch (Exception ignored) {
                    // some applet names may already be provided by another binary
                }
            }
            br.close();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static boolean hasRoot() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk"};
        for (String p : paths) if (new File(p).exists()) return true;
        return false;
    }

    private static void chmodBinaries(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (f.getName().equals("bin")) chmodBinaries(f);
                continue;
            }
            f.setExecutable(true, false);
            f.setReadable(true, false);
        }
    }

    public static void mkdirs(File f) throws IOException {
        if (!f.exists() && !f.mkdirs()) throw new IOException("cannot create " + f);
    }

    public static void unzipAsset(Context c, String asset, File target) throws IOException {
        InputStream is = c.getAssets().open(asset);
        ZipInputStream zis = new ZipInputStream(is);
        byte[] buf = new byte[16384];
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            File out = new File(target, e.getName());
            if (e.isDirectory()) {
                mkdirs(out);
            } else {
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) mkdirs(parent);
                FileOutputStream fos = new FileOutputStream(out);
                int n;
                while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    public static String copyAsset(Context c, String asset, File out) throws IOException {
        InputStream is = c.getAssets().open(asset);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) mkdirs(parent);
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close();
        is.close();
        return out.getAbsolutePath();
    }

    public static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) mkdirs(parent);
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(text.getBytes("UTF-8"));
        fos.close();
    }

    public static String readAsset(Context c, String asset) throws IOException {
        InputStream is = c.getAssets().open(asset);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toString("UTF-8");
    }
}
