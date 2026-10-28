package dev.margbug.myspotify

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var playbackService: MediaPlaybackService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlaybackService.LocalBinder
            playbackService = binder.getService().apply {
                onControlAction = { action ->
                    runOnUiThread {
                        when (action) {
                            MediaPlaybackService.ACTION_PLAY_PAUSE,
                            MediaPlaybackService.ACTION_PLAY,
                            MediaPlaybackService.ACTION_PAUSE -> {
                                webView.evaluateJavascript("window.__spotifyTogglePlay && window.__spotifyTogglePlay();", null)
                            }
                            MediaPlaybackService.ACTION_NEXT -> {
                                webView.evaluateJavascript("window.__spotifyNext && window.__spotifyNext();", null)
                            }
                            MediaPlaybackService.ACTION_PREV -> {
                                webView.evaluateJavascript("window.__spotifyPrev && window.__spotifyPrev();", null)
                            }
                        }
                    }
                }
            }
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求 Android 13+ 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        startAndBindService()
        initWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // 后台隐藏而不是退出，保持音频播放
                    moveTaskToBack(true)
                }
            }
        })
    }

    private fun startAndBindService() {
        val intent = Intent(this, MediaPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF121212.toInt())
        }
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        // 关键改动：禁用桌面级宽视口，让 WebView 采用移动端真实物理屏幕宽度（360-412px）
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.setSupportZoom = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.textZoom = 100

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 核心：伪装成 Windows 10 Chrome 桌面版 UA，触发 Spotify Web 端整首播放特权
        settings.userAgentString = DESKTOP_USER_AGENT

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 注册 JS 原生交互桥
        webView.addJavascriptInterface(
            WebAppInterface { isPlaying, title, artist, coverUrl ->
                playbackService?.updatePlayback(isPlaying, title, artist, coverUrl)
            },
            "AndroidBridge"
        )

        // 核心：处理 DRM Protected Media 权限请求，确保 Widevine L3 解密模块正常启动
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                for (resource in request.resources) {
                    if (resource == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) {
                        request.grant(request.resources)
                        return
                    }
                }
                super.onPermissionRequest(request)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                injectResponsiveStyles()
                injectControlScript()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectResponsiveStyles()
                injectControlScript()
            }
        }

        webView.loadUrl(SPOTIFY_WEB_URL)
    }

    private fun injectResponsiveStyles() {
        try {
            val css = assets.open("inject.css").bufferedReader().use { it.readText() }
            val js = """
                (function() {
                    var style = document.getElementById('myspotify-injected-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'myspotify-injected-style';
                        document.head.appendChild(style);
                    }
                    style.textContent = `${css.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")}`;
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun injectControlScript() {
        try {
            val js = assets.open("inject.js").bufferedReader().use { it.readText() }
            webView.evaluateJavascript(js, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0"
        const val SPOTIFY_WEB_URL = "https://open.spotify.com"
    }
}
