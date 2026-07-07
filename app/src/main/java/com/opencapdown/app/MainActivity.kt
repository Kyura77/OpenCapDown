package com.opencapdown.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import okhttp3.OkHttpClient
import java.io.File

class MainActivity : ComponentActivity() {

    private var bridge: OpenCapDownBridge? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as OpenCapDownApp
        val core = app.core
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.startsWith("file://")) {
                        view.visibility = View.VISIBLE
                    }
                }
            }
            webChromeClient = WebChromeClient()

            val bridgeImpl = OpenCapDownBridge(
                core = core,
                client = client,
                cacheDir = File(cacheDir, "webview")
            )
            bridge = bridgeImpl
            addJavascriptInterface(bridgeImpl, "OpenCapDown")

            visibility = View.INVISIBLE
        }

        setContentView(webView)
    }

    override fun onDestroy() {
        bridge?.destroy()
        bridge = null
        super.onDestroy()
    }
}
