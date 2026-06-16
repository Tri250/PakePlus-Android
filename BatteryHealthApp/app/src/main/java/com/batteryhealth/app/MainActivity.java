package com.batteryhealth.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 电池健康度分析工具主Activity
 *
 * 核心修复（v1.2.5）：
 * 1. 修复openFileChooser清理callback的bug - 导致选择文件后无反应
 * 2. 优化权限管理 - 使用SAF框架无需存储权限
 * 3. 修复Android 13+媒体权限误用问题
 * 4. 添加详细诊断日志
 *
 * @version 1.2.5
 * @author 带娃的小陈工
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BatteryHealthApp";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1002;
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024; // 500MB限制（原 200MB，避免大 bugreport 被拒）

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String[] permissions;
    private boolean isWebViewDestroyed = false;
    private boolean isMemoryLow = false;
    private boolean isPickerFromJs = false; // 标记是否由JS触发

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置全屏模式，优化用户体验
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);

        // 初始化权限数组
        initPermissions();

        // 初始化WebView
        initWebView();

        // 延迟请求权限 - 在WebView加载完成后请求，避免阻塞UI
        if (webView != null) {
            webView.postDelayed(this::checkAndRequestPermissions, 500);
        }
    }

    /**
     * 内存警告处理
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory called with level: " + level);

        isMemoryLow = true;

        if (level >= TRIM_MEMORY_MODERATE) {
            if (webView != null && !isWebViewDestroyed) {
                webView.clearCache(true);
                webView.clearHistory();
                Log.d(TAG, "WebView cache cleared due to memory pressure");
            }

            if (webView != null) {
                webView.evaluateJavascript(
                    "if(window.BatteryHealthApp) window.BatteryHealthApp.onMemoryWarning();",
                    null
                );
            }
        }

        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            Toast.makeText(this, "内存不足，建议关闭其他应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "onLowMemory called");
        isMemoryLow = true;

        if (webView != null && !isWebViewDestroyed) {
            webView.clearCache(true);
        }
    }

    /**
     * 初始化权限数组
     * 关键修复：使用SAF框架，Android 11+无需任何存储权限
     */
    private void initPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - 使用SAF无需传统存储权限
            // READ_MEDIA_VIDEO仅用于Android 14+的部分媒体访问兼容
            permissions = new String[]{};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 - 使用SAF无需权限
            permissions = new String[]{};
        } else {
            // Android 10及以下 - 旧权限模型
            permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        Log.d(TAG, "Permissions initialized, count: " + permissions.length);
    }

    /**
     * 检查并请求权限
     * 仅在旧版本Android上需要存储权限
     */
    private void checkAndRequestPermissions() {
        if (permissions == null || permissions.length == 0) {
            Log.d(TAG, "No permissions needed (using SAF)");
            return;
        }

        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            Log.d(TAG, "Requesting permissions: " + permissions.length);
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "All permissions already granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                }
            }
            if (allGranted) {
                Log.d(TAG, "All permissions granted");
            } else {
                Log.w(TAG, "Some permissions denied");
                // 关键：即使权限被拒，SAF仍可使用
                Toast.makeText(this, "权限受限，将使用系统文件选择器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 初始化WebView
     */
    private void initWebView() {
        webView = findViewById(R.id.webView);

        if (webView == null) {
            Log.e(TAG, "WebView is null");
            Toast.makeText(this, "初始化失败，请重启应用", Toast.LENGTH_SHORT).show();
            return;
        }

        WebSettings webSettings = webView.getSettings();

        // ========== 安全配置 ==========
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        // 关键：允许file://页面访问其他file://资源（用于读取临时文件）
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webSettings.setGeolocationEnabled(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);

        // ========== 性能优化 ==========
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setTextZoom(100);

        // 启用数据库存储
        webSettings.setDatabaseEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        // ========== 添加JavaScript接口 ==========
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidFilePicker");
        Log.d(TAG, "JavaScript interface added");

        // ========== WebViewClient配置 ==========

        // 关键：创建WebViewAssetLoader用于访问临时文件
        // 使用标准地址https://appassets.androidplatform.net/绕过file://限制
        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/bha/", new WebViewAssetLoader.PathHandler() {
                @Override
                public WebResourceResponse handle(String path) {
                    try {
                        Log.d(TAG, "AssetLoader request path: " + path);
                        // path 格式: bha_1234567890_xxx.zip (相对于 /bha/ 前缀)
                        File cacheFile = new File(getCacheDir(), path);
                        if (cacheFile.exists() && cacheFile.getName().startsWith("bha_")) {
                            long size = cacheFile.length();
                            Log.d(TAG, "Serving file from cache: " + cacheFile.getAbsolutePath() + " size=" + size);
                            InputStream is = new FileInputStream(cacheFile);
                            // MIME类型：application/zip
                            return new WebResourceResponse("application/zip", "UTF-8", is);
                        }
                        Log.w(TAG, "Cache file not found: " + path);
                        return null;
                    } catch (Exception e) {
                        Log.e(TAG, "AssetLoader error for path: " + path, e);
                        return null;
                    }
                }
            })
            .build();
        Log.d(TAG, "WebViewAssetLoader initialized");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // 关键：拦截https://appassets.androidplatform.net/请求
                WebResourceResponse response = assetLoader.shouldInterceptRequest(request.getUrl());
                if (response != null) {
                    Log.d(TAG, "Intercepted request: " + request.getUrl());
                    return response;
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // WebViewAssetLoader的内部地址，让WebView处理
                if (url.startsWith("https://appassets.androidplatform.net/")) {
                    return false;
                }

                if (url.startsWith("file:///android_asset/")) {
                    return false;
                }

                if (url.startsWith("content://")) {
                    return false;
                }

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open URL: " + url, e);
                    }
                    return true;
                }

                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    Log.e(TAG, "WebView error: " + error.getDescription());
                    Toast.makeText(MainActivity.this, "页面加载失败，请重试", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                Log.e(TAG, "SSL Error: " + error.toString());
                handler.cancel();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);
                // 注入JavaScript代码来监听文件选择器点击
                injectFilePickerScript();
            }
        });

        // ========== WebChromeClient配置 ==========
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                Log.d(TAG, "=== onShowFileChooser triggered ===");

                // 关键修复：不要在这里清理callback，让openFileChooser自己管理
                if (MainActivity.this.filePathCallback != null) {
                    Log.w(TAG, "Previous callback exists, cleaning up");
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                    MainActivity.this.filePathCallback = null;
                }

                MainActivity.this.filePathCallback = filePathCallback;
                isPickerFromJs = false; // 来自WebView input

                openFileChooser();
                return true;
            }
        });

        // 加载本地HTML文件
        try {
            webView.loadUrl("file:///android_asset/index.html");
            Log.d(TAG, "HTML loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load HTML", e);
            Toast.makeText(this, "加载失败，请重启应用", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 注入JavaScript代码监听文件选择器点击
     */
    private void injectFilePickerScript() {
        String jsCode =
            "(function() {" +
            "   try {" +
            "       var dropArea = document.getElementById('drop-area');" +
            "       var fileInput = document.getElementById('zip-file');" +
            "       if (dropArea) {" +
            "           if (window.__bhaClickHandler) {" +
            "               dropArea.removeEventListener('click', window.__bhaClickHandler, true);" +
            "           }" +
            "           window.__bhaClickHandler = function(e) {" +
            "               e.preventDefault();" +
            "               e.stopPropagation();" +
            "               console.log('Drop area clicked, calling Android picker');" +
            "               if (window.AndroidFilePicker && window.AndroidFilePicker.openFilePicker) {" +
            "                   window.AndroidFilePicker.openFilePicker();" +
            "               } else if (fileInput) {" +
            "                   console.warn('AndroidFilePicker not available, using file input');" +
            "                   fileInput.click();" +
            "               } else {" +
            "                   console.error('No file picker available');" +
            "               }" +
            "           };" +
            "           dropArea.addEventListener('click', window.__bhaClickHandler, true);" +
            "           console.log('File picker script injected successfully');" +
            "       } else {" +
            "           console.warn('Drop area element not found, will retry');" +
            "           setTimeout(function() {" +
            "               var retryArea = document.getElementById('drop-area');" +
            "               if (retryArea) {" +
            "                   console.log('Drop area found on retry, injecting...');" +
            "                   retryArea.addEventListener('click', window.__bhaClickHandler, true);" +
            "               }" +
            "           }, 1000);" +
            "       }" +
            "   } catch (e) {" +
            "       console.error('injectFilePickerScript error:', e);" +
            "   }" +
            "})();";

        if (webView != null && !isWebViewDestroyed) {
            webView.evaluateJavascript(jsCode, null);
        }
        Log.d(TAG, "File picker script injected");
    }

    /**
     * 打开文件选择器
     * 关键修复：不再清理callback，避免清空刚设置的callback导致无反应
     */
    private void openFileChooser() {
        Log.d(TAG, "=== openFileChooser called ===");

        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            String[] mimeTypes = {
                "application/zip",
                "application/x-zip-compressed"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooserIntent = Intent.createChooser(intent, "选择诊断文件（ZIP格式）");
            // 关键：必须设置FLAG_GRANT_READ_URI_PERMISSION
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE);
            Log.d(TAG, "File chooser intent launched successfully");
        } catch (Exception ex) {
            Log.e(TAG, "Failed to open file chooser", ex);
            // 回退方案
            try {
                Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fallbackIntent.setType("*/*");
                fallbackIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"*/*"});
                fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(
                    Intent.createChooser(fallbackIntent, "选择诊断文件（ZIP格式）"),
                    FILE_CHOOSER_REQUEST_CODE
                );
                Log.d(TAG, "Fallback file chooser launched");
            } catch (Exception ex2) {
                Log.e(TAG, "Failed to open fallback file chooser", ex2);
                // 关键：清理callback防止WebView挂起
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }
                Toast.makeText(this, "无法打开文件选择器，请检查系统", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 获取文件名
     */
    private String getFileName(Uri uri) {
        String fileName = null;
        ContentResolver resolver = getContentResolver();

        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        }

        if (fileName == null) {
            fileName = uri.getLastPathSegment();
            if (fileName != null && fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            }
        }

        return fileName != null ? fileName : "unknown";
    }

    /**
     * 获取文件大小
     */
    private long getFileSizeFromUri(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    long size = cursor.getLong(sizeIndex);
                    cursor.close();
                    return size;
                }
                cursor.close();
            }
            return -1;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get file size", e);
            return -1;
        }
    }

    /**
     * JavaScript接口类
     */
    public class WebAppInterface {

        @JavascriptInterface
        public void openFilePicker() {
            Log.d(TAG, "=== JavaScript called openFilePicker ===");
            // 输入验证：确保在主线程执行
            if (!isMainThread()) {
                Log.w(TAG, "openFilePicker called from non-main thread, dispatching to main thread");
            }
            runOnUiThread(() -> {
                // 检查 WebView 是否已被销毁
                if (isWebViewDestroyed || webView == null) {
                    Log.e(TAG, "WebView is destroyed, cannot open file picker");
                    invokeJsOnMain("onFilePickerError", "WebView已销毁");
                    return;
                }
                // 关键修复：JS触发时也要保留callback
                // 但因为JS触发没有onShowFileChooser的callback，
                // 这里创建一个空callback以便文件选择完成后通知JS
                if (filePathCallback == null) {
                    Log.w(TAG, "No callback from WebView, using empty callback");
                    filePathCallback = new ValueCallback<Uri[]>() {
                        @Override
                        public void onReceiveValue(Uri[] value) {
                            // 空callback，避免WebView挂起
                            Log.d(TAG, "Empty callback received: " + (value != null ? value.length : "null"));
                        }
                    };
                }
                isPickerFromJs = true;
                openFileChooser();
            });
        }

        private boolean isMainThread() {
            return Looper.myLooper() == Looper.getMainLooper();
        }

        /**
         * 关键方法：原生 ZIP 流式解析电池健康度（v2.1.7 重构）
         *
         * 之前的 readFileAsBase64 方案将 200MB 文件读入 ByteArrayOutputStream
         * 再生成 1.33 倍的 Base64 字符串，传入 evaluateJavascript 时 WebView
         * 直接 OOM 闪退。本方法直接对 URI 打开 ZipInputStream 单遍扫描，
         * 内存占用 O(1)，结果以小型 JSON 字符串返回给 JS。
         *
         * @param uriString 文件 URI（content:// 形式，SAF 选择）
         * @param callbackJs 成功回调函数名（接收 JSON 字符串）
         * @param errorCallbackJs 错误回调函数名（接收错误消息）
         */
        @JavascriptInterface
        public void analyzeZipNative(final String uriString, final String callbackJs, final String errorCallbackJs) {
            Log.d(TAG, "=== analyzeZipNative for: " + uriString + " ===");
            
            // 输入验证
            if (uriString == null || uriString.trim().isEmpty()) {
                Log.e(TAG, "Invalid URI string: null or empty");
                invokeJsOnMain(errorCallbackJs, "无效的文件URI");
                return;
            }
            if (callbackJs == null || callbackJs.trim().isEmpty()) {
                Log.e(TAG, "Invalid callback function name");
                invokeJsOnMain(errorCallbackJs, "无效的回调函数名");
                return;
            }
            if (errorCallbackJs == null || errorCallbackJs.trim().isEmpty()) {
                Log.e(TAG, "Invalid error callback function name");
                return;
            }
            
            // 验证 URI 格式
            try {
                Uri uri = Uri.parse(uriString);
                if (uri == null || uri.getScheme() == null) {
                    invokeJsOnMain(errorCallbackJs, "URI格式无效");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse URI: " + uriString, e);
                invokeJsOnMain(errorCallbackJs, "URI解析失败");
                return;
            }
            
            // 检查 WebView 状态
            if (isWebViewDestroyed || webView == null) {
                Log.e(TAG, "WebView is destroyed, cannot analyze");
                invokeJsOnMain(errorCallbackJs, "WebView已销毁");
                return;
            }
            
            new Thread(() -> {
                InputStream inputStream = null;
                try {
                    Uri uri = Uri.parse(uriString);
                    ContentResolver resolver = getContentResolver();
                    inputStream = resolver.openInputStream(uri);
                    if (inputStream == null) {
                        invokeJsOnMain(errorCallbackJs, "无法打开文件 URI");
                        return;
                    }

                    // 进度回调：单条信息
                    final BatteryParser.ProgressCallback progressCb = (processed, total, currentName, bestSoFar) -> {
                        runOnUiThread(() -> {
                            String safeName = currentName == null ? "" :
                                    currentName.replace("\\", "\\\\").replace("'", "\\'");
                            int bestCurrent = bestSoFar != null ? bestSoFar.currentCapacity : 0;
                            int bestCycle = bestSoFar != null ? bestSoFar.cycleCount : 0;
                            String js = "if(window.BatteryHealthApp && window.BatteryHealthApp.onNativeProgress){" +
                                    "  window.BatteryHealthApp.onNativeProgress(" + processed + "," +
                                    total + ",'" + safeName + "'," + bestCurrent + "," + bestCycle + ");" +
                                    "}";
                            try { webView.evaluateJavascript(js, null); } catch (Exception ignore) {}
                        });
                    };

                    // 核心解析
                    final BatteryParser.BatteryInfo info = BatteryParser.processZipStream(inputStream, progressCb);

                    // 关闭流
                    try { inputStream.close(); } catch (Exception ignore) {}
                    inputStream = null;

                    if (info == null || (info.currentCapacity == 0 && info.cycleCount == 0 && info.batteryTemp == 0)) {
                        // 即使没有找到电池数据，也返回调试信息（包含 entry 列表）
                        final String debugJson = info != null ? infoToJson(info) : "{\"debugInfo\":\"解析器返回 null\"}";
                        invokeJsOnMain(callbackJs, debugJson);
                        return;
                    }

                    // 构造 JSON
                    final String json = infoToJson(info);

                    runOnUiThread(() -> {
                        String jsCode = "if(window.BatteryHealthApp && window.BatteryHealthApp." + callbackJs + "){" +
                                "  window.BatteryHealthApp." + callbackJs + "(" + json + ");" +
                                "}";
                        Log.d(TAG, "Returning result: brand=" + info.brand + " cap=" + info.currentCapacity + " cycles=" + info.cycleCount);
                        try {
                            webView.evaluateJavascript(jsCode, null);
                        } catch (Exception e) {
                            Log.e(TAG, "evaluateJavascript failed", e);
                        }
                    });

                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "OOM in analyzeZipNative", oom);
                    invokeJsOnMain(errorCallbackJs, "内存不足，请尝试较小的文件");
                } catch (Exception e) {
                    Log.e(TAG, "analyzeZipNative failed", e);
                    invokeJsOnMain(errorCallbackJs, "解析失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                } finally {
                    if (inputStream != null) {
                        try { inputStream.close(); } catch (Exception ignore) {}
                    }
                }
            }, "BatteryParser-Native").start();
        }

        /**
         * BatteryInfo → JSON
         * 使用 JSON 字符串拼接避免引入 org.json 依赖在某些设备上的兼容问题
         */
        private String infoToJson(BatteryParser.BatteryInfo info) {
            StringBuilder sb = new StringBuilder(512);
            sb.append("{");
            sb.append("\"currentCapacity\":").append(info.currentCapacity).append(",");
            sb.append("\"designCapacity\":").append(info.designCapacity).append(",");
            sb.append("\"chargeCounter\":").append(info.chargeCounter).append(",");
            sb.append("\"cycleCount\":").append(info.cycleCount).append(",");
            sb.append("\"batteryTemp\":").append(info.batteryTemp).append(",");
            sb.append("\"voltage\":").append(info.voltage).append(",");
            sb.append("\"confidence\":").append(String.format(java.util.Locale.US, "%.3f", info.confidence)).append(",");
            sb.append("\"brand\":\"").append(jsonEscape(info.brand == null ? "generic" : info.brand)).append("\",");
            sb.append("\"technology\":\"").append(jsonEscape(info.technology == null ? "" : info.technology)).append("\",");
            sb.append("\"rawContent\":\"").append(jsonEscape(info.rawContent == null ? "" : info.rawContent)).append("\",");
            sb.append("\"debugInfo\":\"").append(jsonEscape(info.debugInfo == null ? "" : info.debugInfo)).append("\",");
            sb.append("\"dataSource\":\"").append(jsonEscape(info.dataSource == null ? "" : info.dataSource)).append("\",");
            sb.append("\"kvMapDump\":\"").append(jsonEscape(info.kvMapDump == null ? "" : info.kvMapDump)).append("\",");
            sb.append("\"capacitySource\":\"").append(jsonEscape(info.capacitySource == null ? "" : info.capacitySource)).append("\",");
            sb.append("\"cycleSource\":\"").append(jsonEscape(info.cycleSource == null ? "" : info.cycleSource)).append("\",");
            sb.append("\"tempSource\":\"").append(jsonEscape(info.tempSource == null ? "" : info.tempSource)).append("\",");
            // v2.1.17 新增：设备信息
            sb.append("\"imei1\":\"").append(jsonEscape(info.imei1 == null ? "" : info.imei1)).append("\",");
            sb.append("\"imei2\":\"").append(jsonEscape(info.imei2 == null ? "" : info.imei2)).append("\",");
            sb.append("\"serialNumber\":\"").append(jsonEscape(info.serialNumber == null ? "" : info.serialNumber)).append("\",");
            sb.append("\"deviceModel\":\"").append(jsonEscape(info.deviceModel == null ? "" : info.deviceModel)).append("\",");
            sb.append("\"deviceSource\":\"").append(jsonEscape(info.deviceSource == null ? "" : info.deviceSource)).append("\"");
            sb.append("}");
            return sb.toString();
        }

        private String jsonEscape(String s) {
            if (s == null) return "";
            StringBuilder out = new StringBuilder(s.length() + 16);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '\\': out.append("\\\\"); break;
                    case '"': out.append("\\\""); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    case '\b': out.append("\\b"); break;
                    case '\f': out.append("\\f"); break;
                    default:
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                }
            }
            return out.toString();
        }

        private void invokeJsOnMain(final String fn, final String arg) {
            runOnUiThread(() -> {
                String safe = arg.replace("\\", "\\\\").replace("'", "\\'");
                String js = "if(window.BatteryHealthApp && window.BatteryHealthApp." + fn + "){" +
                        "  window.BatteryHealthApp." + fn + "('" + safe + "');" +
                        "}";
                try { webView.evaluateJavascript(js, null); } catch (Exception ignore) {}
            });
        }

        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "JS Log: " + message);
        }

        /**
         * v2.1.17+ 新增：打开外部链接（查询激活日期）
         * 使用Chrome Custom Tab获得更好体验，降级到系统浏览器
         *
         * @param url 目标URL
         */
        @JavascriptInterface
        public void openExternalUrl(String url) {
            Log.d(TAG, "=== openExternalUrl: " + url + " ===");

            // 输入验证
            if (url == null || url.trim().isEmpty()) {
                Log.e(TAG, "Invalid URL: null or empty");
                showToastOnMain("链接无效");
                return;
            }

            // URL安全校验
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Log.e(TAG, "Invalid URL scheme: " + url);
                showToastOnMain("不安全的链接");
                return;
            }

            // 检查WebView状态
            if (isWebViewDestroyed) {
                Log.e(TAG, "WebView is destroyed, cannot open URL");
                return;
            }

            runOnUiThread(() -> {
                try {
                    Uri uri = Uri.parse(url);

                    // 优先尝试Chrome Custom Tab
                    try {
                        androidx.browser.customtabs.CustomTabsIntent.Builder builder =
                            new androidx.browser.customtabs.CustomTabsIntent.Builder();
                        builder.setToolbarColor(getResources().getColor(android.R.color.white));
                        builder.setShowTitle(true);
                        // 使用系统默认动画资源
                        builder.setStartAnimations(MainActivity.this, android.R.anim.fade_in, android.R.anim.fade_out);
                        builder.setExitAnimations(MainActivity.this, android.R.anim.fade_in, android.R.anim.fade_out);

                        androidx.browser.customtabs.CustomTabsIntent customTabsIntent = builder.build();
                        customTabsIntent.launchUrl(MainActivity.this, uri);
                        Log.d(TAG, "Opened URL with Chrome Custom Tab");
                    } catch (Exception e) {
                        // 降级到普通浏览器
                        Log.w(TAG, "Chrome Custom Tab failed, falling back to browser", e);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open URL: " + url, e);
                    showToastOnMain("打开链接失败");
                }
            });
        }

        /**
         * v2.1.17+ 新增：复制文本到剪贴板
         *
         * @param text 要复制的文本
         * @return 是否复制成功
         */
        @JavascriptInterface
        public boolean copyToClipboard(String text) {
            Log.d(TAG, "=== copyToClipboard ===");

            // 输入验证
            if (text == null || text.trim().isEmpty()) {
                Log.w(TAG, "Empty text, nothing to copy");
                return false;
            }

            // 长度限制（防止异常大数据）
            if (text.length() > 10000) {
                Log.w(TAG, "Text too long, truncating to 10000 chars");
                text = text.substring(0, 10000);
            }

            final String finalText = text;

            runOnUiThread(() -> {
                try {
                    android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("设备信息", finalText);
                    clipboard.setPrimaryClip(clip);
                    Log.d(TAG, "Text copied to clipboard: " + finalText.substring(0, Math.min(20, finalText.length())) + "...");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy to clipboard", e);
                }
            });

            return true;
        }

        /**
         * 在主线程显示Toast
         */
        private void showToastOnMain(String message) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public String readFileContent(String uriString) {
            Log.d(TAG, "JavaScript called readFileContent for: " + uriString);
            
            // 输入验证
            if (uriString == null || uriString.trim().isEmpty()) {
                Log.e(TAG, "Invalid URI string: null or empty");
                return null;
            }
            
            // 检查 WebView 状态
            if (isWebViewDestroyed) {
                Log.e(TAG, "WebView is destroyed, cannot read file");
                return null;
            }
            
            try {
                Uri uri = Uri.parse(uriString);
                if (uri == null || uri.getScheme() == null) {
                    Log.e(TAG, "Invalid URI format");
                    return null;
                }
                
                ContentResolver resolver = getContentResolver();
                InputStream inputStream = resolver.openInputStream(uri);

                if (inputStream == null) {
                    Log.e(TAG, "Failed to open input stream");
                    return null;
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                inputStream.close();
                outputStream.close();

                byte[] fileBytes = outputStream.toByteArray();
                
                // 文件大小限制检查（避免内存溢出）
                if (fileBytes.length > MAX_FILE_SIZE) {
                    Log.e(TAG, "File too large: " + fileBytes.length + " bytes");
                    return null;
                }
                
                String base64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.DEFAULT);

                Log.d(TAG, "File content read successfully, size: " + fileBytes.length);
                return base64;
            } catch (Exception e) {
                Log.e(TAG, "Failed to read file content", e);
                return null;
            }
        }

        @JavascriptInterface
        public long getFileSize(String uriString) {
            Log.d(TAG, "JavaScript called getFileSize for: " + uriString);
            
            // 输入验证
            if (uriString == null || uriString.trim().isEmpty()) {
                Log.e(TAG, "Invalid URI string: null or empty");
                return -1;
            }
            
            try {
                Uri uri = Uri.parse(uriString);
                if (uri == null || uri.getScheme() == null) {
                    Log.e(TAG, "Invalid URI format");
                    return -1;
                }
                
                ContentResolver resolver = getContentResolver();
                android.database.Cursor cursor = resolver.query(uri, null, null, null, null);
                if (cursor != null) {
                    int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (sizeIndex >= 0 && cursor.moveToFirst()) {
                        long size = cursor.getLong(sizeIndex);
                        cursor.close();
                        Log.d(TAG, "File size from cursor: " + size);
                        return size;
                    }
                    cursor.close();
                }
                return -1;
            } catch (Exception e) {
                Log.e(TAG, "Failed to get file size", e);
                return -1;
            }
        }

        /**
         * 带进度的文件复制到临时目录
         * 关键修复：使用临时文件方案，避免200MB大文件OOM
         * 将content:// URI流式复制到应用cacheDir，返回file:// URL
         *
         * @param uriString 文件URI
         * @param callbackJs JavaScript回调函数名
         * @param startPercent 进度条起始百分比
         * @param endPercent 进度条结束百分比
         */
        @JavascriptInterface
        public void readFileContentWithProgress(String uriString, final String callbackJs,
                                                 final int startPercent, final int endPercent) {
            Log.d(TAG, "=== readFileContentWithProgress (copy to cache) for: " + uriString + " ===");
            
            // 输入验证
            if (uriString == null || uriString.trim().isEmpty()) {
                Log.e(TAG, "Invalid URI string: null or empty");
                invokeErrorJs("onFileReadError", "无效的文件URI");
                return;
            }
            if (callbackJs == null || callbackJs.trim().isEmpty()) {
                Log.e(TAG, "Invalid callback function name");
                invokeErrorJs("onFileReadError", "无效的回调函数名");
                return;
            }
            if (startPercent < 0 || startPercent > 100 || endPercent < 0 || endPercent > 100 || startPercent > endPercent) {
                Log.e(TAG, "Invalid progress range: " + startPercent + " - " + endPercent);
                invokeErrorJs("onFileReadError", "无效的进度范围");
                return;
            }
            
            // 检查 WebView 状态
            if (isWebViewDestroyed || webView == null) {
                Log.e(TAG, "WebView is destroyed, cannot read file");
                invokeErrorJs("onFileReadError", "WebView已销毁");
                return;
            }

            new Thread(() -> {
                java.io.File tempFile = null;
                try {
                    Uri uri = Uri.parse(uriString);
                    ContentResolver resolver = getContentResolver();
                    InputStream inputStream = resolver.openInputStream(uri);

                    if (inputStream == null) {
                        Log.e(TAG, "Failed to open input stream");
                        runOnUiThread(() -> {
                            webView.evaluateJavascript(
                                "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('无法打开文件');",
                                null
                            );
                        });
                        return;
                    }

                    // 获取文件大小
                    long fileSize = -1;
                    android.database.Cursor cursor = resolver.query(uri, null, null, null, null);
                    if (cursor != null) {
                        int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                        if (sizeIndex >= 0 && cursor.moveToFirst()) {
                            fileSize = cursor.getLong(sizeIndex);
                        }
                        cursor.close();
                    }

                    Log.d(TAG, "File size: " + fileSize + " bytes");

                    // 创建临时文件
                    String originalName = getFileName(uri);
                    if (originalName == null || originalName.isEmpty()) {
                        originalName = "upload_" + System.currentTimeMillis() + ".zip";
                    }
                    // 安全文件名
                    String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");

                    java.io.File cacheDir = getCacheDir();
                    // 清理旧的临时文件
                    cleanOldTempFiles(cacheDir);

                    tempFile = new java.io.File(cacheDir, "bha_" + System.currentTimeMillis() + "_" + safeName);

                    // 关键修复：使用FileOutputStream流式写入，避免内存爆炸
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                    byte[] buffer = new byte[64 * 1024];
                    int bytesRead;
                    long totalRead = 0;
                    int lastReportedPercent = startPercent;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        // 实时更新进度
                        if (fileSize > 0) {
                            int currentPercent = (int) (startPercent + (totalRead * (endPercent - startPercent) / fileSize));

                            if (currentPercent > lastReportedPercent) {
                                lastReportedPercent = currentPercent;
                                final int progress = currentPercent;
                                final long readBytes = totalRead;
                                final long totalBytes = fileSize;

                                runOnUiThread(() -> {
                                    String jsCode = String.format(
                                        "if(window.BatteryHealthApp && window.BatteryHealthApp.updateProgress) {" +
                                        "  window.BatteryHealthApp.updateProgress(%d, '正在读取文件... " +
                                        "%.1fMB / %.1fMB');" +
                                        "}",
                                        progress,
                                        readBytes / (1024.0 * 1024.0),
                                        totalBytes / (1024.0 * 1024.0)
                                    );
                                    webView.evaluateJavascript(jsCode, null);
                                });
                            }
                        }
                    }

                    fos.flush();
                    fos.close();
                    inputStream.close();

                    final long finalSize = totalRead;
                    final String finalPath = tempFile.getAbsolutePath();
                    // 关键：使用WebViewAssetLoader的https://地址绕过file://访问限制
                    final String fileUrl = "https://appassets.androidplatform.net/bha/" + tempFile.getName();

                    Log.d(TAG, "File copied to cache: " + finalPath + " size=" + finalSize);
                    Log.d(TAG, "AssetLoader URL: " + fileUrl);

                    // 回调JS，传递file:// URL和文件信息（对象形式，避免大字符串）
                    runOnUiThread(() -> {
                        String jsCode = String.format(
                            "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                            "  window.BatteryHealthApp.%s({url: '%s', size: %d, name: '%s', path: '%s'});" +
                            "}",
                            callbackJs, callbackJs,
                            fileUrl.replace("'", "\\'"),
                            finalSize,
                            safeName.replace("'", "\\'"),
                            finalPath.replace("'", "\\'")
                        );
                        webView.evaluateJavascript(jsCode, null);
                        Log.d(TAG, "Callback executed: " + callbackJs);
                    });

                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "Out of memory while copying file", oom);
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('内存不足，请尝试较小的文件');",
                            null
                        );
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy file with progress", e);
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                    final String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            String.format(
                                "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('%s');",
                                errorMsg.replace("'", "\\'")
                            ),
                            null
                        );
                    });
                }
            }).start();
        }

        /**
         * 读取文件并返回 Base64 字符串
         * 关键修复：避免 WebViewAssetLoader URL 拦截在某些设备上不可靠的问题
         * 直接通过 JavaScriptInterface 返回 Base64，JS 端转为 ArrayBuffer
         * 支持进度回调
         *
         * @param uriString 文件 URI
         * @param callbackJs 成功回调
         * @param errorCallbackJs 错误回调
         * @param startPercent 起始进度
         * @param endPercent 结束进度
         */
        @JavascriptInterface
        public void readFileAsBase64(String uriString, final String callbackJs,
                                     final String errorCallbackJs,
                                     final int startPercent, final int endPercent) {
            Log.d(TAG, "=== readFileAsBase64 for: " + uriString + " ===");
            
            // 输入验证
            if (uriString == null || uriString.trim().isEmpty()) {
                Log.e(TAG, "Invalid URI string: null or empty");
                invokeErrorJs(errorCallbackJs, "无效的文件URI");
                return;
            }
            if (callbackJs == null || callbackJs.trim().isEmpty()) {
                Log.e(TAG, "Invalid callback function name");
                invokeErrorJs(errorCallbackJs, "无效的回调函数名");
                return;
            }
            if (errorCallbackJs == null || errorCallbackJs.trim().isEmpty()) {
                Log.e(TAG, "Invalid error callback function name");
                return;
            }
            if (startPercent < 0 || startPercent > 100 || endPercent < 0 || endPercent > 100 || startPercent > endPercent) {
                Log.e(TAG, "Invalid progress range: " + startPercent + " - " + endPercent);
                invokeErrorJs(errorCallbackJs, "无效的进度范围");
                return;
            }
            
            // 检查 WebView 状态
            if (isWebViewDestroyed || webView == null) {
                Log.e(TAG, "WebView is destroyed, cannot read file");
                invokeErrorJs(errorCallbackJs, "WebView已销毁");
                return;
            }
            
            new Thread(() -> {
                java.io.File tempFile = null;
                try {
                    Uri uri = Uri.parse(uriString);
                    ContentResolver resolver = getContentResolver();
                    InputStream inputStream = resolver.openInputStream(uri);
                    if (inputStream == null) {
                        invokeErrorJs(errorCallbackJs, "无法打开文件");
                        return;
                    }

                    // 文件大小
                    long fileSize = 0;
                    android.database.Cursor cursor = resolver.query(uri, null, null, null, null);
                    if (cursor != null) {
                        int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                        if (sizeIndex >= 0 && cursor.moveToFirst()) {
                            fileSize = cursor.getLong(sizeIndex);
                        }
                        cursor.close();
                    }
                    Log.d(TAG, "File size: " + fileSize + " bytes");

                    String originalName = getFileName(uri);
                    if (originalName == null || originalName.isEmpty()) {
                        originalName = "upload_" + System.currentTimeMillis() + ".zip";
                    }
                    String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");

                    java.io.File cacheDir = getCacheDir();
                    cleanOldTempFiles(cacheDir);
                    tempFile = new java.io.File(cacheDir, "bha_" + System.currentTimeMillis() + "_" + safeName);

                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                    byte[] buffer = new byte[64 * 1024];
                    int bytesRead;
                    long totalRead = 0;
                    int lastReportedPercent = startPercent;
                    long lastUiUpdateTime = 0;
                    long bytesSinceLastUpdate = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        bytesSinceLastUpdate += bytesRead;
                        if (fileSize > 0) {
                            int currentPercent = (int) (startPercent + (totalRead * (endPercent - startPercent) / fileSize));
                            long currentTime = System.currentTimeMillis();
                            if (currentPercent > lastReportedPercent &&
                                (currentTime - lastUiUpdateTime > 300 || bytesSinceLastUpdate > 512 * 1024)) {
                                lastReportedPercent = currentPercent;
                                lastUiUpdateTime = currentTime;
                                bytesSinceLastUpdate = 0;
                                final int progress = currentPercent;
                                final long readBytes = totalRead;
                                final long totalBytes = fileSize;
                                runOnUiThread(() -> {
                                    String jsCode = String.format(
                                        "if(window.BatteryHealthApp && window.BatteryHealthApp.updateProgress) {" +
                                        "  window.BatteryHealthApp.updateProgress(%d, '正在读取文件... %.1fMB / %.1fMB');" +
                                        "}",
                                        progress,
                                        readBytes / (1024.0 * 1024.0),
                                        totalBytes / (1024.0 * 1024.0)
                                    );
                                    webView.evaluateJavascript(jsCode, null);
                                });
                            }
                        }
                    }
                    fos.flush();
                    fos.close();
                    inputStream.close();

                    final long finalSize = totalRead;
                    final String finalPath = tempFile.getAbsolutePath();

                    // 关键修复：直接读取临时文件为 Base64，分块传输避免一次性占用大内存
                    final java.io.File finalTempFile = tempFile;
                    final long size = tempFile.length();
                    final long chunkSize = 1024 * 1024; // 1MB per chunk

                    // 启动新线程分块读取并 Base64
                    new Thread(() -> {
                        try {
                            java.io.FileInputStream fis = new java.io.FileInputStream(finalTempFile);
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream((int) size);
                            byte[] readBuffer = new byte[(int) Math.min(chunkSize, size)];
                            int read;
                            long readTotal = 0;
                            while ((read = fis.read(readBuffer)) != -1) {
                                baos.write(readBuffer, 0, read);
                                readTotal += read;
                            }
                            fis.close();
                            final String base64Data = android.util.Base64.encodeToString(
                                baos.toByteArray(), android.util.Base64.NO_WRAP);
                            baos.close();

                            final String finalName = safeName;
                            runOnUiThread(() -> {
                                String jsCode = String.format(
                                    "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                                    "  window.BatteryHealthApp.%s({data: '%s', size: %d, name: '%s', path: '%s'});" +
                                    "}",
                                    callbackJs, callbackJs,
                                    base64Data.replace("'", "\\'"),
                                    size,
                                    finalName.replace("'", "\\'"),
                                    finalPath.replace("'", "\\'")
                                );
                                webView.evaluateJavascript(jsCode, null);
                                Log.d(TAG, "Callback executed: " + callbackJs + " size=" + size);
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to encode file to base64", e);
                            if (finalTempFile.exists()) finalTempFile.delete();
                            invokeErrorJs(errorCallbackJs, "Base64 编码失败: " + e.getMessage());
                        }
                    }).start();

                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "Out of memory while copying file", oom);
                    if (tempFile != null && tempFile.exists()) tempFile.delete();
                    invokeErrorJs(errorCallbackJs, "内存不足，请尝试较小的文件");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy file with progress", e);
                    if (tempFile != null && tempFile.exists()) tempFile.delete();
                    invokeErrorJs(errorCallbackJs, e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            }).start();
        }

        private void invokeErrorJs(final String errorCallbackJs, final String message) {
            runOnUiThread(() -> {
                String js = String.format(
                    "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                    "  window.BatteryHealthApp.%s('%s');" +
                    "}",
                    errorCallbackJs, errorCallbackJs,
                    message.replace("'", "\\'")
                );
                webView.evaluateJavascript(js, null);
            });
        }

        /**
         * 清理旧的临时文件
         * 避免cacheDir累积
         */
        private void cleanOldTempFiles(java.io.File cacheDir) {
            try {
                java.io.File[] files = cacheDir.listFiles((dir, name) -> name.startsWith("bha_"));
                if (files != null && files.length > 5) {
                    // 按修改时间排序，删除最旧的
                    java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                    int toDelete = files.length - 5;
                    for (int i = 0; i < toDelete; i++) {
                        if (files[i].delete()) {
                            Log.d(TAG, "Cleaned old temp file: " + files[i].getName());
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to clean old temp files", e);
            }
        }

        /**
         * 清理所有临时文件
         */
        @JavascriptInterface
        public void cleanAllTempFiles() {
            try {
                java.io.File cacheDir = getCacheDir();
                java.io.File[] files = cacheDir.listFiles((dir, name) -> name.startsWith("bha_"));
                if (files != null) {
                    for (java.io.File f : files) {
                        f.delete();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to clean all temp files", e);
            }
        }

        /**
         * 删除指定的临时文件
         */
        @JavascriptInterface
        public void deleteTempFile(String filePath) {
            try {
                java.io.File file = new java.io.File(filePath);
                if (file.exists() && file.getName().startsWith("bha_")) {
                    file.delete();
                    Log.d(TAG, "Deleted temp file: " + filePath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete temp file", e);
            }
        }

        /**
         * 检查文件访问权限
         */
        @JavascriptInterface
        public boolean checkStoragePermission() {
            if (permissions == null || permissions.length == 0) {
                return true; // SAF模式无需权限
            }
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 请求存储权限
         */
        @JavascriptInterface
        public void requestStoragePermission() {
            runOnUiThread(() -> checkAndRequestPermissions());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "=== onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null) + " ===");

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            ValueCallback<Uri[]> callback = filePathCallback;
            filePathCallback = null;

            if (callback == null) {
                Log.w(TAG, "filePathCallback is null - cannot notify WebView");
                return;
            }

            Uri[] results = null;

            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                Log.d(TAG, "Selected URI: " + uri);

                if (uri != null) {
                    // 保留URI权限
                    try {
                        getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Log.d(TAG, "Persistable URI permission granted");
                    } catch (Exception e) {
                        Log.w(TAG, "Could not take persistable permission: " + e.getMessage());
                    }

                    // 检查文件大小
                    long fileSize = getFileSizeFromUri(uri);
                    Log.d(TAG, "File size: " + fileSize + " bytes");

                    if (fileSize > 0 && fileSize > MAX_FILE_SIZE) {
                        String sizeMB = String.format("%.1f", fileSize / (1024.0 * 1024.0));
                        Toast.makeText(this, "文件过大(" + sizeMB + "MB)，请选择小于500MB的文件", Toast.LENGTH_LONG).show();
                        callback.onReceiveValue(null);
                        return;
                    }

                    results = new Uri[]{uri};
                    String fileName = getFileName(uri);
                    Log.d(TAG, "File accepted: " + fileName);
                    Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();

                    // 通知JavaScript文件已选择
                    notifyFileSelected(uri.toString(), fileName);
                } else {
                    Log.w(TAG, "URI is null");
                    Toast.makeText(this, "文件选择失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "File selection cancelled: resultCode=" + resultCode);
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(this, "已取消选择", Toast.LENGTH_SHORT).show();
                }
            }

            // 关键：必须调用callback.onReceiveValue，否则WebView会挂起
            callback.onReceiveValue(results);
            Log.d(TAG, "Callback notified with results: " + (results != null ? results.length : "null"));
        }
    }

    /**
     * 通知JavaScript文件已选择
     */
    private void notifyFileSelected(String uriString, String fileName) {
        // 转义URI和文件名中的特殊字符
        String safeUri = uriString.replace("\\", "\\\\").replace("'", "\\'");
        String safeName = fileName.replace("\\", "\\\\").replace("'", "\\'");

        String jsCode =
            "(function() {" +
            "   try {" +
            "       console.log('Notify file selected:', '" + safeName + "');" +
            "       if (window.BatteryHealthApp && window.BatteryHealthApp.handleAndroidFileSelected) {" +
            "           window.BatteryHealthApp.handleAndroidFileSelected('" + safeUri + "', '" + safeName + "');" +
            "       }" +
            "       var fileNameDisplay = document.getElementById('selected-file-name');" +
            "       var fileDisplayContainer = document.getElementById('file-name-display');" +
            "       if (fileNameDisplay) {" +
            "           fileNameDisplay.textContent = '" + safeName + "';" +
            "       }" +
            "       if (fileDisplayContainer) {" +
            "           fileDisplayContainer.style.display = 'flex';" +
            "       }" +
            "       console.log('File selected notification processed successfully');" +
            "   } catch (e) {" +
            "       console.error('notifyFileSelected error:', e);" +
            "   }" +
            "})();";

        if (webView != null && !isWebViewDestroyed) {
            webView.evaluateJavascript(jsCode, null);
        }
        Log.d(TAG, "Notified JavaScript about file selection: " + fileName);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && !isWebViewDestroyed && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null && !isWebViewDestroyed) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null && !isWebViewDestroyed) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        isWebViewDestroyed = true;

        // 清理所有临时文件
        try {
            java.io.File cacheDir = getCacheDir();
            java.io.File[] files = cacheDir.listFiles((dir, name) -> name.startsWith("bha_"));
            if (files != null) {
                for (java.io.File f : files) {
                    f.delete();
                    Log.d(TAG, "Cleaned temp file on destroy: " + f.getName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean temp files on destroy", e);
        }

        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("AndroidFilePicker");
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.loadUrl("about:blank");
            webView.clearCache(true);
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
