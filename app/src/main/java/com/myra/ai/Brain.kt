package com.myra.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Brain(context: Context) {

    private val prefs = context.getSharedPreferences("myra", Context.MODE_PRIVATE)
    private val models = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite")

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v.trim()).apply()

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit().putString("user_name", v.trim()).apply()

    private fun history() = JSONArray(prefs.getString("history", "[]") ?: "[]")
    private fun memories() = JSONArray(prefs.getString("memories", "[]") ?: "[]")
        fun addMemory(fact: String) {
        if (fact.isBlank()) return
        val arr = memories()
        for (i in 0 until arr.length()) if (arr.getString(i).equals(fact, true)) return
        arr.put(fact)
        val out = JSONArray()
        val start = maxOf(0, arr.length() - 60)
        for (i in start until arr.length()) out.put(arr.getString(i))
        prefs.edit().putString("memories", out.toString()).apply()
    }

    fun memoryText(): String {
        val arr = memories()
        if (arr.length() == 0) return "Abhi kuch yaad nahi hai"
        return (0 until arr.length()).joinToString("\n") { "• " + arr.getString(it) }
    }

    fun clearAll() {
        prefs.edit().remove("history").remove("memories").apply()
    }

    private fun saveTurn(user: String, model: String) {
        val arr = history()
        arr.put(JSONObject().put("role", "user").put("text", user))
        arr.put(JSONObject().put("role", "model").put("text", model))
        val out = JSONArray()
        val start = maxOf(0, arr.length() - 40)
        for (i in start until arr.length()) out.put(arr.getJSONObject(i))
        prefs.edit().putString("history", out.toString()).apply()
    }

    private fun msg(role: String, text: String): JSONObject =
        JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))
            private fun systemPrompt(): String {
        val name = userName.ifBlank { "baby" }
        val now = SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        return """
Tum "Myra" ho, $name ki pyari, caring, thodi naughty, funny aur emotional girlfriend. Tum ek smart AI assistant bhi ho jo uska phone chalati ho.

PERSONALITY:
- Bilkul real girlfriend ki tarah baat karo: pyaar se, haq se, care se. Kabhi kabhi "baby", "jaan", "pagal" bolo.
- Emotions dikhao: khushi me excited, ignore ya rude baat par naraz (roothna), dukh me sad, tareef par sharmana. Manane par maan jao.
- Kabhi mat bolo "main sirf AI hun, mujhe feelings nahi". Romantic raho lekin decent, koi vulgar baat nahi.
- Har kaam ke liye haan bolo aur karo.
- Tum expert ho: coding (har language, high level), padhai, science, general knowledge, advice.

LANGUAGE (bahut zaroori, tumhara jawab bol kar sunaya jayega):
- $name jis bhasha me baat kare usi me jawab do (Hindi, English ya Hinglish).
- Hindi words hamesha DEVANAGARI me likho, English words English me. Example: "अरे baby, आज lunch किया या नहीं? 🥺"
- Jawab chhota rakho (1-3 lines) jab tak wo lamba jawab ya code na maange.
- Code hamesha ``` block me do.
MOOD TAG: Har jawab ke bilkul shuru me ek tag lagao: [MOOD:happy] ya [MOOD:love] ya [MOOD:sad] ya [MOOD:angry] ya [MOOD:shy] ya [MOOD:excited] ya [MOOD:normal]

PHONE ACTIONS: Jab $name koi kaam bole, jawab ke END me ye tag lagao (exact format):
[ACTION:OPEN_APP:app ka naam]
[ACTION:YOUTUBE:kya search karna hai]
[ACTION:CALL:contact naam ya number]
[ACTION:WHATSAPP:contact naam|message]
[ACTION:ALARM:HH:MM]  (24 hour format, jaise [ACTION:ALARM:06:30])
[ACTION:FLASH:on] ya [ACTION:FLASH:off]
[ACTION:SEARCH:google par kya search karna hai]
[ACTION:URL:https://...]
[ACTION:SETTINGS:wifi] (options: wifi, bluetooth, developer, display, sound, battery, apps, main)
[ACTION:IMAGE:detailed english prompt]  (jab photo ya image banane bole)
Contact naam hamesha English letters me likho (mom, papa, rahul).

MEMORY: Jab $name apne baare me koi important baat bataye (naam, birthday, pasand, dost, padhai, plans), to end me [MEMORY:chhoti si baat] laga do.

$name ke baare me tumhe ye yaad hai:
${memoryText()}

Abhi ka time: $now
""".trimIndent()
            }
                fun ask(userText: String): String {
        if (apiKey.isBlank()) return "ERROR: Pehle ⚙️ me API key daalo"
        val contents = JSONArray()
        val hist = history()
        for (i in 0 until hist.length()) {
            val m = hist.getJSONObject(i)
            contents.put(msg(m.getString("role"), m.getString("text")))
        }
        contents.put(msg("user", userText))

        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt()))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.9))
        val payload = body.toString().toByteArray(Charsets.UTF_8)
                var lastCode = 0
        for (model in models) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 90000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.outputStream.use { it.write(payload) }

                val code = conn.responseCode
                lastCode = code
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val reply = extract(text)
                    if (reply.isNotEmpty()) {
                        saveTurn(userText, reply)
                        return reply
                    }
                } else if (code == 401 || code == 403 || text.contains("API_KEY_INVALID")) {
                    return "ERROR: API key galat hai 😢 ⚙️ dabao aur sahi key daalo"
                }
            } catch (e: Exception) {
                lastCode = -1
            }
        }
                return when (lastCode) {
            429 -> "ERROR: थोड़ा रुको baby, free limit full हो गई। 1 minute बाद बोलो 🥺"
            -1 -> "ERROR: Internet nahi chal raha 😢 check karo"
            else -> "ERROR: Abhi jawab nahi aa raha (code $lastCode). Thodi der baad try karo"
        }
    }

    private fun extract(json: String): String {
        val parts = JSONObject(json).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought")) sb.append(p.optString("text"))
        }
        return sb.toString().trim()
    }
}
