package com.coding.meet.webviewtoapp

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.coding.meet.webviewtoapp.databinding.ActivityMainBinding
class MainActivity : AppCompatActivity() {

    private var webUrl = "https://www.google.com/"
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
    } else {
        arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE

        )
    }

    private var doubleBackToExitPressedOnce = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.any { !it.value }) {
            Toast.makeText(this, "Permissions are required!", Toast.LENGTH_LONG).show()
        }
    }
    private val mainBinding: ActivityMainBinding by lazy {
        DataBindingUtil.setContentView(this, R.layout.activity_main)
    }
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val REQUEST_SELECT_FILE = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions.launch(
            multiplePermissionNameList
        )

        setupWebView()

    }

    @JavascriptInterface
    fun showMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT)
                .show()
        }
    }

    @JavascriptInterface
    fun clearAppCache() {
        runOnUiThread {
            try {
                mainBinding.webView.clearCache(true)
                mainBinding.webView.clearHistory()
                mainBinding.webView.clearFormData()
                Toast.makeText(
                    applicationContext,
                    "WebView  cache cleared successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Failed to clear cache", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupWebView() {
        val webSettings = mainBinding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webSettings.setSupportZoom(true)
        mainBinding.webView.addJavascriptInterface(this, "AndroidBridge")
        webSettings.mediaPlaybackRequiresUserGesture = false

        mainBinding.webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = null

                uploadMessage = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE)
                } catch (e: ActivityNotFoundException) {
                    uploadMessage = null
                    Toast.makeText(
                        applicationContext,
                        "Cannot open file chooser",
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                return true
            }
        }

        mainBinding.webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http")) {
                    return false
                } else {
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        startActivity(this)
                    }
                    return true
                }
            }
            private var webViewPreviousState = 0
            private val PAGE_STARTED = 1
            private  val PAGE_REDIRECTED = 2

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webViewPreviousState = if(url.contentEquals(url)) {
                    PAGE_STARTED;
                } else {
                    PAGE_REDIRECTED;
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (webViewPreviousState == PAGE_STARTED) {
                    if (mainBinding.webView.progress == 100) {

                        Toast.makeText(this@MainActivity, "Page loaded!!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (webViewPreviousState == PAGE_STARTED) {

                    if (mainBinding.webView.progress == 100) {
                        webViewPreviousState = 0
                        Toast.makeText(this@MainActivity, "NO INTERNET CONNECTION", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        mainBinding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimeType)
            )
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, "Downloading File", Toast.LENGTH_LONG).show()
        }

        mainBinding.webView.loadUrl(webUrl)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null)
                return
            val results: Array<Uri>? =
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            uploadMessage?.onReceiveValue(results)
            uploadMessage = null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mainBinding.webView.canGoBack()) {
                    mainBinding.webView.goBack()
                } else {
                    showToastExit()
                }
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun showToastExit() {
        when {
            doubleBackToExitPressedOnce -> {
                finish()
            }

            else -> {
                doubleBackToExitPressedOnce = true
                Toast.makeText(this, "Please Click Back Again to Exit", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        doubleBackToExitPressedOnce = false
                    }, 2000
                )
            }
        }
    }
}