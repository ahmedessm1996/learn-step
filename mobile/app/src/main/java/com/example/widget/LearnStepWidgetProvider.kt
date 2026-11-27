package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

data class WidgetExperimentSnapshot(
    val trackKey: String = "html",
    val trackTitle: String = "HTML",
    val lessonIndex: Int = 0,
    val headline: String = "كيف تصنع بطاقة زجاجية شفافة دون التأثير على وضوح النص؟",
    val question: String = "ماذا يحدث للنص عندما تضيف شفافية rgba على الخلفية؟",
    val personalGoal: String = "",
    val isGlassColors: Boolean = true
)

class LearnStepWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = readSnapshot(context)
        appWidgetIds.forEach { appWidgetId ->
            updateSingleWidget(context, appWidgetManager, appWidgetId, snapshot)
        }
    }

    companion object {
        const val EXTRA_LAUNCH_EXPERIMENT = "com.example.widget.EXTRA_LAUNCH_EXPERIMENT"
        const val EXTRA_WIDGET_TRACK = "com.example.widget.EXTRA_WIDGET_TRACK"
        const val EXTRA_WIDGET_INDEX = "com.example.widget.EXTRA_WIDGET_INDEX"

        private const val PREFS_NAME = "steplearn_widget_snapshot"
        private const val KEY_TRACK = "trackKey"
        private const val KEY_TRACK_TITLE = "trackTitle"
        private const val KEY_INDEX = "lessonIndex"
        private const val KEY_HEADLINE = "headline"
        private const val KEY_QUESTION = "question"
        private const val KEY_PERSONAL_GOAL = "personalGoal"
        private const val KEY_GLASS = "isGlassColors"

        fun saveAndRefreshWidget(context: Context, snapshot: WidgetExperimentSnapshot) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TRACK, snapshot.trackKey)
                .putString(KEY_TRACK_TITLE, snapshot.trackTitle)
                .putInt(KEY_INDEX, snapshot.lessonIndex)
                .putString(KEY_HEADLINE, snapshot.headline)
                .putString(KEY_QUESTION, snapshot.question)
                .putString(KEY_PERSONAL_GOAL, snapshot.personalGoal)
                .putBoolean(KEY_GLASS, snapshot.isGlassColors)
                .apply()
            updateAllWidgets(context)
        }

        fun readSnapshot(context: Context): WidgetExperimentSnapshot {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return WidgetExperimentSnapshot(
                trackKey = prefs.getString(KEY_TRACK, "html").orEmpty().ifBlank { "html" },
                trackTitle = prefs.getString(KEY_TRACK_TITLE, "HTML").orEmpty().ifBlank { "HTML" },
                lessonIndex = prefs.getInt(KEY_INDEX, 0),
                headline = prefs.getString(
                    KEY_HEADLINE,
                    "كيف تصنع بطاقة زجاجية شفافة دون التأثير على وضوح النص؟"
                ).orEmpty(),
                question = prefs.getString(
                    KEY_QUESTION,
                    "ماذا يحدث للنص عندما تضيف شفافية rgba على الخلفية؟"
                ).orEmpty(),
                personalGoal = prefs.getString(KEY_PERSONAL_GOAL, "").orEmpty(),
                isGlassColors = prefs.getBoolean(KEY_GLASS, true)
            )
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, LearnStepWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val snapshot = readSnapshot(context)
            ids.forEach { appWidgetId ->
                updateSingleWidget(context, manager, appWidgetId, snapshot)
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, LearnStepWidgetProvider::class.java)
            return manager.requestPinAppWidget(provider, null, null)
        }

        fun buildLaunchIntent(context: Context, snapshot: WidgetExperimentSnapshot): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_LAUNCH_EXPERIMENT, true)
                putExtra(EXTRA_WIDGET_TRACK, snapshot.trackKey)
                putExtra(EXTRA_WIDGET_INDEX, snapshot.lessonIndex)
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            snapshot: WidgetExperimentSnapshot
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_learnstep)

            val badgeText = if (snapshot.personalGoal.isNotBlank()) {
                "${snapshot.trackTitle} · هدفي: ${snapshot.personalGoal}"
            } else {
                "steplearn · ${snapshot.trackTitle}"
            }
            val displayQuestion = snapshot.question.ifBlank { snapshot.headline }

            views.setTextViewText(R.id.widget_badge, badgeText)
            views.setTextViewText(R.id.widget_question, displayQuestion)

            val previewBitmap = renderPreviewBitmap(snapshot)
            views.setImageViewBitmap(R.id.widget_preview_image, previewBitmap)

            val launchIntent = buildLaunchIntent(context, snapshot)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_open_btn, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Renders a lightweight 260x104 RGB_565 preview bitmap well below RemoteViews memory bounds.
         */
        fun renderPreviewBitmap(snapshot: WidgetExperimentSnapshot): Bitmap {
            val width = 260
            val height = 104
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#121B17")
            }
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 18f, 18f, bgPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#24382F")
            }
            canvas.drawRoundRect(RectF(1f, 1f, width - 1f, height - 1f), 18f, 18f, borderPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8CE5BE")
                textSize = 15f
                isFakeBoldText = true
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EAF3EE")
                textSize = 12f
            }

            when {
                snapshot.trackKey == "database" -> {
                    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#183E30")
                    }
                    canvas.drawRoundRect(RectF(14f, 14f, width - 14f, 40f), 8f, 8f, headerPaint)
                    canvas.drawText("SELECT name, score FROM products", 22f, 31f, titlePaint)
                    canvas.drawText("• بطاقة ذكية      96%", 24f, 62f, bodyPaint)
                    canvas.drawText("• محرر الكود      92%", 24f, 84f, bodyPaint)
                }
                snapshot.trackKey == "csharp" -> {
                    val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#163127")
                    }
                    canvas.drawRoundRect(RectF(14f, 14f, width - 14f, height - 14f), 12f, 12f, chipPaint)
                    canvas.drawText("Trace · x = 4 -> y = 12", 24f, 44f, titlePaint)
                    canvas.drawText("Output: 12", 24f, 72f, bodyPaint)
                }
                snapshot.trackKey == "angular" -> {
                    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#192922")
                    }
                    canvas.drawRoundRect(RectF(14f, 14f, width - 14f, height - 14f), 12f, 12f, cardPaint)
                    canvas.drawText("Cinema App · Signal Live", 24f, 44f, titlePaint)
                    canvas.drawText("UI updates instantly on click", 24f, 72f, bodyPaint)
                }
                snapshot.trackKey == "js" -> {
                    val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#2BB07F")
                    }
                    canvas.drawRoundRect(RectF(16f, 22f, width - 16f, 58f), 12f, 12f, btnPaint)
                    val darkText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0B110E")
                        textSize = 14f
                        isFakeBoldText = true
                    }
                    canvas.drawText("تفاعل فوري بالمنطق", 68f, 45f, darkText)
                    canvas.drawText("if / event -> نتيجة حية", 54f, 84f, titlePaint)
                }
                else -> {
                    // Glass / frosted emerald card outcome preview
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#193D30")
                    }
                    canvas.drawRoundRect(RectF(14f, 12f, width - 14f, height - 12f), 14f, 14f, glowPaint)
                    val glassBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                        color = Color.parseColor("#52D1A0")
                    }
                    canvas.drawRoundRect(RectF(24f, 20f, width - 24f, height - 20f), 12f, 12f, glassBorder)
                    canvas.drawText("بطاقة زجاجية واضحة", 56f, 48f, titlePaint)
                    canvas.drawText("rgba(43, 176, 127, 0.22)", 54f, 72f, bodyPaint)
                }
            }
            return bmp
        }
    }
}
