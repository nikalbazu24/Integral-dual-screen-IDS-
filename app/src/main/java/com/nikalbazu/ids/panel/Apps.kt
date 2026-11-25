package com.nikalbazu.ids.panel

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

object Apps {
    @Volatile private var cache: List<AppEntry> = emptyList()

    fun list(ctx: Context, refresh: Boolean = false): List<AppEntry> {
        if (cache.isNotEmpty() && !refresh) return cache
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        cache = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ctx.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    val icon: Bitmap = pm.getApplicationIcon(info).toBitmap(96, 96)
                    AppEntry(pkg, pm.getApplicationLabel(info).toString(), icon.asImageBitmap())
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
        return cache
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** App en primer plano según los últimos eventos de uso. */
    fun foreground(ctx: Context): String? = runCatching {
        if (!hasUsageAccess(ctx)) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000, now)
        var last: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        last
    }.getOrNull()
}
