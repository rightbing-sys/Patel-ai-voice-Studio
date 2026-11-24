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
import android.speech.tts.TextToSpeech
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
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#101820")
        window.navigationBarColor = Color.parseColor("#101820")

        tts = TextToSpeech(this, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

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
            addJavascriptInterface(StudioBridge(), "AndroidBridge")
            setDownloadListener { url, _, _, mimeType, _ ->
                if (url.startsWith("data:")) {
                    saveBase64Audio(url, mimeType ?: "audio/mpeg")
                }
            }
            loadUrl("file:///android_asset/index.html")
        }
        rootLayout.addView(webView)

        val splashLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#101820"))
        }

        val bannerResId = resources.getIdentifier("mithila_full_banner", "drawable", packageName)
        if (bannerResId != 0) {
            val bannerImg = ImageView(this).apply {
                setImageResource(bannerResId)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            splashLayout.addView(bannerImg)
        } else {
            val fallbackBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
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
            fallbackBox.addView(titleText)
            fallbackBox.addView(subText)
            splashLayout.addView(fallbackBox)
        }

        rootLayout.addView(splashLayout)
        setContentView(rootLayout)

        Handler(Looper.getMainLooper()).postDelayed({
            splashLayout.visibility = View.GONE
            window.statusBarColor = Color.parseColor("#080D1E")
            window.navigationBarColor = Color.parseColor("#080D1E")
        }, 2500)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("hi", "IN"))
            ttsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED)
            if (!ttsReady) {
                tts?.setLanguage(Locale.US)
                ttsReady = true
            }
        }
    }

    inner class StudioBridge {
        @JavascriptInterface
        fun speakText(text: String, langTag: String, rate: Float, pitch: Float) {
            runOnUiThread {
                if (ttsReady && tts != null) {
                    val locale = Locale.forLanguageTag(langTag)
                    tts?.language = locale
                    tts?.setSpeechRate(rate)
                    tts?.setPitch(pitch)
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "patel_tts")
                } else {
                    Toast.makeText(this@MainActivity, "TTS Engine initializing...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun stopSpeak() {
            runOnUiThread {
                tts?.stop()
            }
        }

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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
