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
    private static final long MAX_FILE_SIZE = 200 * 1024 * 1024; // 200MB限制

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

            // 关键修复：使用 safeEvaluateJavascript 自动检查 webView 空指针
            safeEvaluateJavascript(
                "if(window.BatteryHealthApp) window.BatteryHealthApp.onMemoryWarning();"
            );
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
        if (Build.VERSION.SDK_INT >= 30) {  // Android 11 (R) 及以上
            // Android 11+ - 使用SAF无需权限
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
                        Log.d(TAG, "AssetLoader request raw path: '" + path + "'");
                        // path 可能以 / 开头，需要去除
                        String safePath = path;
                        if (safePath.startsWith("/")) {
                            safePath = safePath.substring(1);
                        }
                        // path 格式: bha_1234567890_xxx.zip (相对于 /bha/ 前缀)
                        File cacheFile = new File(getCacheDir(), safePath);
                        Log.d(TAG, "AssetLoader looking for: " + cacheFile.getAbsolutePath() + " exists=" + cacheFile.exists());
                        if (cacheFile.exists() && cacheFile.getName().startsWith("bha_")) {
                            long size = cacheFile.length();
                            Log.d(TAG, "Serving file from cache: " + cacheFile.getAbsolutePath() + " size=" + size);
                            InputStream is = new FileInputStream(cacheFile);
                            // MIME类型：application/zip
                            return new WebResourceResponse("application/zip", null, is);
                        }
                        Log.w(TAG, "Cache file not found: " + safePath + " in " + getCacheDir().getAbsolutePath());
                        // 列出 cacheDir 中的文件帮助调试
                        File[] files = getCacheDir().listFiles((dir, name) -> name.startsWith("bha_"));
                        if (files != null) {
                            for (File f : files) {
                                Log.d(TAG, "Available cache file: " + f.getName());
                            }
                        }
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

        // 关键修复：使用 safeEvaluateJavascript 自动检查 webView 空指针和异常
        safeEvaluateJavascript(jsCode);
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
                "application/x-zip-compressed",
                "application/octet-stream",
                "*/*"
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
     * 全局安全的webView调用，自动检查webView空指针
     * 关键修复（v2.1.1）：所有异步回调都通过此方法调用 webView.evaluateJavascript
     * 防止 Activity 销毁后异步线程回调导致 NPE 闪退（3秒闪退根因）
     * @param jsCode 要执行的JS代码
     */
    private void safeEvaluateJavascript(final String jsCode) {
        runOnUiThread(() -> {
            if (webView == null) {
                Log.e(TAG, "webView is null, cannot evaluate JS");
                return;
            }
            try {
                webView.evaluateJavascript(jsCode, null);
            } catch (Exception e) {
                Log.e(TAG, "evaluateJavascript failed", e);
            }
        });
    }

    /**
     * JavaScript接口类
     */
    public class WebAppInterface {

        /**
         * 使用 Native 库分析 Bugreport 文件
         * @param filePath 临时文件路径
         * @return JSON 格式的分析结果
         */
        @JavascriptInterface
        public String analyzeBugreportNative(String filePath) {
            Log.d(TAG, "=== analyzeBugreportNative called for: " + filePath + " ===");
            
            try {
                // 初始化 Native 库
                BatteryAnalyzer.init();
                
                if (!BatteryAnalyzer.isNativeAvailable()) {
                    Log.w(TAG, "Native library not available");
                    return "{\"error\": \"Native library not loaded\"}";
                }
                
                // 第一步：解析文件获取原始数据
                BatteryParseResult parseResult = BatteryAnalyzer.parseFile(filePath);
                
                if (parseResult == null || !parseResult.hasData) {
                    Log.w(TAG, "Native parse returned no data");
                    return "{\"error\": \"未找到电池信息，请确认上传的是正确的诊断文件\", \"hasData\": false}";
                }
                
                // 第二步：计算健康度
                BatteryHealthResult healthResult = BatteryAnalyzer.calculateHealth(parseResult);
                
                // 构建 JSON 结果（包含解析数据 + 健康度数据）
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"success\": true,");
                json.append("\"hasData\": true,");
                
                // 解析数据
                json.append("\"brand\": \"").append(escapeJson(parseResult.getBrandText())).append("\",");
                json.append("\"model\": \"").append(escapeJson(parseResult.getModelText())).append("\",");
                json.append("\"designCapacityMah\": ").append(parseResult.designCapacityMah).append(",");
                json.append("\"currentCapacityMah\": ").append(parseResult.currentCapacityMah).append(",");
                json.append("\"chargeCounterMah\": ").append(parseResult.chargeCounterMah).append(",");
                json.append("\"cycleCount\": ").append(parseResult.cycleCount).append(",");
                json.append("\"temperatureCelsius\": ").append(parseResult.temperatureCelsius).append(",");
                json.append("\"manufacturingDate\": \"").append(escapeJson(parseResult.manufacturingDate != null ? parseResult.manufacturingDate : "")).append("\",");
                json.append("\"capacityRetention\": \"").append(escapeJson(parseResult.getCapacityRetentionText())).append("\",");
                
                // 健康度数据
                json.append("\"healthPercentage\": ").append(healthResult.healthPercentage).append(",");
                json.append("\"grade\": \"").append(healthResult.grade).append("\",");
                json.append("\"gradeColor\": \"").append(healthResult.gradeColor).append("\",");
                json.append("\"gradeDescription\": \"").append(escapeJson(healthResult.gradeDescription)).append("\",");
                json.append("\"diagnosisText\": \"").append(escapeJson(healthResult.diagnosisText)).append("\",");
                json.append("\"confidence\": ").append(healthResult.confidence).append(",");
                
                // 建议列表
                json.append("\"suggestions\": [");
                if (healthResult.suggestions != null && !healthResult.suggestions.isEmpty()) {
                    for (int i = 0; i < healthResult.suggestions.size(); i++) {
                        if (i > 0) json.append(",");
                        json.append("\"").append(escapeJson(healthResult.suggestions.get(i))).append("\"");
                    }
                }
                json.append("],");
                
                // 因子数据
                if (healthResult.factors != null) {
                    json.append("\"factors\": {");
                    json.append("\"capacityRetention\": ").append(healthResult.factors.capacityRetention).append(",");
                    json.append("\"cycleDecay\": ").append(healthResult.factors.cycleDecay).append(",");
                    json.append("\"resistanceGrowth\": ").append(healthResult.factors.resistanceGrowth).append(",");
                    json.append("\"thermalAging\": ").append(healthResult.factors.thermalAging).append(",");
                    json.append("\"chargingDamage\": ").append(healthResult.factors.chargingDamage).append(",");
                    json.append("\"availableFactors\": ").append(healthResult.factors.availableFactors);
                    json.append("},");
                } else {
                    json.append("\"factors\": null,");
                }
                
                // 辅助数据
                json.append("\"estimatedResistanceMohm\": ").append(healthResult.estimatedResistanceMohm).append(",");
                json.append("\"remainingLifespanMonths\": ").append(healthResult.remainingLifespanMonths);
                json.append("}");
                
                String result = json.toString();
                Log.d(TAG, "Native analysis result length: " + result.length());
                return result;
                
            } catch (Exception e) {
                Log.e(TAG, "Native analysis failed", e);
                return "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
            }
        }
        
        /**
         * 全流程 Native 分析：复制文件 + 解析 + 健康度计算
         * 完全在 Java/Native 端完成，不经过 fetch/CORS
         * @param uriString 文件 URI
         * @param callbackJs JS 回调函数名
         */
        @JavascriptInterface
        public void analyzeFileFullNative(String uriString, final String callbackJs) {
            Log.d(TAG, "=== analyzeFileFullNative called for: " + uriString + " ===");
            
            new Thread(() -> {
                java.io.File tempFile = null;
                try {
                    // 第一步：复制文件到缓存
                    Uri uri = Uri.parse(uriString);
                    ContentResolver resolver = getContentResolver();
                    InputStream inputStream = resolver.openInputStream(uri);
                    
                    if (inputStream == null) {
                        callbackError(callbackJs, "无法打开文件");
                        return;
                    }
                    
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
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    fos.flush();
                    fos.close();
                    inputStream.close();
                    
                    Log.d(TAG, "File copied to: " + tempFile.getAbsolutePath() + " size=" + tempFile.length());
                    
                    // 第二步：初始化 Native 库
                    BatteryAnalyzer.init();
                    if (!BatteryAnalyzer.isNativeAvailable()) {
                        callbackError(callbackJs, "Native库未加载");
                        return;
                    }
                    
                    // 第三步：解析文件
                    BatteryParseResult parseResult = BatteryAnalyzer.parseFile(tempFile.getAbsolutePath());
                    
                    if (parseResult == null || !parseResult.hasData) {
                        Log.w(TAG, "Native parse returned no data");
                        String diagInfo = parseResult != null ? parseResult.parseLog : "null result";
                        callbackError(callbackJs, "未找到电池信息，请确认上传的是正确的诊断文件 [" + diagInfo + "]");
                        return;
                    }
                    
                    // 第四步：计算健康度
                    BatteryHealthResult healthResult = BatteryAnalyzer.calculateHealth(parseResult);
                    if (healthResult == null) {
                        Log.e(TAG, "calculateHealth returned null");
                        callbackError(callbackJs, "健康度计算失败");
                        return;
                    }
                    
                    // 第五步：构建 JSON 结果
                    // 安全序列化 float：NaN/Infinity 转为 0
                    StringBuilder json = new StringBuilder();
                    json.append("{");
                    json.append("\"success\": true,");
                    json.append("\"hasData\": true,");
                    json.append("\"brand\": \"").append(escapeJson(parseResult.getBrandText())).append("\",");
                    json.append("\"model\": \"").append(escapeJson(parseResult.getModelText())).append("\",");
                    json.append("\"designCapacityMah\": ").append(parseResult.designCapacityMah).append(",");
                    json.append("\"currentCapacityMah\": ").append(parseResult.currentCapacityMah).append(",");
                    json.append("\"chargeCounterMah\": ").append(parseResult.chargeCounterMah).append(",");
                    json.append("\"cycleCount\": ").append(parseResult.cycleCount).append(",");
                    json.append("\"temperatureCelsius\": ").append(safeFloat(parseResult.temperatureCelsius)).append(",");
                    json.append("\"manufacturingDate\": \"").append(escapeJson(parseResult.manufacturingDate != null ? parseResult.manufacturingDate : "")).append("\",");
                    json.append("\"capacityRetention\": \"").append(escapeJson(parseResult.getCapacityRetentionText())).append("\",");
                    json.append("\"healthPercentage\": ").append(safeFloat(healthResult.healthPercentage)).append(",");
                    json.append("\"grade\": \"").append(escapeJson(healthResult.grade != null ? healthResult.grade : "F")).append("\",");
                    json.append("\"gradeColor\": \"").append(escapeJson(healthResult.gradeColor != null ? healthResult.gradeColor : "#9E9E9E")).append("\",");
                    json.append("\"gradeDescription\": \"").append(escapeJson(healthResult.gradeDescription != null ? healthResult.gradeDescription : "")).append("\",");
                    json.append("\"diagnosisText\": \"").append(escapeJson(healthResult.diagnosisText != null ? healthResult.diagnosisText : "")).append("\",");
                    json.append("\"confidence\": ").append(healthResult.confidence).append(",");
                    
                    json.append("\"suggestions\": [");
                    if (healthResult.suggestions != null && !healthResult.suggestions.isEmpty()) {
                        for (int i = 0; i < healthResult.suggestions.size(); i++) {
                            if (i > 0) json.append(",");
                            json.append("\"").append(escapeJson(healthResult.suggestions.get(i) != null ? healthResult.suggestions.get(i) : "")).append("\"");
                        }
                    }
                    json.append("],");
                    
                    if (healthResult.factors != null) {
                        json.append("\"factors\": {");
                        json.append("\"capacityRetention\": ").append(safeFloat(healthResult.factors.capacityRetention)).append(",");
                        json.append("\"cycleDecay\": ").append(safeFloat(healthResult.factors.cycleDecay)).append(",");
                        json.append("\"resistanceGrowth\": ").append(safeFloat(healthResult.factors.resistanceGrowth)).append(",");
                        json.append("\"thermalAging\": ").append(safeFloat(healthResult.factors.thermalAging)).append(",");
                        json.append("\"chargingDamage\": ").append(safeFloat(healthResult.factors.chargingDamage)).append(",");
                        json.append("\"availableFactors\": ").append(healthResult.factors.availableFactors);
                        json.append("},");
                    } else {
                        json.append("\"factors\": null,");
                    }
                    
                    json.append("\"estimatedResistanceMohm\": ").append(safeFloat(healthResult.estimatedResistanceMohm)).append(",");
                    json.append("\"remainingLifespanMonths\": ").append(healthResult.remainingLifespanMonths);
                    json.append("}");
                    
                    final String resultJson = json.toString();
                    Log.d(TAG, "Native full analysis complete, result length: " + resultJson.length());

                    // 第六步：回调 JS
                    // 关键修复：使用 safeEvaluateJavascript 自动检查 webView 空指针
                    final String finalResultJson = resultJson;
                    final String finalCallbackJs = callbackJs;
                    safeEvaluateJavascript(String.format(
                        "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                        "  window.BatteryHealthApp.%s(%s);" +
                        "}",
                        finalCallbackJs, finalCallbackJs,
                        finalResultJson
                    ));
                    
                } catch (Exception e) {
                    Log.e(TAG, "analyzeFileFullNative failed", e);
                    callbackError(callbackJs, "分析失败: " + e.getMessage());
                } finally {
                    // 清理临时文件
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                        Log.d(TAG, "Cleaned temp file: " + tempFile.getName());
                    }
                }
            }).start();
        }
        
        /**
         * 回调 JS 错误
         * 关键修复：使用 safeEvaluateJavascript 自动检查 webView 空指针
         * 防止 Activity 销毁后异步回调导致 NPE 闪退
         */
        private void callbackError(String callbackJs, String errorMsg) {
            // 安全转义 JS 字符串
            String safeMsg = errorMsg
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
            String jsCode = String.format(
                "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                "  window.BatteryHealthApp.%s({error: '%s'});" +
                "}",
                callbackJs, callbackJs,
                safeMsg
            );
            safeEvaluateJavascript(jsCode);
        }

        /**
         * 获取解析摘要
         */
        @JavascriptInterface
        public String getParseSummaryNative(String filePath) {
            Log.d(TAG, "getParseSummaryNative called for: " + filePath);
            
            try {
                BatteryAnalyzer.init();
                if (!BatteryAnalyzer.isNativeAvailable()) {
                    return "Native库未加载";
                }
                
                BatteryParseResult parseResult = BatteryAnalyzer.parseFile(filePath);
                return BatteryAnalyzer.getParseSummary(parseResult);
                
            } catch (Exception e) {
                Log.e(TAG, "Get summary failed", e);
                return "解析失败: " + e.getMessage();
            }
        }
        
        /**
         * 检查 Native 库是否可用
         */
        @JavascriptInterface
        public boolean isNativeLibraryAvailable() {
            BatteryAnalyzer.init();
            return BatteryAnalyzer.isNativeAvailable();
        }
        
        /**
         * 获取 Native 库版本信息
         */
        @JavascriptInterface
        public String getNativeLibraryVersion() {
            return "1.3.0-native";
        }
        
        /**
         * JSON 字符串转义
         */
        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t");
        }

        /**
         * 安全序列化 float：NaN/Infinity 转为 0
         * 防止 JSON 中出现 NaN/Infinity 导致 JS 解析崩溃
         */
        private String safeFloat(float v) {
            if (Float.isNaN(v) || Float.isInfinite(v)) return "0";
            return String.valueOf(v);
        }

        @JavascriptInterface
        public void openFilePicker() {
            Log.d(TAG, "=== JavaScript called openFilePicker ===");
            runOnUiThread(() -> {
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

        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "JS Log: " + message);
        }

        @JavascriptInterface
        public String readFileContent(String uriString) {
            Log.d(TAG, "JavaScript called readFileContent for: " + uriString);
            try {
                Uri uri = Uri.parse(uriString);
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
            try {
                Uri uri = Uri.parse(uriString);
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

            new Thread(() -> {
                java.io.File tempFile = null;
                try {
                    Uri uri = Uri.parse(uriString);
                    ContentResolver resolver = getContentResolver();
                    InputStream inputStream = resolver.openInputStream(uri);

                    if (inputStream == null) {
                        Log.e(TAG, "Failed to open input stream");
                        safeEvaluateJavascript(
                            "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('无法打开文件');"
                        );
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

                                final String jsCode = String.format(
                                    "if(window.BatteryHealthApp && window.BatteryHealthApp.updateProgress) {" +
                                    "  window.BatteryHealthApp.updateProgress(%d, '正在读取文件... " +
                                    "%.1fMB / %.1fMB');" +
                                    "}",
                                    progress,
                                    readBytes / (1024.0 * 1024.0),
                                    totalBytes / (1024.0 * 1024.0)
                                );
                                safeEvaluateJavascript(jsCode);
                            }
                        }
                    }

                    fos.flush();
                    fos.close();
                    inputStream.close();

                    // 关键修复：使用 safeEvaluateJavascript 自动检查 webView 空指针，
                    // 防止 Activity 销毁后异步线程回调导致 NPE 闪退（3秒闪退根因）
                    final long finalSize = totalRead;
                    final String finalPath = tempFile.getAbsolutePath();
                    // 关键：使用WebViewAssetLoader的https://地址绕过file://访问限制
                    final String finalFileUrl = "https://appassets.androidplatform.net/bha/" + tempFile.getName();
                    final String finalSafeName = safeName;
                    final String finalCallbackJs = callbackJs;

                    Log.d(TAG, "File copied to cache: " + finalPath + " size=" + finalSize);
                    Log.d(TAG, "AssetLoader URL: " + finalFileUrl);

                    safeEvaluateJavascript(String.format(
                        "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                        "  window.BatteryHealthApp.%s({url: '%s', size: %d, name: '%s', path: '%s'});" +
                        "}",
                        finalCallbackJs, finalCallbackJs,
                        finalFileUrl.replace("'", "\\'"),
                        finalSize,
                        finalSafeName.replace("'", "\\'"),
                        finalPath.replace("'", "\\'")
                    ));
                    Log.d(TAG, "Callback executed: " + finalCallbackJs);

                } catch (OutOfMemoryError oom) {
                    Log.e(TAG, "Out of memory while copying file", oom);
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                    safeEvaluateJavascript(
                        "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('内存不足，请尝试较小的文件');"
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy file with progress", e);
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                    final String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    safeEvaluateJavascript(
                        String.format(
                            "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('%s');",
                            errorMsg.replace("'", "\\'")
                        )
                    );
                }
            }).start();
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
                        Toast.makeText(this, "文件过大(" + sizeMB + "MB)，请选择小于200MB的文件", Toast.LENGTH_LONG).show();
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

        // 关键修复：使用 safeEvaluateJavascript 自动检查 webView 空指针
        safeEvaluateJavascript(jsCode);
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
