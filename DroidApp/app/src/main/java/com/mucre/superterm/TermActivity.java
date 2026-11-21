package com.mucre.superterm;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

public class TermActivity extends Activity implements TerminalView.Listener {

    private TerminalView term;
    private TermSession session;
    private boolean ctrlArmed = false;
    private boolean altArmed = false;
    private Button ctrlBtn, altBtn, shellBtn;
    private String shellChoice = "fish";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences sp = getSharedPreferences(Installer.PREFS, MODE_PRIVATE);
        shellChoice = sp.getString("shell", "fish");
        System.setProperty("superterm.lib", Installer.libDir(this).getAbsolutePath());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F14"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#11161D"));
        bar.setPadding(dp(6), dp(4), dp(6), dp(4));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(bar);
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shellBtn = key("fish", new View.OnClickListener() {
            public void onClick(View v) {
                shellChoice = shellChoice.equals("fish") ? "sh" : "fish";
                getSharedPreferences(Installer.PREFS, MODE_PRIVATE).edit().putString("shell", shellChoice).apply();
                shellBtn.setText(shellChoice);
                startShell();
            }
        });
        bar.addView(shellBtn);

        bar.addView(key("Esc", send("\u001b")));
        bar.addView(key("Tab", send("\t")));
        bar.addView(key("^C", send("\u0003")));
        bar.addView(key("^D", send("\u0004")));

        ctrlBtn = key("Ctrl", new View.OnClickListener() {
            public void onClick(View v) {
                ctrlArmed = !ctrlArmed;
                altArmed = false;
                paintToggle(ctrlBtn, ctrlArmed);
                paintToggle(altBtn, altArmed);
            }
        });
        bar.addView(ctrlBtn);

        altBtn = key("Alt", new View.OnClickListener() {
            public void onClick(View v) {
                altArmed = !altArmed;
                ctrlArmed = false;
                paintToggle(altBtn, altArmed);
                paintToggle(ctrlBtn, ctrlArmed);
            }
        });
        bar.addView(altBtn);

        bar.addView(key("\u2191", send("\u001b[A")));
        bar.addView(key("\u2193", send("\u001b[B")));
        bar.addView(key("\u2190", send("\u001b[D")));
        bar.addView(key("\u2192", send("\u001b[C")));

        bar.addView(key("KEY", new View.OnClickListener() {
            public void onClick(View v) { if (term != null) term.showKeyboard(); }
        }));

        term = new TerminalView(this);
        term.setListener(this);
        root.addView(term, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        term.post(new Runnable() { public void run() { startShell(); } });
    }

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private Button key(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#C9D1D9"));
        b.setTypeface(Typeface.MONOSPACE);
        b.setPadding(dp(10), dp(4), dp(10), dp(4));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#1B232D"));
        g.setCornerRadius(dp(6));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        lp.rightMargin = dp(4);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void paintToggle(Button b, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(on ? "#3B8EEA" : "#1B232D"));
        g.setCornerRadius(dp(6));
        b.setBackground(g);
        b.setTextColor(Color.parseColor(on ? "#0B0F14" : "#C9D1D9"));
    }

    private View.OnClickListener send(final String seq) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                if (ctrlArmed && seq.length() == 1) {
                    char c = seq.charAt(0);
                    if (c >= 'a' && c <= 'z') {
                        session.write(String.valueOf((char) (c - 'a' + 1)));
                        return;
                    }
                }
                session.write(seq);
            }
        };
    }

    private void startShell() {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            session = null;
        }
        String prefix = Installer.prefix(this);
        String home = Installer.homeUserDir(this).getAbsolutePath();
        File fish = new File(prefix, "bin/fish");
        File sh = new File(prefix, "bin/sh");
        File target = shellChoice.equals("fish") && fish.exists() ? fish : sh;

        String[] env = new String[]{
                "PATH=" + prefix + "/bin:/system/bin:/system/xbin",
                "PREFIX=" + prefix,
                "HOME=" + home,
                "TMPDIR=" + prefix + "/tmp",
                "TERMINFO=" + prefix + "/share/terminfo",
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8",
                "ENV=" + prefix + "/etc/bashrc",
                "XDG_CONFIG_HOME=" + prefix + "/etc",
                "SHELL=" + target.getAbsolutePath(),
                "ANDROID_ROOT=/system",
                "ANDROID_DATA=/data",
                "ANDROID_ART_ROOT=/apex/com.android.art"
        };
        String[] argv = shellChoice.equals("fish") && fish.exists()
                ? new String[]{target.getAbsolutePath(), "-l"}
                : new String[]{target.getAbsolutePath(), "-i"};

        try {
            term.feed("\u001b[2J\u001b[H".getBytes("UTF-8"), 7);
            session = TermSession.create(argv, env, home, term.getRows(), term.getCols());
            final TermSession s = session;
            new Thread(new Runnable() {
                public void run() {
                    byte[] buf = new byte[8192];
                    try {
                        java.io.InputStream in = s.getInput();
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            final byte[] copy = new byte[n];
                            System.arraycopy(buf, 0, copy, 0, n);
                            term.post(new Runnable() {
                                public void run() { term.feed(copy, copy.length); }
                            });
                        }
                    } catch (Exception ignored) {}
                }
            }).start();
        } catch (final Exception e) {
            term.feed(("\r\n\u001b[31mpty error: " + e + "\u001b[0m\r\n").getBytes(java.nio.charset.Charset.forName("UTF-8")), 0);
        }
    }

    @Override
    public void onTextInput(String text) {
        if (session == null) return;
        if (ctrlArmed && text.length() == 1) {
            char c = text.charAt(0);
            if (c >= 'a' && c <= 'z') text = String.valueOf((char) (c - 'a' + 1));
            else if (c >= 'A' && c <= 'Z') text = String.valueOf((char) (c - 'A' + 1));
        }
        if (altArmed) text = "\u001b" + text;
        ctrlArmed = false;
        altArmed = false;
        paintToggle(ctrlBtn, false);
        paintToggle(altBtn, false);
        session.write(text);
    }

    @Override
    public void onKeyCode(int keyCode, boolean ctrl, boolean alt) {
        if (session == null) return;
        KeyEvent ev = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            char c = (char) ('a' + (keyCode - KeyEvent.KEYCODE_A));
            if (ctrl) session.write(String.valueOf((char) (c - 'a' + 1)));
            else session.write(String.valueOf(c));
        }
    }

    @Override
    public void onResize(int rows, int cols) {
        if (session != null) session.resize(rows, cols);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) { session.close(); session = null; }
    }
}
