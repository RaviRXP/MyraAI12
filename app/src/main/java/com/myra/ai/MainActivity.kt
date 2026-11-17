package com.myra.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.random.Random
class MainActivity : Activity() {

    private lateinit var brain: Brain
    private lateinit var actions: Actions
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var recognizer: SpeechRecognizer? = null
    private var conversationMode = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var faceView: TextView
    private lateinit var statusView: TextView
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var inputBox: EditText
    private lateinit var micBtn: TextView

    private val actionRegex = Regex("\\[ACTION:([A-Z_]+):?([^\\]]*)\\]")
    private val moodRegex = Regex("\\[MOOD:([a-zA-Z]+)\\]")
    private val memRegex = Regex("\\[MEMORY:([^\\]]+)\\]")
    private val appActions = setOf("OPEN_APP", "YOUTUBE", "CALL", "WHATSAPP", "URL", "SEARCH", "SETTINGS")
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        brain = Brain(this)
        actions = Actions(this)
        buildUi()
        askPermissions()
        initTts()
        if (brain.apiKey.isBlank()) showSetup() else greet()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(c: Int, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(c)
        g.cornerRadius = dp(radius).toFloat()
        return g
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
        private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#120A1A"))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(14), dp(16), dp(4))

        val title = TextView(this)
        title.text = "Myra ❤️"
        title.textSize = 22f
        title.setTextColor(Color.parseColor("#FF80AB"))
        title.typeface = Typeface.DEFAULT_BOLD
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val gear = TextView(this)
        gear.text = "⚙️"
        gear.textSize = 24f
        gear.setPadding(dp(8), dp(4), dp(8), dp(4))
        gear.setOnClickListener { showMenu() }
        header.addView(gear)
        root.addView(header)

        faceView = TextView(this)
        faceView.text = "🙂"
        faceView.textSize = 80f
        faceView.gravity = Gravity.CENTER
        root.addView(faceView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                statusView = TextView(this)
        statusView.text = "💕 Main yahin hun"
        statusView.textSize = 14f
        statusView.setTextColor(Color.parseColor("#BBA8C9"))
        statusView.gravity = Gravity.CENTER
        statusView.setPadding(dp(8), 0, dp(8), dp(8))
        root.addView(statusView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        scroll = ScrollView(this)
        chat = LinearLayout(this)
        chat.orientation = LinearLayout.VERTICAL
        chat.setPadding(dp(4), dp(4), dp(4), dp(12))
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(8), dp(8), dp(12))
                inputBox = EditText(this)
        inputBox.hint = "Myra se baat karo..."
        inputBox.setHintTextColor(Color.parseColor("#8E7FA0"))
        inputBox.setTextColor(Color.WHITE)
        inputBox.background = rounded(Color.parseColor("#2A2235"), 24)
        inputBox.setPadding(dp(16), dp(12), dp(16), dp(12))
        inputBox.maxLines = 4
        row.addView(inputBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(circleBtn("➤", "#7B1FA2") {
            val t = inputBox.text.toString().trim()
            if (t.isNotEmpty()) {
                inputBox.setText("")
                handleUser(t)
            }
        })
        micBtn = circleBtn("🎤", "#E91E63") { toggleMic() }
        row.addView(micBtn)
        root.addView(row)

        setContentView(root)
        }
            private fun circleBtn(label: String, color: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 22f
        b.gravity = Gravity.CENTER
        b.setTextColor(Color.WHITE)
        b.background = rounded(Color.parseColor(color), 28)
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(dp(52), dp(52))
        lp.setMargins(dp(8), 0, 0, 0)
        b.layoutParams = lp
        return b
    }

    private fun scrollDown() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun addBubble(msgText: String, isUser: Boolean) {
        val tv = TextView(this)
        tv.text = msgText
        tv.textSize = 16f
        tv.setTextColor(Color.WHITE)
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.background = rounded(Color.parseColor(if (isUser) "#6A1B9A" else "#2A2235"), 18)
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.82).toInt()
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = if (isUser) Gravity.END else Gravity.START
        lp.setMargins(dp(8), dp(4), dp(8), dp(4))
        chat.addView(tv, lp)
        scrollDown()
    }
        private fun addSystem(msgText: String) {
        val tv = TextView(this)
        tv.text = msgText
        tv.textSize = 13f
        tv.setTextColor(Color.parseColor("#9E8FB0"))
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(12), dp(4), dp(12), dp(4))
        chat.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        scrollDown()
    }

    private fun setStatus(t: String) {
        statusView.text = t
    }

