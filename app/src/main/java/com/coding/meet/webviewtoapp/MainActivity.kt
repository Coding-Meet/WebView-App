package com.coding.meet.webviewtoapp

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.coding.meet.webviewtoapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private var webUrl = "https://github.com/Coding-Meet"


    private var isLoaded = false
    private var doubleBackToExitPressedOnce = false


    private val networkConnectivityObserver: NetworkConnectivityObserver by lazy {
        NetworkConnectivityObserver(this)
    }

    private val loadingDialog: Dialog by lazy {
        Dialog(this)
    }

    private val mainBinding: ActivityMainBinding by lazy {
        DataBindingUtil.setContentView(this, R.layout.activity_main)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        loadingDialog.setContentView(R.layout.loading_layout)
        loadingDialog.window!!.setLayout(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        val setting = mainBinding.webView.settings
        setting.javaScriptEnabled = true
        setting.allowFileAccess = true
        setting.domStorageEnabled = true
        setting.javaScriptCanOpenWindowsAutomatically = true
        setting.supportMultipleWindows()


        val snackbar = Snackbar.make(
            mainBinding.root,
            "No Internet Connection",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Wifi") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        networkConnectivityObserver.observe(this) {
            when (it) {
                Status.Available -> {
                    if (snackbar.isShown) {
                        snackbar.dismiss()
                    }
                    mainBinding.swipeRefresh.isEnabled = true
                    if (!isLoaded) loadWebView()

                }
                else -> {
                    showNoInternet()
                    snackbar.show()
                    mainBinding.swipeRefresh.isRefreshing = false
                }
            }
        }

        mainBinding.swipeRefresh.setOnRefreshListener {
            if (!isLoaded) {
                loadWebView()
            } else {
                setProgressDialogVisibility(false)
            }
        }

    }

    private fun setProgressDialogVisibility(visible: Boolean) {
        if (visible){
            loadingDialog.show()
        }else{
            loadingDialog.dismiss()
            mainBinding.swipeRefresh.isRefreshing = false
        }
    }

    private fun showNoInternet() {
        isLoaded = false
        setProgressDialogVisibility(false)
        gone(mainBinding.webView)
        visible(mainBinding.noInternet.noInternetRL)
    }

    private fun loadWebView() {
        gone(mainBinding.noInternet.noInternetRL)
        visible(mainBinding.webView)
        mainBinding.webView.loadUrl(webUrl)
        mainBinding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                setProgressDialogVisibility(true)
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url.toString()
                view?.loadUrl(url)
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoaded = true
                webUrl = url!!
                setProgressDialogVisibility(false)
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                isLoaded = false
                setProgressDialogVisibility(false)
                super.onReceivedError(view, request, error)
            }

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
                Toast.makeText(this,"Please Click Back Again to Exit",Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                    doubleBackToExitPressedOnce = false
                    }
                ,2000
                )
            }
        }
    }
}