package com.example.engine

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import com.example.data.WebContainerProfile
import com.example.ui.screens.ContainerHostActivity
import com.example.ui.screens.WebSandboxActivity

sealed class ShortcutPinResult {
    data class Pinned(val label: String) : ShortcutPinResult()
    data class Unsupported(val reason: String) : ShortcutPinResult()
    data class Error(val message: String) : ShortcutPinResult()
}

/**
 * Creates 1-click badged launcher shortcuts directly on the Android Home Screen using
 * ShortcutManager.requestPinShortcut() targeting ContainerHostActivity (for cloned apps)
 * and WebSandboxActivity (for isolated Web Stealth containers).
 */
class ShortcutCreator(private val context: Context) {

    private val shortcutManager: ShortcutManager? =
        context.getSystemService(ShortcutManager::class.java)

    /**
     * Pins a Home Screen shortcut targeting ContainerHostActivity with EXTRA_CONTAINER_ID
     * and badges the provided app icon Bitmap with a glowing emerald/cyan clone indicator.
     */
    fun pinClonedShortcut(container: AppContainer, icon: Bitmap?): Boolean {
        return pinClonedShortcutDetailed(container, icon) is ShortcutPinResult.Pinned
    }

    fun pinClonedShortcutDetailed(
        container: AppContainer,
        sourceIcon: Bitmap?,
        fallbackDrawable: Drawable? = null
    ): ShortcutPinResult {
        val manager = shortcutManager
            ?: return ShortcutPinResult.Unsupported("ShortcutManager unavailable on this device.")
        if (!manager.isRequestPinShortcutSupported) {
            return ShortcutPinResult.Unsupported(
                "Current Home Screen launcher does not support pinned shortcuts."
            )
        }

        val resolvedBitmap = sourceIcon ?: fallbackDrawable?.let {
            runCatching { it.toBitmap(width = 144, height = 144) }.getOrNull()
        }

        val badgedBitmap = renderBadgedCloneBitmap(
            sourceBitmap = resolvedBitmap,
            fallbackInitial = container.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "M",
            slotNumber = container.slotNumber
        )

        val launchIntent = ContainerHostActivity.createLaunchIntent(context, container)
        val shortLabel = container.displayLabel.take(25)

        val shortcutInfo = ShortcutInfo.Builder(context, "mc_clone_${container.id}")
            .setShortLabel(shortLabel)
            .setLongLabel("${container.displayLabel} (MultiCreator)")
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

    private fun renderBadgedCloneBitmap(
        sourceBitmap: Bitmap?,
        fallbackInitial: String,
        slotNumber: Int
    ): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Deep Midnight Obsidian canvas matching adaptive icon safe zone
        canvas.drawColor(0xFF090D16.toInt())

        val padding = 34
        if (sourceBitmap != null) {
            val dst = Rect(padding, padding, size - padding, size - padding)
            canvas.drawBitmap(sourceBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    0xFF06B6D4.toInt(), 0xFF6366F1.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                RectF(36f, 36f, size - 36f, size - 36f),
                32f, 32f, tilePaint
            )
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 68f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(fallbackInitial, size / 2f, size / 2f + 24f, textPaint)
        }

        // Luminous bottom-right Clone Slot Badge (#1, #2, #3...)
        val cx = size - 44f
        val cy = size - 44f
        val outerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF090D16.toInt() }
        val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - 20f, cy - 20f, cx + 20f, cy + 20f,
                0xFF10B981.toInt(), 0xFF06B6D4.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawCircle(cx, cy, 24f, outerRing)
        canvas.drawCircle(cx, cy, 19f, badgeFill)
        canvas.drawText("#$slotNumber", cx, cy + 8f, glyphPaint)

        return bitmap
    }

    private fun renderWebContainerIcon(monogram: String, accentColor: Int): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

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
