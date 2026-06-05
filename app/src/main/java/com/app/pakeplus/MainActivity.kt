package com.app.pakeplus

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.pakeplus.utils.LocationPermissionManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/**
 * 掌上商客主界面
 * 基于 WebView 加载 LBS 雷达应用，处理各类权限和外部跳转
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var locationManager: LocationPermissionManager

    // 文件上传回调
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    // 摄像头/麦克风权限
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var pendingPermissionRequest: PermissionRequest? = null

    // 定位权限
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null

    // 全屏视频
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = 0

    // 配置项
    private var isFullScreenMode: Boolean = false
    private var mainFrameLoadError: Boolean = false
    private var showLaunchSplash: Boolean = false
    private var keepScreenOnFromConfig: Boolean = false
    private var allowCallPhoneFromConfig: Boolean = false
    private var locationEnabledFromConfig: Boolean = true

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager = LocationPermissionManager(this)

        // 初始化文件选择器
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val resultCode = result.resultCode
            val data = result.data
            val callback = fileUploadCallback ?: return@registerForActivityResult

            var results: Array<Uri>? = null
            if (resultCode == RESULT_OK && data != null) {
                val dataString = data.dataString
                val clipData = data.clipData

                when {
                    clipData != null -> {
                        results = Array(clipData.itemCount) { i ->
                            clipData.getItemAt(i).uri
                        }
                    }
                    dataString != null -> {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }
            callback.onReceiveValue(results)
            fileUploadCallback = null
        }

        // 摄像头/麦克风权限
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val request = pendingPermissionRequest ?: return@registerForActivityResult

            val allGranted = permissions.values.all { it }
            if (allGranted) {
                request.grant(request.resources)
            } else {
                showTopToast(getString(R.string.permission_required), Toast.LENGTH_SHORT)
                request.deny()
            }
            pendingPermissionRequest = null
        }

        // 定位权限
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val origin = pendingGeolocationOrigin
            val geoCallback = pendingGeolocationCallback
            pendingGeolocationOrigin = null
            pendingGeolocationCallback = null

            if (origin != null && geoCallback != null) {
                val fine = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarse = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (fine || coarse) {
                    geoCallback.invoke(origin, true, false)
                } else {
                    geoCallback.invoke(origin, false, false)
                    showTopToast(getString(R.string.location_permission_denied), Toast.LENGTH_LONG)
                }
            }
        }

        // 加载配置
        val config = parseJsonWithNative("app.json")
        val fullScreen = config?.get("fullScreen") as? Boolean ?: true
        val gesture = config?.get("gesture") as? Boolean ?: true
        val debug = config?.get("debug") as? Boolean ?: false
        val userAgent = config?.get("userAgent") as? String ?: ""
        val webUrl = config?.get("webUrl") as? String ?: "https://handbiz.example.com"
        val clearCache = config?.get("clearCache") as? Boolean ?: false
        val setZoom = config?.get("setZoom") as? Boolean ?: false
        allowCallPhoneFromConfig = config?.get("callPhone") as? Boolean ?: true
        locationEnabledFromConfig = config?.get("location") as? Boolean ?: true
        val launchCfg = config?.get("launch") as? String
        showLaunchSplash = !launchCfg.isNullOrBlank()
        keepScreenOnFromConfig = config?.get("screenOn") as? Boolean ?: true

        if (keepScreenOnFromConfig) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        WebView.setWebContentsDebuggingEnabled(debug)

        // 全屏模式配置
        isFullScreenMode = fullScreen
        if (fullScreen) {
            setupFullScreenMode()
        }

        enableEdgeToEdge()
        setContentView(R.layout.single_main)

        if (!showLaunchSplash) {
            findViewById<View>(R.id.splash_overlay).visibility = View.GONE
        }

        // 系统安全区域
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ConstraintLayout)) { view, insets ->
            val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBar.left, systemBar.top, systemBar.right, systemBar.bottom)
            insets
        }

        if (isFullScreenMode) {
            window.decorView.post { hideSystemUI() }
        }

        // 初始化 WebView
        setupWebView(userAgent, clearCache, setZoom, gesture, debug, webUrl)
    }

    /**
     * 配置全屏模式
     */
    private fun setupFullScreenMode() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    /**
     * 配置 WebView
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(
        userAgent: String,
        clearCache: Boolean,
        setZoom: Boolean,
        gesture: Boolean,
        debug: Boolean,
        webUrl: String
    ) {
        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 关键：启用 WebView 定位
            setGeolocationEnabled(locationEnabledFromConfig)
            allowFileAccess = true
            useWideViewPort = true
            allowFileAccessFromFileURLs = true
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            databaseEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // 启用缩放
            setSupportZoom(setZoom)
            builtInZoomControls = setZoom
            displayZoomControls = false
            // 文字大小
            textZoom = 100
        }

        if (userAgent.isNotEmpty()) {
            webView.settings.userAgentString = userAgent
        }

        if (clearCache) {
            webView.clearCache(true)
        }

        // JS 桥接
        webView.addJavascriptInterface(JsInterface(this), "JsBridge")
        webView.webViewClient = MyWebViewClient(debug)
        webView.webChromeClient = MyChromeClient(this)

        // 下载监听
        webView.setDownloadListener { url, ua, contentDisposition, mimetype, _ ->
            if (tryHandleSpecialSchemeDownload(url, contentDisposition, mimetype)) return@setDownloadListener
            startDownload(url, ua, contentDisposition, mimetype)
        }

        // 手势检测
        setupGestureDetector(gesture)

        // 加载 URL
        webView.loadUrl(webUrl)
    }

    /**
     * 配置手势检测
     */
    private fun setupGestureDetector(gesture: Boolean) {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) && abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (diffX > 0) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                            return true
                        }
                    } else {
                        if (webView.canGoForward()) {
                            webView.goForward()
                            return true
                        }
                    }
                }
                return false
            }
        })

        webView.setOnTouchListener { _, event ->
            if (gesture) {
                gestureDetector.onTouchEvent(event)
            }
            false
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        if (customView != null) {
            webView.pauseTimers()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isFullScreenMode && customView == null) {
            hideSystemUI()
        }
    }

    override fun onDestroy() {
        if (customView != null) {
            hideCustomView()
        }
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) {
            hideCustomView()
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        originalOrientation = requestedOrientation

        val decorView = window.decorView as ViewGroup
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)

        val fullscreenContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        fullscreenContainer.addView(view)
        rootView.addView(fullscreenContainer)
        hideSystemUI()
        webView.visibility = View.GONE
    }

    private fun hideCustomView() {
        if (customView == null) return

        showSystemUI()
        webView.visibility = View.VISIBLE

        val decorView = window.decorView as ViewGroup
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)
        val fullscreenContainer = customView?.parent as? ViewGroup
        fullscreenContainer?.let { rootView.removeView(it) }

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        requestedOrientation = originalOrientation

        if (!keepScreenOnFromConfig) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                try {
                    @Suppress("NewApi")
                    it.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } catch (e: Exception) {
                    Log.w(TAG, "BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE not available", e)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }
    }

    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    /**
     * 解析 app.json 配置
     */
    private fun parseJsonWithNative(jsonFilePath: String): Map<String, Any>? {
        return try {
            val jsonString = assets.open(jsonFilePath).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            mapOf(
                "name" to jsonObject.optString("name", "掌上商客"),
                "webUrl" to jsonObject.optString("webUrl", ""),
                "debug" to jsonObject.optBoolean("debug", false),
                "userAgent" to jsonObject.optString("userAgent", ""),
                "fullScreen" to jsonObject.optBoolean("fullScreen", true),
                "launch" to (jsonObject.optString("launch", "")),
                "screenOn" to jsonObject.optBoolean("screenOn", true),
                "gesture" to jsonObject.optBoolean("gesture", true),
                "clearCache" to jsonObject.optBoolean("clearCache", false),
                "setZoom" to jsonObject.optBoolean("setZoom", true),
                "callPhone" to jsonObject.optBoolean("callPhone", true),
                "location" to jsonObject.optBoolean("location", true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析 app.json 失败", e)
            null
        }
    }

    /**
     * JS 调用的接口
     */
    inner class JsInterface(private val context: Context) {

        @JavascriptInterface
        fun downloadBase64File(base64Data: String, mimeType: String?, fileName: String?) {
            (context as? MainActivity)?.runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    saveDecodedDownload(bytes, mimeType, fileName)
                } catch (e: Exception) {
                    Log.e(TAG, "保存失败", e)
                    showTopToast("保存失败: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "打开URL失败: $url", e)
            }
        }

        @JavascriptInterface
        fun isAndroid(): Boolean = true

        @JavascriptInterface
        fun isApp(): Boolean = true

        /**
         * 获取设备信息（供 Web 端使用）
         */
        @JavascriptInterface
        fun getDeviceInfo(): String {
            return JSONObject().apply {
                put("platform", "android")
                put("version", Build.VERSION.RELEASE)
                put("sdk", Build.VERSION.SDK_INT)
                put("brand", Build.BRAND)
                put("model", Build.MODEL)
                put("appVersion", AppConfig.version)
            }.toString()
        }

        /**
         * 检查定位权限状态
         */
        @JavascriptInterface
        fun checkLocationPermission(): Boolean {
            return locationManager.hasLocationPermission()
        }
    }

    /**
     * 保存下载文件到公共下载目录
     */
    private fun saveDecodedDownload(bytes: ByteArray, mimeType: String?, fileName: String?) {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val safeName = when {
            !fileName.isNullOrBlank() -> fileName
            !mimeType.isNullOrBlank() -> {
                val ext = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType) ?: "bin"
                "download_${System.currentTimeMillis()}.$ext"
            }
            else -> "download_${System.currentTimeMillis()}.bin"
        }

        val outFile = File(downloadsDir, safeName)
        FileOutputStream(outFile).use { it.write(bytes) }

        showTopToast(getString(R.string.save_success) + ": ${outFile.name}", Toast.LENGTH_LONG)
        Log.d(TAG, "文件已保存: ${outFile.absolutePath}")
    }

    /**
     * 处理特殊协议下载（data:, blob:）
     */
    private fun tryHandleSpecialSchemeDownload(
        url: String,
        contentDisposition: String?,
        mimetype: String?
    ): Boolean {
        when {
            url.startsWith("data:", ignoreCase = true) -> {
                if (!trySaveDataUrlToDownloads(url, contentDisposition, mimetype)) {
                    showTopToast("无法保存此链接", Toast.LENGTH_SHORT)
                }
                return true
            }
            url.startsWith("blob:", ignoreCase = true) -> {
                saveBlobUrlViaJavaScript(url, contentDisposition, mimetype)
                return true
            }
            else -> return false
        }
    }

    private fun trySaveDataUrlToDownloads(
        dataUrl: String,
        contentDisposition: String?,
        mimetype: String?
    ): Boolean {
        return try {
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return false
            val meta = dataUrl.substring(5, comma)
            val payload = dataUrl.substring(comma + 1)
            val isBase64 = meta.contains(";base64", ignoreCase = true)
            val mimeFromMeta = meta.substringBefore(';').trim().takeIf { it.isNotEmpty() }
            val effectiveMime = mimetype?.takeIf { it.isNotBlank() } ?: mimeFromMeta
            val bytes = if (isBase64) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                URLDecoder.decode(payload, StandardCharsets.UTF_8.name())
                    .toByteArray(StandardCharsets.UTF_8)
            }
            val name = URLUtil.guessFileName(dataUrl, contentDisposition, effectiveMime)
            saveDecodedDownload(bytes, effectiveMime, name)
            true
        } catch (e: Exception) {
            Log.e(TAG, "data URL 保存失败", e)
            false
        }
    }

    private fun saveBlobUrlViaJavaScript(
        blobUrl: String,
        contentDisposition: String?,
        mimetype: String?
    ) {
        val quotedUrl = JSONObject.quote(blobUrl)
        val guessed = URLUtil.guessFileName(blobUrl, contentDisposition, mimetype)
        val quotedName = JSONObject.quote(guessed)
        val script = """
            (function(){
              try {
                var u = $quotedUrl;
                var defaultName = $quotedName;
                fetch(u).then(function(r){ return r.blob(); }).then(function(blob){
                  var reader = new FileReader();
                  reader.onloadend = function() {
                    try {
                      var dataUrl = reader.result || '';
                      var i = dataUrl.indexOf(',');
                      var b64 = i >= 0 ? dataUrl.substring(i + 1) : dataUrl;
                      var mime = blob.type || 'application/octet-stream';
                      if (window.JsBridge && window.JsBridge.downloadBase64File) {
                        window.JsBridge.downloadBase64File(b64, mime, defaultName);
                      }
                    } catch (e) { console.error(e); }
                  };
                  reader.readAsDataURL(blob);
                }).catch(function(e){ console.error('blob fetch', e); });
              } catch (e2) { console.error(e2); }
            })();
        """.trimIndent()
        webView.post {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(script, null)
            }
        }
    }

    /**
     * 系统下载管理器下载
     */
    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?
    ) {
        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val lowerMime = mimetype?.lowercase() ?: ""
        val lowerName = fileName.lowercase()
        val isVideoMp4 = lowerMime.contains("video/mp4") ||
            (lowerMime.contains("application/octet-stream") && url.contains(".mp4", ignoreCase = true))

        if (isVideoMp4) {
            fileName = when {
                lowerName.endsWith(".mp4") -> fileName
                lowerName.endsWith(".bin") -> fileName.replace(
                    Regex("\\.bin$", RegexOption.IGNORE_CASE), ".mp4"
                )
                !fileName.contains('.') -> "$fileName.mp4"
                else -> fileName
            }
        }

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            if (isVideoMp4) {
                setMimeType("video/mp4")
            } else if (!mimetype.isNullOrEmpty()) {
                setMimeType(mimetype)
            }
            if (!userAgent.isNullOrEmpty()) {
                addRequestHeader("User-Agent", userAgent)
            }
            setDescription(getString(R.string.downloading))
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.enqueue(request)
            showTopToast(getString(R.string.download_started), Toast.LENGTH_SHORT)
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager.enqueue 失败: $url", e)
            showTopToast("${getString(R.string.download_failed)}: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    private fun showTopToast(message: String, duration: Int) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 120)
        toast.show()
    }

    private fun isDownloadableFileUrl(url: String): Boolean {
        val checkUrl = url.substringBefore("?").substringBefore("#").lowercase()
        val exts = listOf(
            "mp4", "mov", "mkv", "avi",
            "mp3", "aac", "wav", "flac",
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "txt", "pdf",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "rar", "7z"
        )
        return exts.any { checkUrl.endsWith(".$it") }
    }

    private fun hideSplashOverlay() {
        if (!showLaunchSplash) return
        val overlay = findViewById<View>(R.id.splash_overlay)
        if (overlay.visibility != View.VISIBLE) return
        overlay.animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
            }
            .start()
    }

    /**
     * WebView 客户端
     */
    inner class MyWebViewClient(val debug: Boolean) : WebViewClient() {

        private fun handleOverrideUrl(view: WebView?, rawUrl: String?): Boolean {
            if (rawUrl.isNullOrBlank()) return false
            val fixedUrl = rawUrl.toString()

            // tel: 协议
            if (fixedUrl.startsWith("tel:", ignoreCase = true)) {
                if (!allowCallPhoneFromConfig) {
                    showTopToast(getString(R.string.phone_call_disabled), Toast.LENGTH_SHORT)
                    return true
                }
                return try {
                    val intent = Intent(Intent.ACTION_DIAL, fixedUrl.toUri())
                    view?.context?.startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    showTopToast(getString(R.string.phone_call_not_found), Toast.LENGTH_SHORT)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "处理 tel URL 失败: $fixedUrl", e)
                    true
                }
            }

            // HTTP/HTTPS 文件下载
            if (fixedUrl.startsWith("http://") || fixedUrl.startsWith("https://")) {
                if (isDownloadableFileUrl(fixedUrl)) {
                    val ua = view?.settings?.userAgentString ?: ""
                    val ext = MimeTypeMap.getFileExtensionFromUrl(fixedUrl)
                    val mime = ext?.let {
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase())
                    } ?: "application/octet-stream"
                    this@MainActivity.startDownload(fixedUrl, ua, null, mime)
                    return true
                }
                return false
            }

            if (fixedUrl.startsWith("file://")) {
                return false
            }

            // Intent URI
            if (fixedUrl.startsWith("intent://")) {
                try {
                    val intent = Intent.parseUri(fixedUrl, Intent.URI_INTENT_SCHEME)
                    val pm = view?.context?.packageManager
                    if (pm != null && intent.resolveActivity(pm) != null) {
                        view.context.startActivity(intent)
                        return true
                    }
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrEmpty()) {
                        view?.loadUrl(fallbackUrl)
                        return true
                    }
                } catch (e: URISyntaxException) {
                    Log.e(TAG, "Intent URI 解析失败: $fixedUrl", e)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "找不到处理 Intent 的应用: $fixedUrl", e)
                }
            }

            return try {
                val intent = Intent(Intent.ACTION_VIEW, fixedUrl.toUri())
                val pm = view?.context?.packageManager
                if (pm != null && intent.resolveActivity(pm) != null) {
                    view.context.startActivity(intent)
                    true
                } else {
                    Log.w(TAG, "无应用处理: $fixedUrl")
                    !fixedUrl.startsWith("about:", ignoreCase = true) &&
                        !fixedUrl.startsWith("javascript:", ignoreCase = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "打开外部应用失败: $fixedUrl", e)
                true
            }
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return handleOverrideUrl(view, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString()
            return handleOverrideUrl(view, url)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.w(TAG, "WebView 加载错误: ${error?.description}")
            if (showLaunchSplash && request?.isForMainFrame == true) {
                mainFrameLoadError = true
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (showLaunchSplash && request?.isForMainFrame == true) {
                val code = errorResponse?.statusCode ?: 0
                if (code >= 400) mainFrameLoadError = true
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.post {
                if (!mainFrameLoadError) hideSplashOverlay()
            }

            // 注入 blob: / data: 下载拦截器
            val blobInterceptor = """
                (function () {
                  if (window.__blobDownloadInjected) return;
                  window.__blobDownloadInjected = true;
                  
                  document.addEventListener('click', function (e) {
                    try {
                      var target = e.target;
                      while (target && target.tagName && target.tagName.toLowerCase() !== 'a') {
                        target = target.parentElement;
                      }
                      if (!target) return;
                      
                      var href = target.getAttribute('href');
                      if (!href) return;
                      var isBlob = href.indexOf('blob:') === 0;
                      var isData = href.indexOf('data:') === 0;
                      if (!isBlob && !isData) return;
                      
                      e.preventDefault();
                      e.stopPropagation();
                      
                      var fileName = target.getAttribute('download') || 'download-' + Date.now();
                      
                      if (isData) {
                        try {
                          var comma = href.indexOf(',');
                          if (comma < 0) return;
                          var meta = href.substring(5, comma);
                          var payload = href.substring(comma + 1);
                          if (meta.indexOf(';base64') === -1) return;
                          var mime = (meta.split(';')[0] || 'application/octet-stream').trim();
                          if (window.JsBridge && window.JsBridge.downloadBase64File) {
                            window.JsBridge.downloadBase64File(payload, mime, fileName);
                          }
                        } catch (errD) {
                          console.error('data: download error', errD);
                        }
                        return;
                      }
                      
                      fetch(href)
                        .then(function (res) { return res.blob(); })
                        .then(function (blob) {
                          var reader = new FileReader();
                          reader.onloadend = function () {
                            try {
                              var dataUrl = reader.result || '';
                              var commaIndex = dataUrl.indexOf(',');
                              var base64 = commaIndex >= 0 ? dataUrl.substring(commaIndex + 1) : dataUrl;
                              var mime = blob.type || 'application/octet-stream';
                              if (window.JsBridge && window.JsBridge.downloadBase64File) {
                                window.JsBridge.downloadBase64File(base64, mime, fileName);
                              } else {
                                console.error('JsBridge not found on window');
                              }
                            } catch (err) {
                              console.error('Blob download convert error', err);
                            }
                          };
                          reader.readAsDataURL(blob);
                        })
                        .catch(function (err) {
                          console.error('Blob download fetch error', err);
                        });
                    } catch (e2) {
                      console.error('Blob download interceptor error', e2);
                    }
                  }, true);
                })();
            """.trimIndent()

            view?.evaluateJavascript(blobInterceptor, null)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (showLaunchSplash) mainFrameLoadError = false
            if (debug) {
                try {
                    val vConsole = assets.open("vConsole.js").bufferedReader().use { it.readText() }
                    val openDebug = "var vConsole = new window.VConsole()"
                    view?.evaluateJavascript(vConsole + openDebug, null)
                } catch (e: Exception) {
                    Log.w(TAG, "vConsole 加载失败", e)
                }
            }
            try {
                val injectJs = assets.open("custom.js").bufferedReader().use { it.readText() }
                view?.evaluateJavascript(injectJs, null)
            } catch (e: Exception) {
                Log.w(TAG, "custom.js 加载失败", e)
            }
        }
    }

    /**
     * WebChrome 客户端
     */
    inner class MyChromeClient(private val activity: MainActivity) : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            Log.d(TAG, "加载进度: $newProgress% - ${view?.url}")
        }

        /**
         * 处理 getUserMedia 权限请求（摄像头 / 麦克风）
         */
        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) return

            activity.runOnUiThread {
                val resources = request.resources
                val needPermissions = mutableListOf<String>()
                if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    needPermissions.add(Manifest.permission.CAMERA)
                }
                if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    needPermissions.add(Manifest.permission.RECORD_AUDIO)
                }

                if (needPermissions.isEmpty()) {
                    request.grant(resources)
                    return@runOnUiThread
                }

                val notGranted = needPermissions.filter {
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                }

                if (notGranted.isEmpty()) {
                    request.grant(resources)
                } else {
                    activity.pendingPermissionRequest?.deny()
                    activity.pendingPermissionRequest = request
                    activity.permissionLauncher.launch(notGranted.toTypedArray())
                }
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            super.onPermissionRequestCanceled(request)
            if (activity.pendingPermissionRequest == request) {
                activity.pendingPermissionRequest = null
            }
        }

        /**
         * 处理 HTML5 定位权限请求
         */
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            if (origin == null || callback == null) {
                super.onGeolocationPermissionsShowPrompt(origin, callback)
                return
            }

            if (!locationEnabledFromConfig) {
                callback.invoke(origin, false, false)
                showTopToast("LBS 功能未启用", Toast.LENGTH_SHORT)
                return
            }

            activity.runOnUiThread {
                // 使用 LocationPermissionManager 检查权限
                if (activity.locationManager.hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                    return@runOnUiThread
                }

                val need = activity.locationManager.getRequiredPermissions()
                if (need.isEmpty()) {
                    callback.invoke(origin, true, false)
                    return@runOnUiThread
                }

                // 处理之前的回调
                activity.pendingGeolocationCallback?.let { prevCb ->
                    activity.pendingGeolocationOrigin?.let { prevOrigin ->
                        prevCb.invoke(prevOrigin, false, false)
                    }
                }
                activity.pendingGeolocationOrigin = origin
                activity.pendingGeolocationCallback = callback
                activity.locationPermissionLauncher.launch(need)
            }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view != null && callback != null) {
                activity.showCustomView(view, callback)
            } else {
                super.onShowCustomView(view, callback)
            }
        }

        override fun onHideCustomView() {
            activity.hideCustomView()
            super.onHideCustomView()
        }

        /**
         * 处理文件选择
         */
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            if (activity.fileUploadCallback != null) {
                activity.fileUploadCallback?.onReceiveValue(null)
            }
            activity.fileUploadCallback = filePathCallback

            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    val acceptTypes = fileChooserParams?.acceptTypes
                    if (acceptTypes != null && acceptTypes.isNotEmpty()) {
                        if (acceptTypes.size == 1) {
                            type = acceptTypes[0]
                        } else {
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                        }
                    } else {
                        type = "*/*"
                    }
                    if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }

                val chooserIntent = Intent.createChooser(intent, "选择文件")
                activity.fileChooserLauncher.launch(chooserIntent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "无法打开文件选择器", e)
                activity.fileUploadCallback?.onReceiveValue(null)
                activity.fileUploadCallback = null
                return false
            }
        }
    }

    companion object {
        private const val TAG = "HandBizMain"
    }
}
