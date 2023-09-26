package com.coding.meet.webviewtoapp

import android.app.Dialog
import android.app.DownloadManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.coding.meet.webviewtoapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity() {
    private var webUrl = "https://github.com/Coding-Meet"
//    private var webUrl = "https://sample-videos.com/download-sample-jpg-image.php"
    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf()
    } else {
        arrayListOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

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
        if (visible) {
            loadingDialog.show()
        } else {
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
        mainBinding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->

            Log.d("Url", url.trim())
            Log.d("userAgent", userAgent)
            Log.d("contentDisposition", contentDisposition)
            Log.d("mimeType", mimeType)
            Log.d("contentLength", contentLength.toString())
            if (checkMultiplePermission()) {
                download(url.trim(),userAgent,contentDisposition,mimeType,contentLength)
            }

        }
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
                Toast.makeText(this, "Please Click Back Again to Exit", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        doubleBackToExitPressedOnce = false
                    }, 2000
                )
            }
        }
    }

    private fun download(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        val folder = File(
            Environment.getExternalStorageDirectory().toString() + "/Download/Image"
        )
        if (!folder.exists()) {
            folder.mkdirs()
        }
        Toast.makeText(this, "Download Started", Toast.LENGTH_SHORT).show()


        val request = DownloadManager.Request(Uri.parse(url))
        request.setMimeType(mimeType)
        val cookie = CookieManager.getInstance().getCookie(url)
        request.addRequestHeader("cookie",cookie)
        request.addRequestHeader("User-Agent",userAgent)
        request.setAllowedNetworkTypes(
            DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
        )
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        request.setTitle(fileName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "Image/$fileName"
        )
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

    }


    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()
        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                listPermissionNeeded.add(permission)
            }
        }
        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionNeeded.toTypedArray(),
                multiplePermissionId
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == multiplePermissionId) {
            if (grantResults.isNotEmpty()) {
                var isGrant = true
                for (element in grantResults) {
                    if (element == PackageManager.PERMISSION_DENIED) {
                        isGrant = false
                    }
                }
                if (isGrant) {
                    // here all permission granted successfully
                    Toast.makeText(this,"all permission granted successfully",Toast.LENGTH_LONG).show()
                } else {
                    var someDenied = false
                    for (permission in permissions) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                permission
                            )
                        ) {
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    permission
                                ) == PackageManager.PERMISSION_DENIED
                            ) {
                                someDenied = true
                            }
                        }
                    }
                    if (someDenied) {
                        // here app Setting open because all permission is not granted
                        // and permanent denied
                        appSettingOpen(this)
                    } else {
                        // here warning permission show
                        warningPermissionDialog(this) { _: DialogInterface, which: Int ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE ->
                                    checkMultiplePermission()
                            }
                        }
                    }
                }
            }
        }
    }
}