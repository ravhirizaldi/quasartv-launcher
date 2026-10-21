package com.quasartv.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.content.pm.ApplicationInfo
import android.util.DisplayMetrics
import java.util.Locale

class AppCatalog(
    private val packageManager: PackageManager,
    private val launcherPackageName: String = DEFAULT_LAUNCHER_PACKAGE
) {
    constructor(context: Context) : this(context.packageManager, context.packageName)

    private val appCache = HashMap<String, CachedApp>()

    fun discover(): List<AppRecord> {
        val rawCandidates = ArrayList<RawCandidate>()
        rawCandidates += query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        rawCandidates += query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))

        // Both platform queries commonly return the same activity. Drop duplicates before any
        // label or icon work so package resources are touched once per launchable activity.
        val seenActivities = HashSet<String>()
        val candidates = rawCandidates.mapNotNull { raw ->
            if (!seenActivities.add(raw.packageName + '\u0000' + raw.activityName)) {
                null
            } else {
                candidate(raw)
            }
        }

        val records = LinkedHashMap<String, AppRecord>()
        candidates
            .sortedWith(compareBy<Candidate>({ it.packageName }, { it.activityName }))
            .forEach { candidate ->
                if (records.containsKey(candidate.packageName)) return@forEach
                val launchIntent = candidate.launchIntent ?: return@forEach
                val cached = cachedApp(candidate)
                records[candidate.packageName] = AppRecord(
                    label = cached.label,
                    packageName = candidate.packageName,
                    launchIntent = launchIntent,
                    icon = cached.icon,
                    favoriteEligible = candidate.packageName != launcherPackageName
                )
            }

        // Do not retain artwork for packages that were uninstalled.
        appCache.keys.retainAll(records.keys)

        return records.values
            .filter { it.packageName != launcherPackageName }
            .sortedWith(compareBy<AppRecord>({ it.label.lowercase(Locale.ROOT) }, { it.packageName }))
    }

    fun launch(context: Context, app: AppRecord): LaunchResult {
        if (app.packageName == launcherPackageName || !app.favoriteEligible) {
            return LaunchResult.Unavailable("App is not eligible for launch.", app.packageName)
        }

        val intent = Intent(app.launchIntent)
        return try {
            // startActivity already performs resolution; a separate resolveActivity call only
            // adds a binder round trip to every launch.
            context.startActivity(intent)
            LaunchResult.Success(app.packageName)
        } catch (error: ActivityNotFoundException) {
            LaunchResult.Unavailable("App is no longer available.", app.packageName)
        } catch (error: SecurityException) {
            LaunchResult.Failed("App launch was denied.", app.packageName, error)
        } catch (error: RuntimeException) {
            LaunchResult.Failed("App launch failed.", app.packageName, error)
        }
    }

    private fun query(intent: Intent): List<RawCandidate> {
        val resolved = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (_: RuntimeException) {
            return emptyList()
        }

        return resolved.orEmpty().mapNotNull { resolveInfo -> rawCandidate(resolveInfo, intent) }
    }

    private fun rawCandidate(resolveInfo: ResolveInfo, queryIntent: Intent): RawCandidate? {
        val activityInfo = try {
            resolveInfo.activityInfo
        } catch (_: RuntimeException) {
            return null
        } ?: return null
        val packageName = try {
            activityInfo.packageName?.takeIf { it.isNotBlank() }
        } catch (_: RuntimeException) {
            null
        } ?: return null
        if (packageName == launcherPackageName) return null
        val activityName = try {
            activityInfo.name?.takeIf { it.isNotBlank() }
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val applicationInfo = try {
            activityInfo.applicationInfo
        } catch (_: RuntimeException) {
            null
        }
        return RawCandidate(packageName, activityName, queryIntent, resolveInfo, applicationInfo)
    }

    private fun candidate(raw: RawCandidate): Candidate {
        val launchIntent = try {
            packageManager.getLeanbackLaunchIntentForPackage(raw.packageName)
                ?: packageManager.getLaunchIntentForPackage(raw.packageName)
        } catch (_: RuntimeException) {
            null
        } ?: try {
            Intent(raw.queryIntent).setComponent(ComponentName(raw.packageName, raw.activityName))
        } catch (_: RuntimeException) {
            null
        }
        return Candidate(raw.packageName, raw.activityName, launchIntent, raw.resolveInfo, raw.applicationInfo)
    }

    private fun loadLabel(
        resolveInfo: ResolveInfo,
        applicationInfo: android.content.pm.ApplicationInfo?,
        packageName: String
    ): String {
        val activityLabel = try {
            resolveInfo.loadLabel(packageManager)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: RuntimeException) {
            null
        }
        if (activityLabel != null) return activityLabel

        val applicationLabel = try {
            applicationInfo?.loadLabel(packageManager)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: RuntimeException) {
            null
        }
        return applicationLabel ?: packageName
    }

    private fun cachedApp(candidate: Candidate): CachedApp {
        // A new install path is issued when an app is updated, so it invalidates stale artwork.
        val sourceDir = candidate.applicationInfo?.sourceDir
            ?: candidate.applicationInfo?.publicSourceDir
        val locale = Locale.getDefault().toLanguageTag()
        appCache[candidate.packageName]?.let { cached ->
            if (cached.activityName == candidate.activityName &&
                cached.sourceDir == sourceDir &&
                cached.locale == locale
            ) {
                return cached
            }
        }
        return CachedApp(
            activityName = candidate.activityName,
            sourceDir = sourceDir,
            locale = locale,
            label = loadLabel(candidate.resolveInfo, candidate.applicationInfo, candidate.packageName),
            icon = loadIcon(candidate.resolveInfo, candidate.applicationInfo)?.let(::boundedIcon)
        ).also { appCache[candidate.packageName] = it }
    }

    private fun loadIcon(
        resolveInfo: ResolveInfo,
        applicationInfo: android.content.pm.ApplicationInfo?
    ): Drawable? {
        if (applicationInfo != null) {
            val resourceId = resolveInfo.activityInfo?.icon?.takeIf { it != 0 } ?: applicationInfo.icon
            highDensityDrawable(packageManager, applicationInfo, resourceId)?.let { return it }
        }
        try {
            return resolveInfo.loadIcon(packageManager)
        } catch (_: RuntimeException) {
            // Try application icon below when activity resources are unavailable.
        }
        return try {
            applicationInfo?.loadIcon(packageManager)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun boundedIcon(icon: Drawable): Drawable {
        // Package artwork can be much larger than its TV tile; retain at most 64 KiB per icon.
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val width = icon.intrinsicWidth.coerceAtLeast(1)
        val height = icon.intrinsicHeight.coerceAtLeast(1)
        val scale = 128f / maxOf(width, height)
        val drawnWidth = (width * scale).toInt().coerceAtLeast(1)
        val drawnHeight = (height * scale).toInt().coerceAtLeast(1)
        val left = (128 - drawnWidth) / 2
        val top = (128 - drawnHeight) / 2
        icon.setBounds(left, top, left + drawnWidth, top + drawnHeight)
        icon.draw(Canvas(bitmap))
        return BitmapDrawable(null, bitmap).apply { isFilterBitmap = true }
    }

    data class AppRecord(
        val label: String,
        val packageName: String,
        val launchIntent: Intent,
        val icon: Drawable?,
        val favoriteEligible: Boolean
    )

    private data class CachedApp(
        val activityName: String,
        val sourceDir: String?,
        val locale: String,
        val label: String,
        val icon: Drawable?
    )

    private data class RawCandidate(
        val packageName: String,
        val activityName: String,
        val queryIntent: Intent,
        val resolveInfo: ResolveInfo,
        val applicationInfo: ApplicationInfo?
    )

    private data class Candidate(
        val packageName: String,
        val activityName: String,
        val launchIntent: Intent?,
        val resolveInfo: ResolveInfo,
        val applicationInfo: ApplicationInfo?
    )

    companion object {
        const val DEFAULT_LAUNCHER_PACKAGE = "com.quasartv.launcher"
    }
}

internal fun highDensityDrawable(packageManager: PackageManager, appInfo: ApplicationInfo, resourceId: Int): Drawable? =
    if (resourceId == 0) null else try {
        packageManager.getResourcesForApplication(appInfo)
            .getDrawableForDensity(resourceId, DisplayMetrics.DENSITY_XXXHIGH, null)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }
