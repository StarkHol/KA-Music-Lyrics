package com.ka.music.lyrics;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * 模块设置页：状态展示 + 一键重新注入 + 桌面图标开关。
 */
public class MainActivity extends Activity {

    private static final String KA = "com.hoilai.mm.music";
    private static final String HL = "com.juren233.hyperlyricsenhanced";
    private static final int BG = 0xFFF4F1FB;
    private static final int CARD = 0xFFFFFFFF;
    private static final int PRIMARY = 0xFF6750A4;
    private static final int TEXT = 0xFF1D1B20;
    private static final int SUB = 0xFF6B7280;
    private static final int STROKE = 0xFFE3DAF7;

    private TextView stateView;
    private TextView dotView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("KA-Music歌词");
        title.setTextSize(26);
        title.setTextColor(TEXT);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("独立的歌词 Provider 模块 · 只作用于 KA Music");
        sub.setTextSize(12.5f);
        sub.setTextColor(SUB);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        // ===== 状态卡片 =====
        LinearLayout stateCard = card();
        TextView stateTitle = new TextView(this);
        stateTitle.setText("运行状态");
        stateTitle.setTextSize(16);
        stateTitle.setTextColor(TEXT);
        stateTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        stateCard.addView(stateTitle);

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.setPadding(0, dp(10), 0, 0);

        dotView = new TextView(this);
        dotView.setText("●");
        dotView.setTextSize(13);
        dotView.setTextColor(0xFFBDBDBD);
        stateRow.addView(dotView);

        stateView = new TextView(this);
        stateView.setText("正在检查…");
        stateView.setTextSize(14);
        stateView.setTextColor(SUB);
        stateView.setPadding(dp(8), 0, 0, 0);
        stateRow.addView(stateView);
        stateCard.addView(stateRow);
        root.addView(stateCard);

