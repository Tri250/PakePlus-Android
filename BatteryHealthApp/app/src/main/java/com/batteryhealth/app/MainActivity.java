package com.batteryhealth.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.SslError;
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
 * - 文件选择器支持
 * - 内存泄漏防护
 * - 性能优化配置
 * 
 * @version 1.1.0
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
     */
    private void initPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的媒体权限
            permissions = new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12
            permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE
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
                // 检查是否可以再次请求
                boolean shouldShowRationale = false;
                for (String permission : permissions) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        shouldShowRationale = true;
                        break;
                    }
                }
                if (!shouldShowRationale) {
                    // 用户选择了"不再询问"，引导到设置页面
                    Toast.makeText(this, "请在设置中手动授予存储权限", Toast.LENGTH_LONG).show();
                }
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
        
        // 禁用不安全的文件访问 - 防止跨域攻击
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
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
        // 缓存策略
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAppCacheEnabled(true);
        webSettings.setAppCachePath(getCacheDir().getAbsolutePath());
        
        // 渲染优化
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        
        // 文字渲染优化
        webSettings.setTextZoom(100);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        
        // 硬件加速（如果支持）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        
        // ========== WebViewClient配置 ==========
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // 只允许本地资源
                if (url.startsWith("file:///android_asset/")) {
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
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 安全策略：拒绝处理SSL错误，防止中间人攻击
                Log.e(TAG, "SSL Error: " + error.toString());
                handler.cancel();
                Toast.makeText(MainActivity.this, "安全连接错误", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded: " + url);
            }
        });

        // ========== WebChromeClient配置 ==========
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // 清理之前的回调
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                    MainActivity.this.filePathCallback = null;
                }
                
                MainActivity.this.filePathCallback = filePathCallback;
                
                // 创建文件选择Intent
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                
                // 备选MIME类型
                String[] mimeTypes = {
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
                };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                intent.putExtra(Intent.EXTRA_TITLE, "选择诊断文件");
                
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "选择 ZIP 格式的诊断文件"),
                        FILE_CHOOSER_REQUEST_CODE
                    );
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to open file chooser", ex);
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                
                return true;
            }
            
            @Override
            public void onConsoleMessage(String message, String sourceId, int lineNumber) {
                // 调试日志输出
                Log.d(TAG, "WebView Console: " + message + " (" + sourceId + ":" + lineNumber + ")");
            }
        });

        // 加载本地HTML文件
        try {
            webView.loadUrl("file:///android_asset/index.html");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load HTML", e);
            Toast.makeText(this, "加载失败，请重启应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) {
                Log.w(TAG, "filePathCallback is null");
                return;
            }
            
            Uri[] results = null;
            
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    // 验证文件类型
                    String fileName = uri.getLastPathSegment();
                    if (fileName != null && fileName.toLowerCase().endsWith(".zip")) {
                        results = new Uri[]{uri};
                        Log.d(TAG, "File selected: " + fileName);
                    } else {
                        Toast.makeText(this, "请选择ZIP格式的文件", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && !isWebViewDestroyed && webView.canGoBack()) {
            webView.goBack();
        } else {
            // 双击退出确认
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                super.onBackPressed();
            }
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