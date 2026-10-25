package com.kadian.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * 卡点 · Android 壳。
 * WebView 从 assets/public/ 加载（经 https://appassets.androidplatform.net 本地源，
 * 与真实 HTTPS 站点同源，CORS / IndexedDB / fetch 全部正常），
 * AI 接口直接由 JS 端 fetch，本 Activity 只负责：拍照选图、备份导入导出、后退键。
 */
public class MainActivity extends Activity {

    private static final String TAG = "Kadian";
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String ASSET_ROOT = "public";
    private static final int FILE_CHOOSER_REQ = 1001;
    private static final int IMPORT_FILE_REQ = 1002;
    private static final int EXPORT_CHUNK = 400000;   // base64 分片，防 Binder 1MB 限制
    private static final int IMPORT_CHUNK = 400000;

    private WebView web;
    private ValueCallback<Uri[]> pendingFileCallback;
    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private final StringBuffer exportBuf = new StringBuffer();

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFFF6F6F3);
            getWindow().setNavigationBarColor(0xFFF6F6F3);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        web = new WebView(this);
        setContentView(web);
        configureWebView();
        web.loadUrl("https://" + APP_HOST + "/index.html");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // 供 JS 识别当前运行在 APK 内（跳过 Service Worker 等）
        s.setUserAgentString(s.getUserAgentString() + " KadianAndroid/1.0");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (APP_HOST.equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || path.isEmpty() || path.equals("/")) path = "/index.html";
                    if (path.startsWith("/")) path = path.substring(1);
                    if (path.contains("..")) return null;
                    try {
                        InputStream is = getAssets().open(ASSET_ROOT + "/" + path);
                        return new WebResourceResponse(mimeOf(path), "UTF-8", is);
                    } catch (IOException e) {
                        Log.w(TAG, "asset miss: " + path);
                        return null;
                    }
                }
                return null;   // 其余请求（AI API）走正常网络
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, final ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                    pendingFileCallback = null;
                }
                pendingFileCallback = callback;

                // 相机 intent：输出到自家 provider（全尺寸原图，OCR 才看得清）
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File dir = new File(getExternalCacheDir(), "uploads");
                if (!dir.exists()) dir.mkdirs();
                pendingCameraFile = new File(dir, "cam_" + System.currentTimeMillis() + ".jpg");
                pendingCameraUri = WebAssetProvider.uriForFile(MainActivity.this, pendingCameraFile);
                camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                boolean hasCamera = camera.resolveActivity(getPackageManager()) != null;

                // 相册 intent
                Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
                gallery.addCategory(Intent.CATEGORY_OPENABLE);
                gallery.setType("image/*");
                gallery.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(gallery, "选择图片");
                if (hasCamera) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                }
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    startActivityForResult(chooser, FILE_CHOOSER_REQ);
                } catch (Exception e) {
                    pendingFileCallback = null;
                    pendingCameraFile = null;
                    pendingCameraUri = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, "console: " + m.message());
                return true;
            }
        });

        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void exportChunk(String part) {
                exportBuf.append(part);
            }

            @JavascriptInterface
            public void exportEnd(final String filename, final String mime) {
                final byte[] bytes;
                try {
                    bytes = Base64.decode(exportBuf.toString(), Base64.DEFAULT);
                } catch (Exception e) {
                    runOnUiThread(() -> toast("备份失败：" + e.getMessage()));
                    return;
                }
                exportBuf.setLength(0);
                final String path = writeToDownloads(filename, bytes, mime);
                runOnUiThread(() -> toast("备份已保存：" + path));
            }

            @JavascriptInterface
            public void readImport() {
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    try {
                        startActivityForResult(i, IMPORT_FILE_REQ);
                    } catch (Exception e) {
                        toast("无法打开文件选择器");
                    }
                });
            }

            @JavascriptInterface
            public String version() {
                return "1.1.0";
            }
        }, "KadianAndroid");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /* 备份写入：Q+ 用 MediaStore.Downloads（公共下载目录）；旧系统用应用外部目录，均无需权限 */
    private String writeToDownloads(String name, byte[] bytes, String mime) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime == null ? "application/json" : mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        os.write(bytes);
                    }
                    v.clear();
                    v.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, v, null, null);
                    return "Download/" + name;
                }
            } catch (Exception e) {
                Log.w(TAG, "mediastore write failed", e);
            }
        }
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, name);
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(bytes);
            return f.getAbsolutePath();
        } catch (IOException e) {
            return "失败：" + e.getMessage();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_CHOOSER_REQ) {
            Uri result = null;
            if (res == RESULT_OK) {
                if (data != null && data.getData() != null) {
                    result = data.getData();                       // 相册选择
                } else if (pendingCameraUri != null) {
                    result = pendingCameraUri;                     // 相机拍照（data 通常为 null）
                }
            } else if (pendingCameraFile != null && pendingCameraFile.length() > 0) {
                result = pendingCameraUri;                         // 个别相机取消时也落了盘
            }
            if (pendingFileCallback != null) {
                pendingFileCallback.onReceiveValue(result != null ? new Uri[]{result} : null);
            }
            pendingFileCallback = null;
            pendingCameraUri = null;
            pendingCameraFile = null;
            return;
        }
        if (req == IMPORT_FILE_REQ) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                readImportFile(data.getData());
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    /* 读备份文件 → base64 分片推给 JS（防单次 evaluateJavascript 字符串过大） */
    private void readImportFile(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) { toast("无法读取该文件"); return; }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            final String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            for (int i = 0; i < b64.length(); i += IMPORT_CHUNK) {
                final String part = b64.substring(i, Math.min(b64.length(), i + IMPORT_CHUNK));
                final boolean last = i + IMPORT_CHUNK >= b64.length();
                web.post(() -> web.evaluateJavascript(
                        last
                                ? "(window.__kadianImportChunks.push('" + part + "'),window.__kadianImportDone())"
                                : "window.__kadianImportChunks.push('" + part + "')",
                        null));
            }
        } catch (Exception e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private static String mimeOf(String p) {
        p = p.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".webmanifest")) return "application/manifest+json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".gz")) return "application/gzip";
        return "application/octet-stream";
    }
}
