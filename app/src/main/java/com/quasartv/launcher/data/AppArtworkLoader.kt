package com.quasartv.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.roundToInt

class AppArtworkLoader(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "QuasarTV-artwork") }
    private val maxWidth = (264 * context.resources.displayMetrics.density).roundToInt().coerceAtMost(512)
    private val maxHeight = (156 * context.resources.displayMetrics.density).roundToInt().coerceAtMost(288)
    private val cache = object : LruCache<String, BitmapDrawable>(1024 * 1024) {
        override fun sizeOf(key: String, value: BitmapDrawable) = value.bitmap.allocationByteCount
    }
    private var pending: Future<*>? = null
    private var generation = 0
    private var closed = false

    internal val cachedByteCount: Int get() = cache.size()

    fun load(app: AppCatalog.AppRecord, display: (Drawable) -> Unit) {
        if (closed) return
        val request = ++generation
        pending?.cancel(true)
        fun deliver(drawable: Drawable) {
            mainHandler.post {
                if (!closed && request == generation) display(drawable)
            }
        }
        // Cached artwork must be present before the next frame, including Home rebuilds.
        cache.get(app.packageName)?.let { display(it); return }
        pending = worker.submit {
            val source = try {
                val activityInfo = app.launchIntent.component?.let { packageManager.getActivityInfo(it, 0) }
                val appInfo = activityInfo?.applicationInfo ?: packageManager.getApplicationInfo(app.packageName, 0)
                val bannerId = activityInfo?.banner?.takeIf { it != 0 } ?: appInfo.banner
                highDensityDrawable(packageManager, appInfo, bannerId)
                    ?: highDensityDrawable(packageManager, appInfo, activityInfo?.icon?.takeIf { it != 0 } ?: appInfo.icon)
                    ?: app.icon
            } catch (_: Exception) {
                app.icon
            } ?: return@submit
            if (Thread.currentThread().isInterrupted) return@submit
            val (width, height) = artworkSize(source.intrinsicWidth, source.intrinsicHeight, maxWidth, maxHeight)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            source.setBounds(0, 0, width, height)
            source.draw(Canvas(bitmap))
            val drawable = BitmapDrawable(null, bitmap).apply { isFilterBitmap = true }
            if (Thread.currentThread().isInterrupted) return@submit
            cache.put(app.packageName, drawable)
            deliver(drawable)
        }
    }

    fun clear() {
        generation++
        pending?.cancel(true)
        cache.evictAll()
    }

    fun close() {
        closed = true
        clear()
        worker.shutdownNow()
    }
}

internal fun artworkSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    val sourceWidth = width.coerceAtLeast(1)
    val sourceHeight = height.coerceAtLeast(1)
    val scale = minOf(maxWidth.toFloat() / sourceWidth, maxHeight.toFloat() / sourceHeight)
    return (sourceWidth * scale).roundToInt().coerceIn(1, maxWidth) to
        (sourceHeight * scale).roundToInt().coerceIn(1, maxHeight)
}
