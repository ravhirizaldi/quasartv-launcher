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

    fun discover(): List<AppRecord> {
        val candidates = ArrayList<Candidate>()
        candidates += query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        candidates += query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))

        val records = LinkedHashMap<String, AppRecord>()
        candidates
            .sortedWith(compareBy<Candidate>({ it.packageName }, { it.activityName }, { it.label }))
            .forEach { candidate ->
                if (records.containsKey(candidate.packageName)) return@forEach
                val launchIntent = candidate.launchIntent ?: return@forEach
                records[candidate.packageName] = AppRecord(
                    label = candidate.label,
                    packageName = candidate.packageName,
                    launchIntent = launchIntent,
                    icon = candidate.icon,
                    favoriteEligible = candidate.packageName != launcherPackageName
                )
            }

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
            if (context.packageManager.resolveActivity(intent, 0) == null) {
                LaunchResult.Unavailable("App is no longer available.", app.packageName)
            } else {
                context.startActivity(intent)
                LaunchResult.Success(app.packageName)
            }
        } catch (error: ActivityNotFoundException) {
            LaunchResult.Unavailable("App is no longer available.", app.packageName)
        } catch (error: SecurityException) {
            LaunchResult.Failed("App launch was denied.", app.packageName, error)
        } catch (error: RuntimeException) {
            LaunchResult.Failed("App launch failed.", app.packageName, error)
        }
    }

    private fun query(intent: Intent): List<Candidate> {
        val resolved = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (_: RuntimeException) {
            return emptyList()
        }

        return resolved.orEmpty().mapNotNull { resolveInfo -> candidate(resolveInfo, intent) }
    }

    private fun candidate(resolveInfo: ResolveInfo, queryIntent: Intent): Candidate? {
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
        val launchIntent = try {
            packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?: packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: RuntimeException) {
            null
        } ?: try {
            Intent(queryIntent).setComponent(ComponentName(packageName, activityName))
        } catch (_: RuntimeException) {
            null
        }
        val applicationInfo = try {
            activityInfo.applicationInfo
        } catch (_: RuntimeException) {
            null
        }
        val label = loadLabel(resolveInfo, applicationInfo, packageName)
        val icon = loadIcon(resolveInfo, applicationInfo)?.let(::boundedIcon)
        return Candidate(packageName, activityName, label, launchIntent, icon)
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

    private data class Candidate(
        val packageName: String,
        val activityName: String,
        val label: String,
        val launchIntent: Intent?,
        val icon: Drawable?
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