        // ===== 按钮区 =====
        Button restart = primaryButton("重启模块（重新注入）");
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast("正在重启模块…");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int code = execSuCode("am force-stop " + KA + "; sleep 1; am start -n " + KA + "/.MainActivity");
                        try {
                            Thread.sleep(1200);
                        } catch (Throwable ignored) {
                        }
                        if (code == 0) {
                            toast("模块已重新注入");
                        } else if (code < 0) {
                            toast("重启失败：没拿到 root 权限");
                        } else {
                            toast("重启失败，命令退出码 " + code);
                        }
                        refreshState();
                    }
                }, "KAMLyrics-reinject").start();
            }
        });
        root.addView(restart);

        Button openKa = outlineButton("打开 KA MUSIC");
        openKa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 启动别的应用不用 root，普通 Intent 就够了
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage(KA);
                    if (i == null) {
                        toast("没找到 KA MUSIC");
                        return;
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) {
                    toast("打开失败：" + t);
                }
            }
        });
        root.addView(openKa);

        Button openHl = outlineButton("打开 HYPERLYRICS ENHANCED");
        openHl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent();
                    i.setClassName(HL, HL + ".ui.MainLauncherAlias");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) {
                    toast("打开失败：" + t);
                }
            }
        });
        root.addView(openHl);

        // ===== 设置卡片 =====
        LinearLayout setCard = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textsLp);

        TextView setTitle = new TextView(this);
        setTitle.setText("桌面显示图标");
        setTitle.setTextSize(15.5f);
        setTitle.setTextColor(TEXT);
        setTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        texts.addView(setTitle);

        TextView setDesc = new TextView(this);
        setDesc.setText("关闭后桌面不再显示入口，仍可从 LSPosed 管理器打开");
        setDesc.setTextSize(11.5f);
        setDesc.setTextColor(SUB);
        setDesc.setPadding(0, dp(4), 0, 0);
        texts.addView(setDesc);
        row.addView(texts);

        Switch sw = new Switch(this);
        sw.setChecked(isLauncherEnabled());
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setLauncherEnabled(isChecked);
                toast(isChecked ? "桌面图标已显示" : "桌面图标已隐藏");
            }
        });
        row.addView(sw);
        setCard.addView(row);
        root.addView(setCard);

        // ===== 使用说明卡片 =====
        LinearLayout helpCard = card();
        TextView helpTitle = new TextView(this);
        helpTitle.setText("使用说明");
        helpTitle.setTextSize(16);
        helpTitle.setTextColor(TEXT);
        helpTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        helpCard.addView(helpTitle);

        TextView help = new TextView(this);
        help.setText("1. 在 LSPosed 中启用本模块，作用域勾选「KA Music」\n"
                + "2. 点「重启模块」重新注入\n"
                + "3. 播放歌曲后，HyperLyrics Enhanced 会自动发现本模块\n"
                + "4. 歌词翻译取自网易云，与 KA Music 自身的歌词源一致");
        help.setTextSize(13);
        help.setTextColor(SUB);
        help.setLineSpacing(dp(6), 1f);
        help.setPadding(0, dp(10), 0, 0);
        helpCard.addView(help);
        root.addView(helpCard);

        // ===== 关于卡片 =====
        LinearLayout aboutCard = card();
        TextView about = new TextView(this);
        about.setText("KA-Music歌词  v1.0\n作者 柑橘の洛　·　提供包 com.hoilai.mm.music");
        about.setTextSize(12);
        about.setTextColor(SUB);
        about.setLineSpacing(dp(4), 1f);
        aboutCard.addView(about);
        root.addView(aboutCard);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    // ===== 状态 =====

    private void refreshState() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = execSu("logcat -d | grep KAMLyrics | tail -30");
                final String text;
                final int color;
                boolean running = isKaRunning();
                if (out.contains("lyric pushed=true")) {
                    text = "已激活：正在提供歌词";
                    color = 0xFF2E7D32;
                } else if (out.contains("provider registered=true")) {
                    text = running ? "已激活：Provider 已注册，播放歌曲即可显示歌词"
                            : "已激活：Provider 已注册（KA Music 未运行）";
                    color = 0xFF2E7D32;
                } else if (out.contains("injected into")) {
                    text = "已注入但注册未成功，请点「重启模块」";
                    color = 0xFFE6A700;
                } else if (running) {
                    text = "KA Music 正在运行（点「重启模块」可重新注入）";
                    color = 0xFFE6A700;
                } else {
                    text = "KA Music 未运行 —— 点「重启模块」或「打开 KA MUSIC」";
                    color = 0xFF9E9E9E;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stateView.setText(text);
                        dotView.setTextColor(color);
                    }
                });
            }
        }, "KAMLyrics-state").start();
    }

    private boolean isKaRunning() {
        File[] list = new File("/proc").listFiles();
        if (list == null) {
            return false;
        }
        for (File f : list) {
            String n = f.getName();
            if (n.isEmpty() || !Character.isDigit(n.charAt(0))) {
                continue;
            }
            FileInputStream in = null;
            try {
                in = new FileInputStream(new File(f, "cmdline"));
                byte[] buf = new byte[128];
                int k = in.read(buf);
                if (k > 0 && KA.equals(new String(buf, 0, k).trim())) {
                    return true;
                }
            } catch (Throwable ignored) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return false;
    }

    // ===== 桌面图标开关 =====

    private ComponentName alias() {
        return new ComponentName(this, "com.ka.music.lyrics.LauncherAlias");
    }

    private boolean isLauncherEnabled() {
        return getPackageManager().getComponentEnabledSetting(alias())
                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setLauncherEnabled(boolean enabled) {
        getPackageManager().setComponentEnabledSetting(alias(),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    // ===== UI 小工具 =====

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(20));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PRIMARY);
        bg.setCornerRadius(dp(26));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private Button outlineButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14.5f);
        b.setTextColor(PRIMARY);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), STROKE);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private String execSu(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", cmd});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            p.waitFor();
        } catch (Throwable t) {
            sb.append(t.toString());
        }
        return sb.toString().trim();
    }

    /** 只看这条命令成不成，用来判断有没有拿到 root。 */
    private int execSuCode(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", cmd});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while (br.readLine() != null) {
                //输出丢掉，但要读走，不然缓冲满了会卡住
            }
            br.close();
            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while (er.readLine() != null) {
            }
            er.close();
            return p.waitFor();
        } catch (Throwable t) {
            return -1;
        }
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
