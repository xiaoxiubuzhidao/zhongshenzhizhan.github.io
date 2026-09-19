package com.zszz.legion;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Random;

/**
 * 终神之战：军团 安卓启动器
 * 打开时先输入图形验证码，验证通过后加载本地缓存游戏并自动检测更新：
 * 读取 game_version.json，有新版本则下载并 SHA256 校验后本地加载，
 * 与电脑版启动器逻辑一致：玩家无需重装 APK 即可自动接收游戏更新。
 */
public class MainActivity extends Activity {

    static final String BASE = "https://xiaoxiubuzhidao.github.io/zhongshenzhizhan.github.io/";
    static final String VERSION_URL = BASE + "game_version.json";
    static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // 去除易混淆字符 0 O 1 I

    private WebView web;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private CaptchaView captchaView;
    private EditText input;
    private String code;
    private int attempts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("zszz", MODE_PRIVATE);
        showCaptcha();
    }

    /** 验证码界面：输入正确后进入游戏 */
    void showCaptcha() {
        TextView title = new TextView(this);
        title.setText("终神之战：军团");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("安全验证 · 请输入下方验证码");
        sub.setTextColor(0xFF9AA0B3);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);

        captchaView = new CaptchaView(this);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("请输入验证码");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF5A6072);
        input.setGravity(Gravity.CENTER);
        input.setBackgroundColor(0xFF151A29);

        Button btn = new Button(this);
        btn.setText("进 入 游 戏");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkInput(); }
        });

        Button refresh = new Button(this);
        refresh.setText("换一张");
        refresh.setBackgroundColor(0x22000000);
        refresh.setTextColor(0xFF9AA0B3);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newCode(); input.setText(""); }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF04050A);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sub, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 190);
        capLp.setMargins(56, 26, 56, 0);
        root.addView(captchaView, capLp);

        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 64);
        inLp.setMargins(56, 22, 56, 0);
        root.addView(input, inLp);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 64);
        btnLp.setMargins(56, 24, 56, 0);
        root.addView(btn, btnLp);

        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rfLp.setMargins(0, 16, 0, 0);
        root.addView(refresh, rfLp);

        setContentView(root);
        newCode();
    }

    void newCode() {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
        code = sb.toString();
        captchaView.setCode(code);
    }

    void checkInput() {
        String s = input.getText().toString().trim().toUpperCase();
        if (s.length() == 0) { toast("请输入验证码"); return; }
        if (s.equals(code)) {
            enterGame();
        } else {
            attempts++;
            if (attempts >= 5) {
                toast("尝试次数过多，请重新打开应用");
                finish();
                return;
            }
            toast("验证码错误，剩余 " + (5 - attempts) + " 次");
            input.setText("");
            newCode();
        }
    }

    /** 验证通过：加载游戏 WebView 并后台检测更新 */
    void enterGame() {
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
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 自定义验证码绘制视图 */
    class CaptchaView extends View {
        private String mCode = "";
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();

        CaptchaView(Context context) {
            super(context);
            setBackgroundColor(0xFF10131E);
        }

        void setCode(String c) {
            mCode = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int hh = getHeight();
            if (w <= 0 || hh <= 0) return;

            // 干扰线
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            for (int i = 0; i < 6; i++) {
                paint.setColor(0xFF2A2F45);
                canvas.drawLine(rnd.nextInt(w), rnd.nextInt(hh), rnd.nextInt(w), rnd.nextInt(hh), paint);
            }
            // 干扰噪点
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF3A4160);
            for (int i = 0; i < 70; i++) {
                canvas.drawCircle(rnd.nextInt(w), rnd.nextInt(hh), 2, paint);
            }
            // 验证码字符（旋转 + 金色）
            if (mCode.length() == 0) return;
            float cw = w / 4f;
            paint.setColor(0xFFE8C877);
            paint.setTextSize(hh * 0.62f);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < mCode.length(); i++) {
                canvas.save();
                canvas.rotate((rnd.nextFloat() - 0.5f) * 32, cw * i + cw / 2, hh / 2);
                canvas.drawText(String.valueOf(mCode.charAt(i)), cw * i + cw * 0.26f, hh * 0.74f, paint);
                canvas.restore();
            }
        }
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
        c.setRequestProperty("User-Agent", "ZSZZ-Android/1.1");
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
