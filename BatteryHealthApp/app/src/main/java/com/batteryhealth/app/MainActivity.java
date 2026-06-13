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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 电池健康度分析工具主Activity
 *
 * 功能特性：
 * - WebView安全加固
 * - 文件选择器支持（完整修复）
 * - JavaScript接口支持
 * - 内存泄漏防护
 * - 性能优化配置
 * - 大文件进度显示
 * - 网络安全配置
 *
 * @version 1.2.3
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置全屏模式，优化用户体验
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);

        // 初始化权限数组 - 根据Android版本适配
        initPermissions();

        // 检查并请求权限
        checkAndRequestPermissions();

        // 初始化WebView
        initWebView();
    }

    /**
     * 内存警告处理 - 优化大文件处理
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory called with level: " + level);
        
        isMemoryLow = true;
        
        if (level >= TRIM_MEMORY_MODERATE) {
            // 内存紧张时清理WebView缓存
            if (webView != null && !isWebViewDestroyed) {
                webView.clearCache(true);
                webView.clearHistory();
                Log.d(TAG, "WebView cache cleared due to memory pressure");
            }
            
            // 通知JavaScript端暂停处理
            if (webView != null) {
                webView.evaluateJavascript(
                    "if(window.BatteryHealthApp) window.BatteryHealthApp.onMemoryWarning();",
                    null
                );
            }
        }
        
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            // 极端内存压力时显示警告
            Toast.makeText(this, "内存不足，建议关闭其他应用", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 低内存警告处理
     */
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
     * 初始化权限数组 - 适配不同Android版本
     */
    private void initPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要媒体权限 + 读取文档权限
            permissions = new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12
            permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        } else {
            // Android 10及以下
            permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
    }

    /**
     * 检查并请求权限
     */
    private void checkAndRequestPermissions() {
        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "需要存储权限才能选择文件", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 初始化WebView - 安全加固与性能优化
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

        // 启用文件访问以支持文件选择器
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);

        // 仅允许 HTTPS 混合内容
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // 禁用不必要的功能
        webSettings.setGeolocationEnabled(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);

        // ========== 性能优化配置 ==========
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setTextZoom(100);

        // 硬件加速
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        // ========== 添加JavaScript接口 ==========
        // 关键修复：添加JavaScript接口，让JS可以调用Android原生文件选择器
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidFilePicker");
        Log.d(TAG, "JavaScript interface added");

        // ========== WebViewClient配置 ==========
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

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
                        Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(MainActivity.this, "安全连接错误", Toast.LENGTH_SHORT).show();
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
                Log.d(TAG, "onShowFileChooser triggered");
                
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                    MainActivity.this.filePathCallback = null;
                }

                MainActivity.this.filePathCallback = filePathCallback;

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
     * 关键修复：确保点击事件能触发Android文件选择器
     */
    private void injectFilePickerScript() {
        String jsCode =
            "(function() {" +
            "   try {" +
            "       var dropArea = document.getElementById('drop-area');" +
            "       var fileInput = document.getElementById('zip-file');" +
            "       if (dropArea) {" +
            "           // 移除可能存在的旧监听器，避免重复触发" +
            "           if (window.__bhaClickHandler) {" +
            "               dropArea.removeEventListener('click', window.__bhaClickHandler, true);" +
            "           }" +
            "           // 创建新的点击处理函数" +
            "           window.__bhaClickHandler = function(e) {" +
            "               e.preventDefault();" +
            "               e.stopPropagation();" +
            "               console.log('Drop area clicked, calling Android picker');" +
            "               if (window.AndroidFilePicker && window.AndroidFilePicker.openFilePicker) {" +
            "                   window.AndroidFilePicker.openFilePicker();" +
            "               } else {" +
            "                   console.warn('AndroidFilePicker not available');" +
            "                   if (fileInput) fileInput.click();" +
            "               }" +
            "           };" +
            "           // 使用捕获阶段，确保优先处理" +
            "           dropArea.addEventListener('click', window.__bhaClickHandler, true);" +
            "           console.log('File picker script injected successfully');" +
            "       } else {" +
            "           console.warn('Drop area element not found');" +
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
     */
    private void openFileChooser() {
        Log.d(TAG, "Opening file chooser");

        // 先清理旧的callback
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

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
        // 添加读取URI权限标志
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivityForResult(
                Intent.createChooser(intent, "选择诊断文件（ZIP格式）"),
                FILE_CHOOSER_REQUEST_CODE
            );
            Log.d(TAG, "File chooser intent launched");
        } catch (Exception ex) {
            Log.e(TAG, "Failed to open file chooser", ex);
            // 回退到ACTION_GET_CONTENT
            try {
                Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fallbackIntent.setType("*/*");
                fallbackIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(
                    Intent.createChooser(fallbackIntent, "选择诊断文件（ZIP格式）"),
                    FILE_CHOOSER_REQUEST_CODE
                );
                Log.d(TAG, "Fallback file chooser launched");
            } catch (Exception ex2) {
                Log.e(TAG, "Failed to open fallback file chooser", ex2);
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }
                Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_LONG).show();
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
        }
        
        return fileName;
    }

    /**
     * JavaScript接口类
     * 关键修复：让JavaScript可以调用Android原生文件选择器
     */
    public class WebAppInterface {
        @JavascriptInterface
        public void openFilePicker() {
            Log.d(TAG, "JavaScript called openFilePicker");
            runOnUiThread(() -> {
                // 直接触发文件选择器
                openFileChooser();
            });
        }

        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "JS Log: " + message);
        }

        /**
         * 读取文件内容并返回Base64编码
         * 关键修复：让JavaScript可以获取文件内容
         */
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

        /**
         * 获取文件大小
         * 用于计算进度
         */
        @JavascriptInterface
        public long getFileSize(String uriString) {
            Log.d(TAG, "JavaScript called getFileSize for: " + uriString);
            try {
                Uri uri = Uri.parse(uriString);
                ContentResolver resolver = getContentResolver();
                
                // 尝试从ContentResolver获取大小
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
                
                // 如果无法获取，返回-1表示需要手动计算
                return -1;
            } catch (Exception e) {
                Log.e(TAG, "Failed to get file size", e);
                return -1;
            }
        }

        /**
         * 带进度的文件读取方法
         * 分块读取文件，每次读取后通过JavaScript回调更新进度
         * @param uriString 文件URI
         * @param callbackJs JavaScript回调函数名
         * @param startPercent 进度条起始百分比
         * @param endPercent 进度条结束百分比
         */
        @JavascriptInterface
        public void readFileContentWithProgress(String uriString, final String callbackJs, 
                                                 final int startPercent, final int endPercent) {
            Log.d(TAG, "JavaScript called readFileContentWithProgress for: " + uriString);
            
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
                    
                    Log.d(TAG, "File size: " + fileSize);
                    
                    // 分块读取
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024]; // 64KB chunks for better progress updates
                    int bytesRead;
                    long totalRead = 0;
                    int lastReportedPercent = startPercent;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        
                        // 计算并更新进度
                        if (fileSize > 0) {
                            int currentPercent = (int) (startPercent + (totalRead * (endPercent - startPercent) / fileSize));
                            
                            // 每变化至少1%才更新，避免频繁调用
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
                    
                    inputStream.close();
                    
                    // 编码为Base64
                    byte[] fileBytes = outputStream.toByteArray();
                    final String base64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.DEFAULT);
                    
                    Log.d(TAG, "File content read successfully, size: " + fileBytes.length);
                    
                    // 调用JavaScript回调，传递Base64内容
                    runOnUiThread(() -> {
                        // 转义Base64字符串中的特殊字符
                        String escapedBase64 = base64.replace("\\", "\\\\")
                                                      .replace("'", "\\'")
                                                      .replace("\n", "\\n")
                                                      .replace("\r", "\\r");
                        
                        String jsCode = String.format(
                            "if(window.BatteryHealthApp && window.BatteryHealthApp.%s) {" +
                            "  window.BatteryHealthApp.%s('%s');" +
                            "}",
                            callbackJs, callbackJs, escapedBase64
                        );
                        webView.evaluateJavascript(jsCode, null);
                        Log.d(TAG, "Callback executed: " + callbackJs);
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read file content with progress", e);
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            String.format(
                                "if(window.BatteryHealthApp) window.BatteryHealthApp.onFileReadError('%s');",
                                e.getMessage().replace("'", "\\'")
                            ),
                            null
                        );
                    });
                }
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null));

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            // 保存callback临时变量
            ValueCallback<Uri[]> callback = filePathCallback;
            filePathCallback = null;

            if (callback == null) {
                Log.w(TAG, "filePathCallback is null");
                return;
            }

            Uri[] results = null;

            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                Log.d(TAG, "Selected URI: " + uri);

                if (uri != null) {
                    // 关键：保留URI权限，确保后续可读取
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
                        // 文件过大，拒绝处理
                        String sizeMB = String.format("%.1f", fileSize / (1024.0 * 1024.0));
                        Toast.makeText(this, "文件过大(" + sizeMB + "MB)，请选择小于200MB的文件", Toast.LENGTH_LONG).show();
                        callback.onReceiveValue(null);
                        return;
                    }

                    results = new Uri[]{uri};
                    String fileName = getFileName(uri);
                    Log.d(TAG, "File accepted: " + fileName);
                    Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();

                    // 关键修复：通知JavaScript文件已选择
                    notifyFileSelected(uri.toString(), fileName);
                } else {
                    Log.w(TAG, "URI is null");
                    Toast.makeText(this, "文件选择失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "File selection cancelled or failed: resultCode=" + resultCode);
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(this, "已取消选择", Toast.LENGTH_SHORT).show();
                }
            }

            // 关键：必须调用callback的onReceiveValue，否则WebView会挂起
            callback.onReceiveValue(results);
        }
    }

    /**
     * 获取文件大小
     * @param uri 文件URI
     * @return 文件大小（字节），-1表示无法获取
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
     * 通知JavaScript文件已选择
     * 关键修复：让JavaScript知道文件已选择并触发处理
     */
    private void notifyFileSelected(String uriString, String fileName) {
        // 转义URI和文件名中的特殊字符，避免JS注入
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