package com.zszz.legion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * 终神之战：军团 安卓启动器
 * 打开时检测版本清单（game_version.json），有新版本则下载并 SHA256 校验后本地加载，
 * 与电脑版启动器逻辑一致：玩家无需重装 APK 即可自动接收游戏更新。
 */
public class MainActivity extends Activity {

    static final String BASE = "https://xiaoxiubuzhidao.github.io/zhongshenzhizhan.github.io/";
    static final String VERSION_URL = BASE + "game_version.json";

    private WebView web;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sp = getSharedPreferences("zszz", MODE_PRIVATE);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient());

        // 先加载本地缓存（若有），保证秒开
        File local = new File(getFilesDir(), "game.html");
        if (local.exists()) {
            web.loadUrl("file://" + local.getAbsolutePath());
        } else {
            web.loadData("<html><body style='background:#04050a;color:#fff;text-align:center;padding-top:40%'>正在连接服务器…</body></html>",
                    "text/html; charset=utf-8", null);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                checkUpdate();
            }
        }).start();
    }

    /** 版本检测 + 下载更新（与电脑启动器一致） */
    void checkUpdate() {
        try {
            String json = httpGet(VERSION_URL);
            if (json == null) {
                toast("网络连接失败，请检查网络后重新打开");
                return;
            }
            String ver = regex(json, "\"version\"\\s*:\\s*\"([^\"]+)\"");
            String sha = regex(json, "\"sha256\"\\s*:\\s*\"([^\"]+)\"");
            String file = regex(json, "\"file\"\\s*:\\s*\"([^\"]+)\"");
            if (ver == null || sha == null) {
                toast("服务器数据异常");
                return;
            }
            String localVer = sp.getString("ver", "");
            if (ver.equals(localVer) && new File(getFilesDir(), "game.html").exists()) {
                // 已是最新
                return;
            }
            toast("发现新版本 v" + ver + "，正在下载…");
            byte[] data = httpGetBytes(BASE + (file != null ? file : "game.html"));
            if (data == null || data.length == 0) {
                toast("下载失败，请检查网络后重新打开");
                return;
            }
            String got = sha256(data);
            if (!sha.equalsIgnoreCase(got)) {
                toast("校验失败，文件可能不完整，请重新打开");
                return;
            }
            FileOutputStream fos = openFileOutput("game.html", MODE_PRIVATE);
            fos.write(data);
            fos.close();
            sp.edit().putString("ver", ver).apply();
            toast("已更新至 v" + ver);
            final File f = new File(getFilesDir(), "game.html");
            h.post(new Runnable() {
                @Override
                public void run() {
                    web.loadUrl("file://" + f.getAbsolutePath());
                }
            });
        } catch (Exception e) {
            toast("更新失败：" + e.getMessage());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    void toast(final String msg) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    static String regex(String s, String pat) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pat).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    static String httpGet(String urlStr) throws Exception {
        byte[] d = httpGetBytes(urlStr);
        return d == null ? null : new String(d, "UTF-8");
    }

    static byte[] httpGetBytes(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "ZSZZ-Android/1.0");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            return null;
        }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return out.toByteArray();
    }

    static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(data);
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
