package com.patel.aivoicestudio

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#101820"))
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(StudioBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/index.html")
        }

        val splashLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val bannerResId = resources.getIdentifier("mithila_full_banner", "drawable", packageName)
            if (bannerResId != 0) {
                setBackgroundResource(bannerResId)
            } else {
                setBackgroundColor(Color.parseColor("#2B090C"))
            }
        }

        val titleView = TextView(this).apply {
            text = "🪷 मिथिला स्ट्रीम स्टूडियो 🪷\nMITHILA STREAM STUDIO\nBIRPUR (BIHAR)"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F4B41A"))
            gravity = Gravity.CENTER
        }

        val subTitleView = TextView(this).apply {
            text = "\n🎙️ Patel AI Voice & Music Studio 🎶\n15 Languages • Suno AI & Gemini Studio\n✨ Loading Studio..."
            textSize = 15f
            setTextColor(Color.parseColor("#E0F7FA"))
            gravity = Gravity.CENTER
        }

        splashLayout.addView(titleView)
        splashLayout.addView(subTitleView)

        rootLayout.addView(webView)
        rootLayout.addView(splashLayout)
        setContentView(rootLayout)

        tts = TextToSpeech(this, this)

        Handler(Looper.getMainLooper()).postDelayed({
            splashLayout.visibility = View.GONE
        }, 3200L)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts?.language = Locale("hi", "IN")
        }
    }

    inner class StudioBridge {
        @JavascriptInterface
        fun speakText(text: String, langCode: String, rate: Float, pitch: Float) {
            runOnUiThread {
                if (!isTtsReady || tts == null) {
                    Toast.makeText(this@MainActivity, "TTS इंजन तैयार हो रहा है...", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val parts = langCode.split("-")
                val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(langCode)
                tts?.language = locale
                tts?.setSpeechRate(rate.coerceIn(0.3f, 2.0f))
                tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "patel_tts_utt")
            }
        }

        @JavascriptInterface
        fun stopSpeak() {
            runOnUiThread {
                tts?.stop()
            }
        }

        @JavascriptInterface
        fun synthesizeAndSaveVoice(text: String, langCode: String, rate: Float, pitch: Float) {
            runOnUiThread {
                if (!isTtsReady || tts == null) return@runOnUiThread
                try {
                    val parts = langCode.split("-")
                    val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(langCode)
                    tts?.language = locale
                    tts?.setSpeechRate(rate.coerceIn(0.3f, 2.0f))
                    tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))

                    val outDir = File(cacheDir, "shared_audio").apply { mkdirs() }
                    val outFile = File(outDir, "Patel_Voice_${System.currentTimeMillis()}.wav")
                    val res = tts?.synthesizeToFile(text, null, outFile, "patel_wav_export")
                    if (res == TextToSpeech.SUCCESS) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (outFile.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "${packageName}.fileprovider",
                                    outFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/wav"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "आवाज़ (WAV) शेयर करें"))
                            }
                        }, 1200L)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareAppApk() {
            runOnUiThread {
                try {
                    val apkPath = applicationInfo.sourceDir
                    val sourceApk = File(apkPath)
                    val outDir = File(cacheDir, "shared_apk").apply { mkdirs() }
                    val destApk = File(outDir, "Patel_AI_Voice_Music_Studio.apk")
                    sourceApk.copyTo(destApk, overwrite = true)

                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        destApk
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "ऐप APK शेयर करें"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "APK Share Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
