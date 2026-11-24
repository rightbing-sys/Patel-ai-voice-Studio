package com.patel.aivoicestudio

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastSavedUri: Uri? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#101820")
        window.navigationBarColor = Color.parseColor("#101820")

        tts = TextToSpeech(this, this, "com.google.android.tts")

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
                    saveBase64Audio(url, mimeType ?: "audio/wav")
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
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val res = tts?.setLanguage(Locale("hi", "IN"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            ttsReady = true
        } else {
            tts = TextToSpeech(this) { s ->
                if (s == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale("hi", "IN"))
                    ttsReady = true
                }
            }
        }
    }

    inner class StudioBridge {
        @JavascriptInterface
        fun speakText(text: String, langTag: String, rate: Float, pitch: Float) {
            runOnUiThread {
                if (ttsReady && tts != null) {
                    val locale = Locale.forLanguageTag(langTag)
                    val res = tts?.setLanguage(locale)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale("hi", "IN"))
                    }
                    tts?.setSpeechRate(rate)
                    tts?.setPitch(pitch)
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "patel_tts_${System.currentTimeMillis()}")
                } else {
                    Toast.makeText(this@MainActivity, "Voice engine loading, 2 second baad phir dabayein", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun synthesizeAndSaveVoice(text: String, langTag: String, rate: Float, pitch: Float) {
            runOnUiThread {
                if (!ttsReady || tts == null) {
                    Toast.makeText(this@MainActivity, "Voice engine ready nahi hai", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val locale = Locale.forLanguageTag(langTag)
                val res = tts?.setLanguage(locale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("hi", "IN"))
                }
                tts?.setSpeechRate(rate)
                tts?.setPitch(pitch)

                val tempFile = File(cacheDir, "Patel_Voice_${System.currentTimeMillis()}.wav")
                val uttId = "save_utt_${System.currentTimeMillis()}"
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == uttId && tempFile.exists()) {
                            copyTempFileToDownloads(tempFile, tempFile.name, "audio/wav")
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Voice file banane mein error aaya", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                tts?.synthesizeToFile(text, null, tempFile, uttId)
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
            saveBase64Audio(base64Data, "audio/wav", fileName)
        }

        @JavascriptInterface
        fun shareText(text: String, targetPackage: String) {
            runOnUiThread {
                try {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        if (targetPackage.isNotEmpty()) {
                            setPackage(targetPackage)
                        }
                    }
                    startActivity(Intent.createChooser(sendIntent, "Share via"))
                } catch (e: Exception) {
                    val fallback = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(fallback, "Share via"))
                }
            }
        }

        @JavascriptInterface
        fun shareAudioAndText(base64Data: String, fileName: String, captionText: String) {
            saveBase64Audio(base64Data, "audio/wav", fileName)
            runOnUiThread {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_TEXT, captionText)
                        lastSavedUri?.let {
                            putExtra(Intent.EXTRA_STREAM, it)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Audio Song"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareAppApk() {
            runOnUiThread {
                try {
                    val sourceApk = File(applicationInfo.sourceDir)
                    val destApk = File(cacheDir, "Patel_AI_Voice_Studio.apk")
                    sourceApk.copyTo(destApk, overwrite = true)

                    val apkUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        destApk
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, apkUri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "🎙️ Patel AI Voice & Music Studio (Mithila Stream Studio, Birpur) ऐप इंस्टॉल करें और 15 भाषाओं में वॉयस व गाने बनाएं!"
                        )
                        addFlags(Intent.FLAG_GRANT_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Patel AI Voice Studio App"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "App share error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun copyTempFileToDownloads(sourceFile: File, fileName: String, mimeType: String) {
        try {
            val bytes = sourceFile.readBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                lastSavedUri = uri
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dest = File(dir, fileName)
                FileOutputStream(dest).use { out -> out.write(bytes) }
                lastSavedUri = Uri.fromFile(dest)
            }
            runOnUiThread {
                Toast.makeText(this, "✅ Voice Audio Saved: $fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBase64Audio(dataUrl: String, mimeType: String, customName: String? = null) {
        try {
            val base64Part = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val fileName = customName ?: "Patel_Song_${System.currentTimeMillis()}.wav"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                lastSavedUri = uri
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, fileName)
                FileOutputStream(file).use { out -> out.write(bytes) }
                lastSavedUri = Uri.fromFile(file)
            }
            runOnUiThread {
                Toast.makeText(this, "✅ Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
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
