package com.patel.aivoicestudio

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#0B1026")
        window.navigationBarColor = Color.parseColor("#0B1026")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        val rootLayout = FrameLayout(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }
            addJavascriptInterface(AudioDownloadBridge(), "AndroidBridge")
            setDownloadListener { url, _, _, mimeType, _ ->
                if (url.startsWith("data:")) {
                    saveBase64Audio(url, mimeType ?: "audio/mpeg")
                }
            }
            loadUrl("file:///android_asset/index.html")
        }
        rootLayout.addView(webView)

        val splashLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101820"))
            setPadding(48, 48, 48, 48)
        }

        val bannerResId = resources.getIdentifier("mithila_full_banner", "drawable", packageName)
        if (bannerResId != 0) {
            val bannerImg = ImageView(this).apply {
                setImageResource(bannerResId)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            splashLayout.addView(bannerImg)
        } else {
            val titleText = TextView(this).apply {
                text = "MITHILA STREAM STUDIO\nBIRPUR"
                textSize = 26f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#D7B56D"))
                gravity = Gravity.CENTER
            }
            val subText = TextView(this).apply {
                text = "\nPatel AI Voice & Music Studio\nLoading Studio..."
                textSize = 16f
                setTextColor(Color.parseColor("#E0F7FA"))
                gravity = Gravity.CENTER
            }
            splashLayout.addView(titleText)
            splashLayout.addView(subText)
        }

        rootLayout.addView(splashLayout)
        setContentView(rootLayout)

        Handler(Looper.getMainLooper()).postDelayed({
            splashLayout.visibility = View.GONE
        }, 2500)
    }

    inner class AudioDownloadBridge {
        @JavascriptInterface
        fun saveAudio(base64Data: String, fileName: String) {
            saveBase64Audio(base64Data, "audio/mpeg", fileName)
        }
    }

    private fun saveBase64Audio(dataUrl: String, mimeType: String, customName: String? = null) {
        try {
            val base64Part = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val fileName = customName ?: "Patel_Voice_${System.currentTimeMillis()}.mp3"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                FileOutputStream(File(dir, fileName)).use { out -> out.write(bytes) }
            }
            runOnUiThread {
                Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
