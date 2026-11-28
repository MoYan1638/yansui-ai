package com.mobileagent.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 应用内浏览器：API Key 开通教程等官网链接在这里打开，不跳系统浏览器。
 *
 * 为什么不用 Custom Tabs：国内机型上 Chrome Custom Tabs 经常没有支持的浏览器，
 * 会 fallback 回外部浏览器，用户还是被踢出应用。WebView 才是真正"内置"。
 */
class BrowserActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        // ---- 顶栏：返回 / 标题 / 系统浏览器打开（兜底）----
        val dp = { px: Int -> (px * resources.displayMetrics.density).toInt() }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(resources.getColor(android.R.color.background_light, null))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            contentDescription = "返回"
            background = null
            setOnClickListener { navigateBack() }
        }
        titleView = TextView(this).apply {
            text = "正在打开…"
            textSize = 14f
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(8), 0, dp(8), 0)
        }
        val openExternal = TextView(this).apply {
            text = "浏览器"
            textSize = 12f
            setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
            setPadding(dp(8), dp(6), dp(4), dp(6))
            setOnClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        bar.addView(titleView)
        bar.addView(openExternal)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true              // 各平台控制台依赖 JS
                domStorageEnabled = true              // 登录态/localStorage
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = false               // 安全：禁止本地文件
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url.toString()
                    return when {
                        u.startsWith("http://") || u.startsWith("https://") -> false // 留在应用内加载
                        u.startsWith("tel:") || u.startsWith("mailto:") -> {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                            true
                        }
                        else -> true // 拦掉其他未知协议，避免跳出
                    }
                }
                override fun onPageFinished(view: WebView, pageUrl: String) {
                    titleView.text = view.title?.takeIf { it.isNotBlank() } ?: "网页"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }
        }

        // 竖排：顶栏 + 进度条 + 网页
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(bar)
        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        // Android 15 强制 edge-to-edge：给顶栏加状态栏高度，避免内容顶到状态栏里
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            bar.updatePadding(top = top + dp(6))
            insets
        }

        // 返回键：网页能回退就回退，否则关页面（移动端标准行为）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })

        webView.loadUrl(url)
    }

    private fun navigateBack() {
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"

        fun start(context: Context, url: String) {
            val i = Intent(context, BrowserActivity::class.java).putExtra(EXTRA_URL, url)
            if (context !is ComponentActivity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}
