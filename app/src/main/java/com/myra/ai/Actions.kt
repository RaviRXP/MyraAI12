package com.myra.ai

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import java.net.URLEncoder
import java.util.Locale

class Actions(private val activity: Activity) {

    fun perform(type: String, value: String): String? {
        return try {
            when (type) {
                "OPEN_APP" -> openApp(value)
                "YOUTUBE" -> youtube(value)
                "CALL" -> call(value)
                "WHATSAPP" -> whatsapp(value)
                "ALARM" -> alarm(value)
                "FLASH" -> flash(value)
                "SEARCH" -> openUrl("https://www.google.com/search?q=" + enc(value), "Google search")
                "URL" -> openUrl(value, "Link")
                "SETTINGS" -> settings(value)
                else -> null
            }
        } catch (e: Exception) {
            "⚠️ Kaam nahi hua: ${e.message}"
        }
    }
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(i)
    }

    private fun openApp(name: String): String {
        val pm = activity.packageManager
        var q = name.lowercase().trim()
        if (q == "insta") q = "instagram"
        if (q == "yt") q = "youtube"
        if (q == "playstore") q = "play store"
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps: List<ResolveInfo> = pm.queryIntentActivities(launcher, 0)
        fun label(r: ResolveInfo) = r.loadLabel(pm).toString().lowercase()
        val match = apps.firstOrNull { label(it) == q }
            ?: apps.firstOrNull { label(it).contains(q) }
            ?: apps.firstOrNull { label(it).length > 2 && q.contains(label(it)) }
            ?: apps.firstOrNull { it.activityInfo.packageName.lowercase().contains(q.replace(" ", "")) }
            ?: return "⚠️ '$name' app phone me nahi mila"
        val i = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "⚠️ '$name' khul nahi paya"
        start(i)
        return "✅ ${match.loadLabel(pm)} khol diya"
    }
        private fun youtube(q: String): String {
        try {
            start(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, q))
        } catch (e: Exception) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc(q))))
        }
        return "✅ YouTube par '$q' search kiya 🎵"
    }

    private fun findNumber(name: String): Pair<String, String>? {
        val digits = name.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 6) return Pair(name, digits)
        if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null

        val n = name.lowercase().trim()
        val family = listOf(
            listOf("mom", "mummy", "mumma", "mummi", "mammi", "maa", "mother", "mom ji"),
            listOf("dad", "papa", "pappa", "daddy", "father", "papa ji"),
            listOf("bhai", "brother", "bhaiya", "bro"),
            listOf("didi", "sister", "di", "behen")
        )
        val tries = family.firstOrNull { n in it } ?: listOf(n)
                for (t in tries) {
            val cursor = activity.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$t%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) return Pair(it.getString(0), it.getString(1))
            }
        }
        return null
    }

    private fun call(name: String): String {
        val c = findNumber(name) ?: return "⚠️ '$name' contact nahi mila"
        val uri = Uri.parse("tel:" + Uri.encode(c.second))
        if (activity.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            start(Intent(Intent.ACTION_CALL, uri))
        } else {
            start(Intent(Intent.ACTION_DIAL, uri))
        }
        return "✅ ${c.first} ko call laga rahi hun 📞"
    }
        private fun whatsapp(value: String): String {
        val parts = value.split("|", limit = 2)
        val who = parts[0].trim()
        val text = parts.getOrElse(1) { "" }.trim()
        val c = findNumber(who) ?: return "⚠️ '$who' contact nahi mila"
        var num = c.second.filter { it.isDigit() }
        if (num.length == 11 && num.startsWith("0")) num = num.substring(1)
        if (num.length == 10) num = "91$num"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$num&text=" + enc(text)))
        i.setPackage("com.whatsapp")
        try {
            start(i)
        } catch (e: Exception) {
            i.setPackage(null)
            start(i)
        }
        return "✅ ${c.first} ka WhatsApp khul gaya, message likh diya. Bas Send ➤ dabao"
    }

    private fun alarm(v: String): String {
        val p = v.trim().split(":")
        val h = p[0].trim().toInt()
        val m = p.getOrElse(1) { "0" }.trim().toInt()
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Myra ❤️ Utho baby!")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        start(i)
        return "✅ Alarm laga diya ⏰ " + String.format(Locale.ENGLISH, "%02d:%02d", h, m)
    }
        private fun flash(v: String): String {
        val cm = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "⚠️ Flash nahi mila"
        val on = v.lowercase().contains("on")
        cm.setTorchMode(id, on)
        return if (on) "✅ Flash ON 🔦" else "✅ Flash OFF"
    }

    private fun openUrl(url: String, what: String): String {
        val u = if (url.startsWith("http")) url else "https://$url"
        start(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        return "✅ $what khol diya 🌐"
    }

    private fun settings(v: String): String {
        val action = when (v.lowercase().trim()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "display", "wallpaper" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "ringtone" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        try {
            start(Intent(action))
        } catch (e: Exception) {
            start(Intent(Settings.ACTION_SETTINGS))
        }
        return "✅ Settings khol di ⚙️"
    }
}