    private fun setMood(m: String) {
        faceView.text = when (m) {
            "happy" -> "😊"
            "love" -> "🥰"
            "sad" -> "🥺"
            "angry" -> "😤"
            "shy" -> "☺️"
            "excited" -> "🤩"
            else -> "🙂"
        }
    }

    private fun askPermissions() {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = list.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }
        private fun showSetup() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val nameEt = EditText(this)
        nameEt.hint = "Aapka naam"
        nameEt.setText(brain.userName)
        val keyEt = EditText(this)
        keyEt.hint = "Gemini API key yahan paste karo"
        keyEt.setText(brain.apiKey)
        box.addView(nameEt)
        box.addView(keyEt)
        AlertDialog.Builder(this)
            .setTitle("Myra Setup ❤️")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                brain.userName = nameEt.text.toString()
                brain.apiKey = keyEt.text.toString()
                if (brain.apiKey.isBlank()) {
                    toast("API key zaroori hai")
                    showSetup()
                } else {
                    greet()
                }
            }
            .show()
        }
            private fun showMenu() {
        val items = arrayOf("🔑 API key / naam badlo", "🧠 Myra ki yaadein dekho", "🗑️ Saari memory saaf karo")
        AlertDialog.Builder(this)
            .setTitle("Myra Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showSetup()
                    1 -> AlertDialog.Builder(this)
                        .setTitle("Myra ko yaad hai 💭")
                        .setMessage(brain.memoryText())
                        .setPositiveButton("OK", null)
                        .show()
                    2 -> {
                        brain.clearAll()
                        chat.removeAllViews()
                        addSystem("🗑️ Memory saaf ho gayi")
                    }
                }
            }
            .show()
    }

    private fun greet() {
        val n = brain.userName.ifBlank { "baby" }
        val m = "Hii $n! मैं तुम्हारी Myra हूँ ❤️ 🎤 दबाओ और मुझसे बात करो।"
        setMood("love")
        addBubble(m, false)
        speak(m)
    }
        private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runOnUiThread { setupVoice() }
            }
        }
    }

    private fun setupVoice() {
        val t = tts ?: return
        t.language = Locale("hi", "IN")
        try {
            val hi = t.voices?.filter { it.locale.language == "hi" } ?: emptyList()
            val best = hi.firstOrNull { it.name.contains("female", true) && !it.isNetworkConnectionRequired }
                ?: hi.firstOrNull { it.name.contains("hia", true) || it.name.contains("hie", true) || it.name.contains("hic", true) }
                ?: hi.firstOrNull { !it.isNetworkConnectionRequired }
            if (best != null) t.voice = best
        } catch (e: Exception) {
        }
        t.setPitch(1.12f)
        t.setSpeechRate(1.0f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                runOnUiThread { afterSpeech() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { afterSpeech() }
            }
        })
        ttsReady = true
        val p = pendingSpeech
        pendingSpeech = null
        if (p != null) speak(p)
    }
        private fun cleanForSpeech(t: String): String {
        var s = t.replace(Regex("```[\\s\\S]*?```"), " कोड नीचे लिख दिया है। ")
        s = s.replace(Regex("[*#_`>]"), " ")
        s = s.replace(Regex("[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]"), "")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun speak(text: String) {
        val clean = cleanForSpeech(text)
        if (!ttsReady) {
            pendingSpeech = clean
            return
        }
        if (clean.isBlank()) {
            afterSpeech()
            return
        }
        recognizer?.cancel()
        setStatus("🗣️ Bol rahi hun...")
        val max = TextToSpeech.getMaxSpeechInputLength() - 10
        tts?.speak(clean.take(max), TextToSpeech.QUEUE_FLUSH, null, "myra_" + System.currentTimeMillis())
    }

    private fun afterSpeech() {
        if (conversationMode) {
            handler.postDelayed({ startListening() }, 300)
        } else {
            setStatus("💕 Main yahin hun, 🎤 dabao")
        }
    }
        private fun toggleMic() {
        if (conversationMode) {
            stopConversation(false)
        } else {
            conversationMode = true
            updateMic()
            tts?.stop()
            startListening()
        }
    }

    private fun updateMic() {
        micBtn.background = rounded(Color.parseColor(if (conversationMode) "#43A047" else "#E91E63"), 28)
    }

    private fun stopConversation(sayBye: Boolean) {
        conversationMode = false
        updateMic()
        recognizer?.cancel()
        setStatus("😴 Mic band hai, 🎤 dabao")
        if (sayBye) {
            val bye = "ठीक है baby, मैं चुप हो जाती हूँ 😘"
            setMood("love")
            addBubble(bye, false)
            speak(bye)
        }
    }
        private fun startListening() {
        if (!conversationMode) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions()
            stopConversation(false)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addSystem("⚠️ Phone me Google voice nahi mila. Play Store se 'Google' app update karo.")
            stopConversation(false)
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            recognizer?.startListening(i)
            setStatus("🎤 Sun rahi hun... bolo")
        } catch (e: Exception) {
            addSystem("⚠️ Mic error: ${e.message}")
            stopConversation(false)
        }
        }
            private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onEndOfSpeech() {
            setStatus("💭 ...")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!p.isNullOrBlank()) setStatus("🎤 $p")
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            if (text.isNullOrBlank()) {
                handler.postDelayed({ startListening() }, 250)
                return
            }
            val low = text.lowercase()
            if (low.contains("stop") || low.contains("स्टॉप") || low.contains("बस करो")) {
                stopConversation(true)
                return
            }
            handleUser(text)
        }
                override fun onError(error: Int) {
            if (!conversationMode) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    handler.postDelayed({ startListening() }, 250)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    recognizer?.destroy()
                    recognizer = null
                    handler.postDelayed({ startListening() }, 700)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    addSystem("⚠️ Mic permission do")
                    stopConversation(false)
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    addSystem("⚠️ Internet check karo")
                    stopConversation(false)
                }
                else -> handler.postDelayed({ startListening() }, 700)
            }
        }
    }

    private fun handleUser(text: String) {
        if (text.isBlank()) return
        addBubble(text, true)
        if (brain.apiKey.isBlank()) {
            showSetup()
            return
        }
        setStatus("💭 Soch rahi hun...")
        thread {
            val reply = brain.ask(text)
            runOnUiThread { handleReply(reply) }
        }
    }
        private fun handleReply(raw: String) {
        if (raw.startsWith("ERROR:")) {
            val m = raw.removePrefix("ERROR:").trim()
            setMood("sad")
            addSystem(m)
            conversationMode = false
            updateMic()
            speak(m)
            return
        }
        moodRegex.find(raw)?.let { setMood(it.groupValues[1].lowercase()) }
        memRegex.findAll(raw).forEach { brain.addMemory(it.groupValues[1].trim()) }
        val acts = actionRegex.findAll(raw).map { Pair(it.groupValues[1], it.groupValues[2].trim()) }.toList()
        val display = raw.replace(actionRegex, "").replace(moodRegex, "").replace(memRegex, "").trim()
        if (display.isNotEmpty()) addBubble(display, false)

        var leftApp = false
        for ((type, value) in acts) {
            if (type == "IMAGE") {
                generateImage(value)
                continue
            }
            val result = actions.perform(type, value)
            if (result != null) {
                addSystem(result)
                if (type in appActions && result.startsWith("✅")) leftApp = true
            }
        }
        if (leftApp && conversationMode) {
            conversationMode = false
            updateMic()
        }
        speak(display)
        }
            private fun generateImage(prompt: String) {
        addSystem("🎨 Photo bana rahi hun... (10-40 sec)")
        thread {
            try {
                val p = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
                val url = URL("https://image.pollinations.ai/prompt/$p?width=768&height=768&nologo=true&seed=" + Random.nextInt(100000))
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 120000
                conn.setRequestProperty("User-Agent", "Myra-App")
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                runOnUiThread {
                    if (bmp != null) addImage(bmp) else addSystem("⚠️ Photo nahi ban payi, dobara bolo")
                }
            } catch (e: Exception) {
                runOnUiThread { addSystem("⚠️ Photo error: ${e.message}") }
            }
        }
    }

    private fun addImage(bmp: Bitmap) {
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        iv.adjustViewBounds = true
        iv.setOnLongClickListener { saveImage(bmp); true }
        val lp = LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.75).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(8), dp(4), dp(8), dp(4))
        chat.addView(iv, lp)
        addSystem("📥 Photo ko dabaye rakho, Gallery me save ho jayegi")
        scrollDown()
    }

    private fun saveImage(bmp: Bitmap) {
        if (Build.VERSION.SDK_INT < 29) { toast("Android 10+ chahiye"); return }
        try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Myra_" + System.currentTimeMillis() + ".jpg")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Myra")
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            toast("✅ Gallery me save ho gayi")
        } catch (e: Exception) {
            toast("Save nahi hui: ${e.message}")
        }
    }
}
