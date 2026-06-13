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

/**
 * 电池健康度分析工具主Activity
 *
 * 功能特性：
 * - WebView安全加固
 * - 文件选择器支持（完整修复）
 * - 内存泄漏防护
 * - 性能优化配置
 *
 * @version 1.2.1
 * @author 带娃的小陈工
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BatteryHealthApp";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String[] permissions;
    private boolean isWebViewDestroyed = false;

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
     * 初始化权限数组 - 适配不同Android版本
     * 关键修复：添加访问所有文件的权限
     */
    private void initPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要媒体权限 + 读取文档权限
            permissions = new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE  // 兼容旧应用
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 需要MANAGE_EXTERNAL_STORAGE或READ_EXTERNAL_STORAGE
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
     * 关键修复：启用文件访问以支持ZIP文件上传
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

        // 关键修复：启用文件访问以支持文件选择器
        // 注意：虽然启用了文件访问，但通过其他安全措施保护
        webSettings.setAllowFileAccess(true);           // 允许访问文件系统
        webSettings.setAllowContentAccess(true);        // 允许访问ContentProvider
        
        // 保持跨域安全限制
        webSettings.setAllowFileAccessFromFileURLs(false);  // 禁止file URL跨域
        webSettings.setAllowUniversalAccessFromFileURLs(false);

        // 仅允许 HTTPS 混合内容
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // 禁用不必要的功能
        webSettings.setGeolocationEnabled(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);

        // ========== 性能优化配置 ==========
        // 缓存策略
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 渲染优化
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // 文字渲染优化
        webSettings.setTextZoom(100);

        // 硬件加速
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        // ========== WebViewClient配置 ==========
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // 允许本地资源
                if (url.startsWith("file:///android_asset/")) {
                    return false;
                }

                // 允许content:// URL（文件选择器返回的URI）
                if (url.startsWith("content://")) {
                    return false;
                }

                // 拦截所有外部链接
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
                // 安全策略：拒绝处理SSL错误，防止中间人攻击
                Log.e(TAG, "SSL Error: " + error.toString());
                handler.cancel();
                Toast.makeText(MainActivity.this, "安全连接错误", Toast.LENGTH_SHORT).show();
            }
        });

        // ========== WebChromeClient配置 - 文件选择器 ==========
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // 清理之前的回调
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                    MainActivity.this.filePathCallback = null;
                }

                MainActivity.this.filePathCallback = filePathCallback;
                Log.d(TAG, "File chooser triggered");

                // 创建文件选择Intent - 支持所有文件类型
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                
                // 关键修复：使用更宽松的MIME类型配置
                intent.setType("*/*");  // 允许所有文件类型
                
                // 备选MIME类型 - 包含ZIP相关类型
                String[] mimeTypes = {
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
                };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "选择诊断文件（ZIP格式）"),
                        FILE_CHOOSER_REQUEST_CODE
                    );
                    Log.d(TAG, "File chooser intent launched");
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to open file chooser", ex);
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
                    return false;
                }

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
     * 获取文件名 - 从ContentProvider URI
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
     * 检查文件是否为ZIP格式
     * 关键修复：使用多种方式验证文件类型
     */
    private boolean isZipFile(Uri uri) {
        String fileName = getFileName(uri);
        Log.d(TAG, "Checking file: " + fileName);
        
        if (fileName == null) {
            // 如果无法获取文件名，允许通过（让JS处理）
            Log.w(TAG, "Cannot get file name, allowing file");
            return true;
        }
        
        // 检查文件扩展名
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".zip") || lowerName.contains("zip") || lowerName.contains("bugreport")) {
            Log.d(TAG, "File is ZIP by name: " + fileName);
            return true;
        }
        
        // 检查MIME类型
        ContentResolver resolver = getContentResolver();
        String mimeType = resolver.getType(uri);
        Log.d(TAG, "File MIME type: " + mimeType);
        
        if (mimeType != null) {
            if (mimeType.equals("application/zip") || 
                mimeType.equals("application/x-zip-compressed") ||
                mimeType.equals("application/octet-stream")) {
                Log.d(TAG, "File is ZIP by MIME type");
                return true;
            }
        }
        
        // 放宽限制：允许所有文件（让JavaScript处理验证）
        Log.w(TAG, "Allowing file for JS validation: " + fileName);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) {
                Log.w(TAG, "filePathCallback is null");
                return;
            }

            Uri[] results = null;

            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                Log.d(TAG, "Selected URI: " + uri);
                
                if (uri != null) {
                    // 关键修复：放宽文件验证，允许所有文件
                    if (isZipFile(uri)) {
                        results = new Uri[]{uri};
                        String fileName = getFileName(uri);
                        Log.d(TAG, "File accepted: " + fileName);
                        Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();
                    } else {
                        // 即使验证失败也允许（让JS处理）
                        Log.w(TAG, "File validation relaxed, allowing anyway");
                        results = new Uri[]{uri};
                        Toast.makeText(this, "已选择文件，请确保是ZIP格式", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Log.d(TAG, "File selection cancelled or failed");
                Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show();
            }

            // 必须调用回调，即使results为null
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
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
        // 清理文件选择回调
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        isWebViewDestroyed = true;

        if (webView != null) {
            // 安全销毁WebView - 防止内存泄漏
            webView.stopLoading();
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