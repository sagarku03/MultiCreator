package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import com.example.MainActivity
import com.example.data.WebContainerProfile
import com.example.ui.screens.WebSandboxActivity

sealed class ShortcutPinResult {
    data class Pinned(val label: String) : ShortcutPinResult()
    data class Unsupported(val reason: String) : ShortcutPinResult()
    data class Error(val message: String) : ShortcutPinResult()
}

/**
 * Creates 1-click badged launcher shortcuts directly on the Android Home Screen using
 * ShortcutManager.requestPinShortcut() for both Work Profile apps and Web Stealth containers.
 */
class ShortcutCreator(private val context: Context) {

    private val shortcutManager: ShortcutManager? =
        context.getSystemService(ShortcutManager::class.java)

    /**
     * Pins a Home Screen shortcut for a native app with a neon cyber-glass isolation badge.
     */
    fun pinClonedAppShortcut(app: InstalledAppItem): ShortcutPinResult {
        val manager = shortcutManager
            ?: return ShortcutPinResult.Unsupported("ShortcutManager unavailable on this device.")
        if (!manager.isRequestPinShortcutSupported) {
            return ShortcutPinResult.Unsupported(
                "Current Home Screen launcher does not support pinned shortcuts."
            )
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_LAUNCH_CLONED_APP
            putExtra(EXTRA_TARGET_PACKAGE, app.packageName)
            putExtra(EXTRA_TARGET_APP_NAME, app.appName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val badgedBitmap = renderBadgedAppIcon(
            sourceDrawable = app.icon,
            fallbackInitial = app.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "M",
            badgeColor = if (app.isClonedInWorkProfile) 0xFF10B981.toInt() else 0xFF06B6D4.toInt()
        )

        val shortLabel = "${app.appName} Vault".take(25)
        val shortcutInfo = ShortcutInfo.Builder(context, "mc_app_${app.packageName}")
            .setShortLabel(shortLabel)
            .setLongLabel("${app.appName} (MultiCreator)")
            .setIcon(Icon.createWithAdaptiveBitmap(badgedBitmap))
            .setIntent(launchIntent)
            .build()

        return runCatching {
            val requested = manager.requestPinShortcut(shortcutInfo, null)
            if (requested) {
                ShortcutPinResult.Pinned(shortLabel)
            } else {
                ShortcutPinResult.Unsupported("Launcher declined shortcut pin request.")
            }
        }.getOrElse { ex ->
            ShortcutPinResult.Error(ex.localizedMessage ?: "Failed to pin shortcut.")
        }
    }

    /**
     * Pins a 1-click Home Screen shortcut that opens an isolated Web Stealth container directly
     * inside WebSandboxActivity with its dedicated cookie jar and fingerprint seed.
     */
    fun pinWebSandboxShortcut(container: WebContainerProfile): ShortcutPinResult {
        val manager = shortcutManager
            ?: return ShortcutPinResult.Unsupported("ShortcutManager unavailable on this device.")
        if (!manager.isRequestPinShortcutSupported) {
            return ShortcutPinResult.Unsupported(
                "Current Home Screen launcher does not support pinned shortcuts."
            )
        }

        val sandboxIntent = WebSandboxActivity.createLaunchIntent(
            context = context,
            container = container
        )

        val monogram = container.name
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "MC" }

        val badgedBitmap = renderWebContainerIcon(
            monogram = monogram,
            accentColor = container.accentHex.toInt()
        )

        val shortLabel = container.name.take(24)
        val shortcutInfo = ShortcutInfo.Builder(context, "mc_web_${container.id}")
            .setShortLabel(shortLabel)
            .setLongLabel("${container.name} • Stealth Sandbox")
            .setIcon(Icon.createWithAdaptiveBitmap(badgedBitmap))
            .setIntent(sandboxIntent)
            .build()

        return runCatching {
            val requested = manager.requestPinShortcut(shortcutInfo, null)
            if (requested) {
                ShortcutPinResult.Pinned(shortLabel)
            } else {
                ShortcutPinResult.Unsupported("Launcher declined shortcut pin request.")
            }
        }.getOrElse { ex ->
            ShortcutPinResult.Error(ex.localizedMessage ?: "Failed to pin web shortcut.")
        }
    }

    private fun renderBadgedAppIcon(
        sourceDrawable: Drawable?,
        fallbackInitial: String,
        badgeColor: Int
    ): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Deep Obsidian backdrop for adaptive safe-zone consistency
        canvas.drawColor(0xFF090D16.toInt())

        val padding = 34
        if (sourceDrawable != null) {
            sourceDrawable.setBounds(padding, padding, size - padding, size - padding)
            sourceDrawable.draw(canvas)
        } else {
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    0xFF06B6D4.toInt(), 0xFF6366F1.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                RectF(36f, 36f, size - 36f, size - 36f),
                32f, 32f, circlePaint
            )
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 68f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(fallbackInitial, size / 2f, size / 2f + 24f, textPaint)
        }

        // Luminous bottom-right Work Vault badge
        val badgeCenterX = size - 44f
        val badgeCenterY = size - 44f
        val outerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF090D16.toInt() }
        val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeColor }
        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawCircle(badgeCenterX, badgeCenterY, 24f, outerRing)
        canvas.drawCircle(badgeCenterX, badgeCenterY, 19f, badgeFill)
        canvas.drawText("2", badgeCenterX, badgeCenterY + 8.5f, glyphPaint)

        return bitmap
    }

    private fun renderWebContainerIcon(monogram: String, accentColor: Int): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient cyber-glass tile
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                0xFF090D16.toInt(), 0xFF0F172A.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                32f, 32f, size - 32f, size - 32f,
                accentColor, 0xFF6366F1.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(36f, 36f, size - 36f, size - 36f), 34f, 34f, tilePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(monogram, size / 2f, size / 2f + 19f, textPaint)

        // Emerald stealth shield dot
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF090D16.toInt() }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10B981.toInt() }
        canvas.drawCircle(size - 44f, size - 44f, 20f, ringPaint)
        canvas.drawCircle(size - 44f, size - 44f, 15f, dotPaint)

        return bitmap
    }

    companion object {
        const val ACTION_LAUNCH_CLONED_APP = "com.example.action.LAUNCH_CLONED_APP"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_APP_NAME = "extra_target_app_name"
    }
}
