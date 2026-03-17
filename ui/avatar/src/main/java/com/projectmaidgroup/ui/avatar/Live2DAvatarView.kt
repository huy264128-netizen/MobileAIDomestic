package com.projectmaidgroup.ui.avatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class Live2DAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 24, 28)
    }

    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 140, 255)
    }

    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 220, 140)
        textSize = 34f
    }

    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 120, 120)
        textSize = 34f
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 30f
    }

    private val controller = Live2DAvatarController(context)
    private var spec: Live2DModelSpec? = null
    private var statusText: String = "IDLE"
    private var assetExists: Boolean = false
    private var assetSummary: List<String> = listOf("No model loaded")

    init {
        setOnClickListener {
            statusText = if (statusText == "IDLE") "TAP MOTION" else "IDLE"
            invalidate()
        }
    }

    fun loadModel(spec: Live2DModelSpec) {
        this.spec = spec
        controller.load(spec)
        assetExists = controller.isModelReady()

        val inspectorLines = Live2DAssetInspector.summary(context, spec).lines()
        val jsonLines = controller.describeCurrentModel()

        assetSummary = inspectorLines + listOf("---- JSON ----") + jsonLines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val cx = width / 2f
        val cy = height * 0.28f
        val radius = min(width, height) * 0.18f

        canvas.drawCircle(cx, cy, radius, avatarPaint)

        canvas.drawText("Live2D Example Placeholder", 40f, 70f, titlePaint)
        canvas.drawText("Status: $statusText", 40f, 120f, textPaint)

        val statePaint = if (assetExists) okPaint else warnPaint
        canvas.drawText(
            if (assetExists) "Asset check: OK" else "Asset check: FAILED",
            40f,
            170f,
            statePaint
        )

        var y = height * 0.55f
        for (line in assetSummary.take(10)) {
            canvas.drawText(line, 40f, y, textPaint)
            y += 40f
        }
    }
}