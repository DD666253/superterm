package com.mucre.superterm;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0B0F14;
    private static final int FG = 0xFFE6EDF3;
    private static final int ACC = 0xFF3B8EEA;

    private LinearLayout root;
    private SharedPreferences sp;
    private String lang = "en";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(Installer.PREFS, MODE_PRIVATE);
        lang = sp.getString("lang", "en");
        System.setProperty("superterm.lib", Installer.libDir(this).getAbsolutePath());
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        showWelcome();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView label(String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        tv.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return tv;
    }

    private Button button(String text, int bgColor, int fgColor) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setTextColor(fgColor);
        bt.setTextSize(15);
        bt.setAllCaps(false);
        bt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(10));
        bt.setBackground(g);
        bt.setPadding(dp(18), dp(12), dp(18), dp(12));
        return bt;
    }

    private void showWelcome() {
        root.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(70), dp(24), dp(24));
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = label("超级TERM", 26, FG, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = label(lang.equals("zh") ? "真实终端 · fish / sh · vim · pkg"
                : "real shell · fish / sh · vim · pkg", 13, 0xFF8B949E, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.addView(sub, lp);

        LinearLayout langs = new LinearLayout(this);
        langs.setOrientation(LinearLayout.HORIZONTAL);
        langs.setGravity(Gravity.CENTER);
        lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(36);
        box.addView(langs, lp);

        String[] codes = {"en", "zh", "us"};
        String[] names = {"English", "中文", "US"};
        for (int i = 0; i < codes.length; i++) {
            final String code = codes[i];
            boolean sel = code.equals(lang);
            Button bt = button(names[i], sel ? ACC : 0xFF161B22, sel ? 0xFF0B0F14 : 0xFFC9D1D9);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.rightMargin = dp(8);
            langs.addView(bt, bl);
            bt.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    lang = code;
                    sp.edit().putString("lang", code).apply();
                    showWelcome();
                }
            });
        }

        Button go = button("Go", ACC, 0xFF0B0F14);
        go.setTextSize(17);
        lp = new LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(40);
        box.addView(go, lp);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showInstall(); }
        });

        TextView foot = label(lang.equals("zh") ? "默认语言：English（英语）" : "default language: English", 11, 0xFF6E7681, false);
        foot.setGravity(Gravity.CENTER);
        lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        box.addView(foot, lp);
    }

    private void showInstall() {
        root.removeAllViews();
        boolean installed = Installer.isInstalled(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(60), dp(24), dp(24));
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = label(installed ? (lang.equals("zh") ? "已安装 · sh + apkManager" : "installed · sh + apkManager")
                : "install sh+apkManager", 20, FG, true);
        box.addView(title);

        TextView detail = label(installed
                ? (lang.equals("zh") ? "虚拟机已就绪，路径：" : "userland ready at ") + Installer.drivePath(this)
                : (lang.equals("zh")
                ? "将把 sh、fish、vim、pkg、eggtool 写入 " + Installer.drivePath(this) + "/usr/bin"
                : "sh, fish, vim, pkg, eggtool will be written into " + Installer.drivePath(this) + "/usr/bin"),
                13, 0xFF8B949E, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        box.addView(detail, lp);

        final TextView log = label(installed ? "" : (lang.equals("zh") ? "等待开始…" : "ready…"), 12, 0xFF8B949E, false);
        lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        box.addView(log, lp);

        final Button action = button(installed ? (lang.equals("zh") ? "进入终端" : "Open terminal")
                : (lang.equals("zh") ? "安装" : "Install"), ACC, 0xFF0B0F14);
        action.setTextSize(17);
        lp = new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(28);
        box.addView(action, lp);

        action.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Installer.isInstalled(MainActivity.this)) {
                    startActivity(new Intent(MainActivity.this, TermActivity.class));
                    return;
                }
                action.setEnabled(false);
                action.setText(lang.equals("zh") ? "安装中…" : "installing…");
                log.setText(lang.equals("zh") ? "写入 usr 目录…" : "writing usr…");
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Installer.install(MainActivity.this);
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    Toast.makeText(MainActivity.this,
                                            lang.equals("zh") ? "安装完成" : "install complete", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(MainActivity.this, TermActivity.class));
                                    finish();
                                }
                            });
                        } catch (final Exception e) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    action.setEnabled(true);
                                    action.setText(lang.equals("zh") ? "重试安装" : "retry install");
                                    log.setText("ERROR: " + e);
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        Button back = button(lang.equals("zh") ? "返回" : "Back", 0xFF161B22, 0xFFC9D1D9);
        lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        box.addView(back, lp);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showWelcome(); }
        });
    }
}
