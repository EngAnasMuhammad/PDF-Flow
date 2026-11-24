package com.anasdev.pdfreader.presentation.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import com.anasdev.pdfreader.R
import java.net.URLEncoder

/**
 * Helper to handle PDF text cleaning and 3-tier zero-permission translation delegation:
 * 1. Google Translate Floating Overlay (ACTION_PROCESS_TEXT targeting Google Translate component).
 * 2. Generic ACTION_PROCESS_TEXT chooser (DeepL, Wikipedia, offline dictionaries, etc.).
 * 3. Browser fallback (Google Translate Web URL).
 */
object TranslateHelper {

    private const val MAX_URL_QUERY_LENGTH = 2000
    private const val GOOGLE_TRANSLATE_PACKAGE = "com.google.android.apps.translate"

    /**
     * Sanitizes extracted PDF text before translation:
     * - Reconnects words split across lines with hyphens (e.g., "inter-\nstate" -> "interstate").
     * - Replaces single line breaks with a single space to restore coherent sentences.
     * - Preserves double line breaks (paragraph separations).
     * - Collapses consecutive spaces/tabs and trims whitespace.
     */
    @JvmStatic
    fun cleanTextForTranslation(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. Rejoin hyphenated line breaks (e.g., "excep-\r\ntional" -> "exceptional")
        val unhyphenated = rawText.replace(Regex("""-(\r?\n)\s*"""), "")

        // 2. Normalize CRLF / CR to standard LF
        val normalizedNl = unhyphenated.replace("\r\n", "\n").replace('\r', '\n')

        // 3. Preserve paragraph breaks (2 or more newlines) while merging single line breaks into spaces
        val paragraphs = normalizedNl.split(Regex("""\n{2,}"""))

        return paragraphs.joinToString("\n\n") { paragraph ->
            paragraph
                .replace('\n', ' ')
                .replace(Regex("""[ \t]+"""), " ")
                .trim()
        }.trim()
    }

    /**
     * Launches translation using the 3-tier smart fallback:
     * - Tier 1: Google Translate floating overlay via ACTION_PROCESS_TEXT.
     * - Tier 2: Generic ACTION_PROCESS_TEXT chooser.
     * - Tier 3: Browser fallback with encoded query.
     */
    @JvmStatic
    fun launchTranslation(context: Context, rawSelectedText: String) {
        val cleanedText = cleanTextForTranslation(rawSelectedText)
        if (cleanedText.isBlank()) return

        // Query available ACTION_PROCESS_TEXT apps
        val processTextIntent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
        }

        val packageManager = context.packageManager
        val resolveInfoList: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    processTextIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(processTextIntent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val candidates = resolveInfoList.filter {
            it.activityInfo != null && it.activityInfo.packageName != context.packageName
        }

        // --- TIER 1: Google Translate Floating Overlay ---
        val googleTranslate = candidates.firstOrNull {
            it.activityInfo.packageName == GOOGLE_TRANSLATE_PACKAGE
        }

        if (googleTranslate != null) {
            val tier1Intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                component = ComponentName(
                    googleTranslate.activityInfo.packageName,
                    googleTranslate.activityInfo.name
                )
                putExtra(Intent.EXTRA_PROCESS_TEXT, cleanedText)
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                context.startActivity(tier1Intent)
                return
            } catch (_: Exception) {
                // If Tier 1 fails unexpectedly, proceed to Tier 2
            }
        }

        // --- TIER 2: Generic ACTION_PROCESS_TEXT Chooser ---
        if (candidates.isNotEmpty()) {
            val tier2Intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, cleanedText)
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            }
            val chooser = Intent.createChooser(tier2Intent, context.getString(R.string.translate)).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                context.startActivity(chooser)
                return
            } catch (_: Exception) {
                // If Tier 2 fails, proceed to Tier 3
            }
        }

        // --- TIER 3: Browser Fallback ---
        try {
            val textForUrl = if (cleanedText.length > MAX_URL_QUERY_LENGTH) {
                cleanedText.take(MAX_URL_QUERY_LENGTH)
            } else {
                cleanedText
            }
            val encodedText = URLEncoder.encode(textForUrl, "UTF-8")
            val webUri = "https://translate.google.com/?sl=auto&text=$encodedText".toUri()
            val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(browserIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_translation_app_found, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, R.string.no_translation_app_found, Toast.LENGTH_SHORT).show()
        }
    }
}
