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
 * 核心修复（v1.3.0）：
 * 1. 移除pauseTimers()调用 - 这是"请选择诊断文件"的直接原因
 *    pauseTimers()全局暂停WebView JS执行，导致evaluateJavascript无法执行
 *    onActivityResult中notifyFileSelected的JS代码被暂停，currentFile永远为null
 * 2. 添加getSelectedFileInfo()同步接口 - JS可直接从Java端同步获取文件信息
 *    不依赖evaluateJavascript异步执行，彻底解决时序问题
 * 3. onResume重新通知JS - 兜底机制，防止JS未收到文件信息
 * 4. onStop保护callback - 文件选择器活跃期间不清空callback
 * 5. onActivityResult无论callback状态都通知JS
 *
 * @version 1.3.0
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
    private boolean isFilePickerActive = false; // 标记文件选择器是否打开中

    // 关键修复：在Java端保存选中的文件信息，提供同步获取机制
    // 解决evaluateJavascript异步执行时序问题
    private volatile String pendingFileUri = null;
    private volatile String pendingFileName = null;

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
                        // 安全检查：防止路径遍历
                        if (path.contains("..") || path.contains("/")) {
                            Log.w(TAG, "Invalid path rejected: " + path);
                            return null;
                        }
                        // WebViewAssetLoader传入的path已经是URL解码后的
                        // 但为了健壮性，也尝试URL解码匹配
                        File cacheFile = new File(getCacheDir(), path);
                        if (cacheFile.exists() && cacheFile.getName().startsWith("bha_")) {
                            long size = cacheFile.length();
                            Log.d(TAG, "Serving file from cache: " + cacheFile.getAbsolutePath() + " size=" + size);
                            InputStream is = new FileInputStream(cacheFile);
                            return new WebResourceResponse("application/zip", "UTF-8", is);
                        }
                        // 如果直接匹配失败，尝试在缓存目录中查找相似文件名
                        // 处理URL编码/解码不一致的情况
                        File cacheDir = getCacheDir();
                        File[] bhaFiles = cacheDir.listFiles((dir, name) ->
                            name.startsWith("bha_") && name.endsWith(".zip"));
                        if (bhaFiles != null) {
                            for (File f : bhaFiles) {
                                // 比较URL解码后的路径和文件名
                                String decodedPath = Uri.decode(path);
                                if (f.getName().equals(path) || f.getName().equals(decodedPath)) {
                                    Log.d(TAG, "Found file via fallback match: " + f.getName());
                                    InputStream is = new FileInputStream(f);
                                    return new WebResourceResponse("application/zip", "UTF-8", is);
                                }
                            }
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
     * 关键修复：标记文件选择器活跃状态，防止onStop清空callback
     */
    private void openFileChooser() {
        Log.d(TAG, "=== openFileChooser called ===");
        isFilePickerActive = true;

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
     * 进度回调接口
     */
    private interface ProgressCallback {
        void onProgress(int percent, String message);
    }

    /**
     * JavaScript接口类
     */
    public class WebAppInterface {

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
            Log.d(TAG, "=== readFileContentWithProgress (stream parse ZIP) for: " + uriString + " ===");

            new Thread(() -> {
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

                    // 关键修复：在Java端流式解析ZIP，只提取目标文本文件内容
                    // 避免把整个ZIP加载到JS内存导致OOM
                    final String extractedContent = extractBatteryTextFromZip(inputStream, fileSize,
                        new ProgressCallback() {
                            @Override
                            public void onProgress(int percent, String message) {
                                final int p = percent;
                                final String m = message;
                                runOnUiThread(() -> {
                                    String jsCode = String.format(
                                        "if(window.BatteryHealthApp && window.BatteryHealthApp.updateProgress) {" +
                                        "  window.BatteryHealthApp.updateProgress(%d, '%s');" +
                                        "}",
                                        p, m.replace("'", "\\'")
                                    );
                                    webView.evaluateJavascript(jsCode, null);
                                });
                            }
                        }, startPercent, endPercent);

                    inputStream.close();

                    if (extractedContent == null) {
                        runOnUiThread(() -> {
                            webView.evaluateJavascript(
                                "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('未找到电池信息文件，请确认上传的是正确的诊断文件');",
                                null
                            );
                        });
                        return;
                    }

                    Log.d(TAG, "Extracted content length: " + extractedContent.length());

                    // 回调JS，传递提取的文本内容
                    // 使用新的回调格式：对象包含content字段
                    runOnUiThread(() -> {
                        String jsCode = String.format(
                            "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                            "  window.BatteryHealthApp.%s({content: '%s', size: %d});" +
                            "}",
                            callbackJs, callbackJs,
                            extractedContent.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n"),
                            extractedContent.length()
                        );
                        webView.evaluateJavascript(jsCode, null);
                        Log.d(TAG, "Callback executed with extracted content: " + callbackJs);
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Failed to process file", e);
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
         * 从ZIP输入流中提取电池相关的文本文件内容
         * 流式解析，避免加载整个ZIP到内存
         *
         * 关键策略（v1.4.0）：
         * 单次扫描过程中：
         * 1. 第一次遇到 bugreport-* 主文件时立即读取
         * 2. 如果主文件不包含电池信息，扫描所有 dumpstate 文件
         * 3. 限制单文件读取大小，避免 OOM
         */
        private String extractBatteryTextFromZip(InputStream inputStream, long fileSize,
                                                   ProgressCallback progressCallback,
                                                   int startPercent, int endPercent) {
            try {
                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream);
                java.util.zip.ZipEntry entry;
                long totalBytesRead = 0;
                int lastProgress = startPercent;

                // 第一遍：先找主 bugreport 文件
                String mainFileName = null;
                long mainFileMaxSize = 0;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName().toLowerCase();
                    long entrySize = entry.getSize();

                    if (fileSize > 0) {
                        totalBytesRead += entry.getCompressedSize();
                        int currentProgress = startPercent + (int) ((totalBytesRead * (endPercent - startPercent)) / (fileSize * 2));
                        if (currentProgress > lastProgress) {
                            lastProgress = currentProgress;
                            progressCallback.onProgress(currentProgress, "正在扫描: " + entry.getName());
                        }
                    }

                    if (!entry.isDirectory() && name.endsWith(".txt")) {
                        if ((name.contains("bugreport-") || name.contains("bugreport_"))) {
                            if (entrySize > mainFileMaxSize) {
                                mainFileMaxSize = entrySize;
                                mainFileName = entry.getName();
                            }
                        }
                    }
                    zis.closeEntry();
                }
                zis.close();
                Log.d(TAG, "Main bugreport file: " + mainFileName + " size=" + mainFileMaxSize);

                // 第二遍：优先读取主 bugreport 文件
                if (mainFileName != null) {
                    zis = new java.util.zip.ZipInputStream(inputStream);
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().equals(mainFileName)) {
                            progressCallback.onProgress(85, "正在解析: " + entry.getName());
                            String mainContent = readEntryContent(zis, 100 * 1024 * 1024);
                            zis.close();
                            if (mainContent != null && mainContent.length() > 0) {
                                String contentLower = mainContent.toLowerCase();
                                boolean hasBatteryInfo = contentLower.contains("battery") ||
                                                         contentLower.contains("health") ||
                                                         contentLower.contains("charge") ||
                                                         contentLower.contains("power_supply") ||
                                                         contentLower.contains("power supply") ||
                                                         contentLower.contains("dumpsys") ||
                                                         contentLower.contains("healthd") ||
                                                         contentLower.contains("voltage") ||
                                                         contentLower.contains("current") ||
                                                         contentLower.contains("capacity");
                                if (hasBatteryInfo) {
                                    Log.d(TAG, "Main bugreport contains battery info, size=" + mainContent.length());
                                    progressCallback.onProgress(95, "已找到电池信息");
                                    return mainContent;
                                } else {
                                    Log.d(TAG, "Main bugreport doesn't contain battery info");
                                }
                            }
                            break;
                        }
                        zis.closeEntry();
                    }
                    try { zis.close(); } catch (Exception e) {}
                }

                // 第三遍：扫描所有 dumpstate 文件
                zis = new java.util.zip.ZipInputStream(inputStream);
                StringBuilder combined = new StringBuilder();
                int maxTotalSize = 50 * 1024 * 1024;
                while ((entry = zis.getNextEntry()) != null) {
                    if (combined.length() >= maxTotalSize) {
                        zis.closeEntry();
                        continue;
                    }
                    String name = entry.getName().toLowerCase();
                    if (!entry.isDirectory() && name.contains("dumpstate") && name.endsWith(".txt")) {
                        progressCallback.onProgress(90, "正在解析: " + entry.getName());
                        String content = readEntryContent(zis, 30 * 1024 * 1024);
                        if (content != null && content.length() > 0) {
                            combined.append("\n\n===== FILE: ").append(entry.getName()).append(" =====\n");
                            combined.append(content);
                        }
                    } else {
                        zis.closeEntry();
                    }
                }
                try { zis.close(); } catch (Exception e) {}

                if (combined.length() > 0) {
                    Log.d(TAG, "Combined dumpstate files, total size=" + combined.length());
                    return combined.toString();
                }

                return null;

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract from ZIP", e);
                return null;
            }
        }

        /**
         * 从已定位的 ZipInputStream 读取 entry 内容（流式，限制大小）
         */
        private String readEntryContent(java.util.zip.ZipInputStream zis, int maxSize) {
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                int totalRead = 0;
                while ((bytesRead = zis.read(buffer)) != -1 && totalRead < maxSize) {
                    int toWrite = Math.min(bytesRead, maxSize - totalRead);
                    baos.write(buffer, 0, toWrite);
                    totalRead += toWrite;
                }
                return baos.toString("UTF-8");
            } catch (Exception e) {
                Log.e(TAG, "Failed to read ZIP entry content", e);
                return null;
            }
        }

        /**
         * 将文件读取为Base64字符串
         * 用于直接传给JS，绕过WebViewAssetLoader
         */
        private String readFileToBase64(java.io.File file) {
            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                byte[] buffer = new byte[(int) file.length()];
                int totalRead = 0;
                int bytesRead;
                while (totalRead < buffer.length &&
                       (bytesRead = fis.read(buffer, totalRead, buffer.length - totalRead)) != -1) {
                    totalRead += bytesRead;
                }
                fis.close();
                return android.util.Base64.encodeToString(buffer, android.util.Base64.NO_WRAP);
            } catch (Exception e) {
                Log.e(TAG, "Failed to read file to Base64", e);
                return null;
            }
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

        /**
         * 同步获取选中的文件信息
         * 关键修复：解决evaluateJavascript异步时序问题
         * 当evaluateJavascript因pauseTimers未执行时，JS可通过此方法同步获取文件信息
         * @return JSON格式的文件信息，或null
         */
        @JavascriptInterface
        public String getSelectedFileInfo() {
            if (pendingFileUri != null && pendingFileName != null) {
                return "{\"uri\":\"" + pendingFileUri.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\",\"name\":\"" + pendingFileName.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\",\"isAndroidUri\":true,\"size\":-1}";
            }
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "=== onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null) + " ===");

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            // 关键修复：标记文件选择器已关闭
            isFilePickerActive = false;

            ValueCallback<Uri[]> callback = filePathCallback;
            filePathCallback = null;

            // 处理文件选择结果
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
                        if (callback != null) {
                            callback.onReceiveValue(null);
                        }
                        return;
                    }

                    String fileName = getFileName(uri);
                    Log.d(TAG, "File accepted: " + fileName);
                    Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();

                    // 关键修复：在Java端保存文件信息，提供同步获取机制
                    pendingFileUri = uri.toString();
                    pendingFileName = fileName;

                    // 关键修复：无论callback是否为null，都必须通知JS
                    // 这是"请选择诊断文件"bug的根本原因：
                    // 之前onStop()会清空callback，导致onActivityResult直接return
                    // 不调用notifyFileSelected，JS端currentFile永远为null
                    notifyFileSelected(uri.toString(), fileName);

                    // 通知WebView文件选择结果
                    if (callback != null) {
                        Uri[] results = new Uri[]{uri};
                        callback.onReceiveValue(results);
                        Log.d(TAG, "Callback notified with results");
                    } else {
                        Log.w(TAG, "filePathCallback is null, but JS has been notified via notifyFileSelected");
                    }
                } else {
                    Log.w(TAG, "URI is null");
                    Toast.makeText(this, "文件选择失败", Toast.LENGTH_SHORT).show();
                    if (callback != null) {
                        callback.onReceiveValue(null);
                    }
                }
            } else {
                Log.d(TAG, "File selection cancelled: resultCode=" + resultCode);
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(this, "已取消选择", Toast.LENGTH_SHORT).show();
                }
                if (callback != null) {
                    callback.onReceiveValue(null);
                }
            }
        }
    }

    /**
     * 通知JavaScript文件已选择
     * 关键修复：使用全局变量+函数调用双重机制，确保currentFile一定被设置
     */
    private void notifyFileSelected(String uriString, String fileName) {
        // 转义特殊字符
        String safeUri = uriString.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
        String safeName = fileName.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");

        // 方案1：直接设置全局变量（最可靠，不依赖任何对象）
        // 方案2：调用handleAndroidFileSelected（如果可用）
        // 方案3：直接操作DOM显示文件名
        // 三重保障，确保文件信息一定传递到JS
        String jsCode =
            "(function() {" +
            "   try {" +
            "       console.log('=== notifyFileSelected ===');" +
            "       console.log('File name:', '" + safeName + "');" +
            "       console.log('URI length:', '" + safeUri.length() + "');" +
            "" +
            "       // 方案1：设置全局变量（最可靠）" +
            "       window.__androidSelectedFile = {" +
            "           uri: '" + safeUri + "'," +
            "           name: '" + safeName + "'," +
            "           isAndroidUri: true," +
            "           size: -1" +
            "       };" +
            "       console.log('Global file variable set:', window.__androidSelectedFile.name);" +
            "" +
            "       // 方案2：调用BatteryHealthApp方法（如果可用）" +
            "       if (window.BatteryHealthApp && typeof window.BatteryHealthApp.handleAndroidFileSelected === 'function') {" +
            "           window.BatteryHealthApp.handleAndroidFileSelected('" + safeUri + "', '" + safeName + "');" +
            "           console.log('BatteryHealthApp.handleAndroidFileSelected called');" +
            "       } else {" +
            "           console.warn('BatteryHealthApp not available, using global variable fallback');" +
            "       }" +
            "" +
            "       // 方案3：直接操作DOM" +
            "       var fileNameDisplay = document.getElementById('selected-file-name');" +
            "       var fileDisplayContainer = document.getElementById('file-name-display');" +
            "       if (fileNameDisplay) {" +
            "           fileNameDisplay.textContent = '" + safeName + "';" +
            "       }" +
            "       if (fileDisplayContainer) {" +
            "           fileDisplayContainer.style.display = 'flex';" +
            "       }" +
            "       console.log('File selected notification complete');" +
            "   } catch (e) {" +
            "       console.error('notifyFileSelected error:', e.message, e.stack);" +
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
            // 关键修复：移除pauseTimers()调用
            // pauseTimers()会全局暂停所有WebView的JS定时器和异步执行
            // 导致onActivityResult中的evaluateJavascript无法执行
            // 这是"请选择诊断文件"反复出现的直接原因之一
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null && !isWebViewDestroyed) {
            webView.onResume();
            // 关键修复：移除resumeTimers()，因为不再调用pauseTimers()

            // 兜底机制：如果有待处理的文件信息，重新通知JS
            // 防止evaluateJavascript因WebView暂停而未执行的情况
            if (pendingFileUri != null && pendingFileName != null) {
                Log.d(TAG, "onResume: re-notifying JS about pending file: " + pendingFileName);
                notifyFileSelected(pendingFileUri, pendingFileName);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 关键修复：文件选择器打开期间不清空callback
        // 因为onStop在文件选择器打开时会被调用（Activity进入后台）
        // 如果清空callback，onActivityResult就无法通知WebView
        if (!isFilePickerActive && filePathCallback != null) {
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
