package com.anasdev.pdfreader.presentation.pdf

import com.anasdev.pdfreader.domain.model.NormalizedPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Helper for hold-to-snap straight line detection ("Smart Rule") on Pen and Highlighter strokes:
 * 1. Tracks gesture distance in screen space (zoom-independent thresholds).
 * 2. Enforces a 300ms stillness hold timer within a 6dp jitter radius.
 * 3. Validates linearity (directDistance / totalPathLength >= 0.85 and length >= 24dp).
 * 4. Applies magnetic axis snapping (within ±2.0° of horizontal or vertical in PDF page space).
 * 5. Provides live endpoint adjustment while the finger remains down after snapping.
 */
class StrokeSnapHelper(
    density: Float,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    companion object {
        const val HOLD_DELAY_MS = 300L
        const val STILLNESS_RADIUS_DP = 6f
        const val MIN_STROKE_LENGTH_DP = 24f
        const val LINEARITY_THRESHOLD = 0.85f
        const val MAGNETIC_AXIS_ANGLE_DEG = 2.0f
    }

    private val stillnessRadiusPx = STILLNESS_RADIUS_DP * density
    private val minStrokeLengthPx = MIN_STROKE_LENGTH_DP * density

    var startScreenX = 0f
        private set
    var startScreenY = 0f
        private set
    var currentScreenX = 0f
        private set
    var currentScreenY = 0f
        private set
    private var anchorScreenX = 0f
    private var anchorScreenY = 0f

    var totalPathLength = 0f
        private set

    var isSnapped = false
        private set

    private var holdJob: Job? = null

    /**
     * Resets tracking state and starts the hold timer for a new stroke.
     */
    fun onStrokeStart(screenX: Float, screenY: Float, onSnap: () -> Unit) {
        cancelHoldTimer()
        isSnapped = false
        startScreenX = screenX
        startScreenY = screenY
        currentScreenX = screenX
        currentScreenY = screenY
        anchorScreenX = screenX
        anchorScreenY = screenY
        totalPathLength = 0f

        startHoldTimer(onSnap)
    }

    /**
     * Updates pointer position. If the pointer moves beyond the stillness jitter radius,
     * restarts the 300ms hold timer.
     */
    fun onStrokeMove(screenX: Float, screenY: Float, onSnap: () -> Unit) {
        val segmentDist = hypot(screenX - currentScreenX, screenY - currentScreenY)
        totalPathLength += segmentDist
        currentScreenX = screenX
        currentScreenY = screenY

        if (isSnapped) return

        val distFromAnchor = hypot(screenX - anchorScreenX, screenY - anchorScreenY)
        if (distFromAnchor > stillnessRadiusPx) {
            anchorScreenX = screenX
            anchorScreenY = screenY
            startHoldTimer(onSnap)
        }
    }

    private fun startHoldTimer(onSnap: () -> Unit) {
        cancelHoldTimer()
        holdJob = coroutineScope.launch {
            delay(HOLD_DELAY_MS)
            if (!isSnapped && isLineValid()) {
                isSnapped = true
                onSnap()
            }
        }
    }

    /**
     * Validates whether the drawn gesture represents a deliberate straight line.
     */
    fun isLineValid(): Boolean {
        val directDistance = hypot(currentScreenX - startScreenX, currentScreenY - startScreenY)
        if (directDistance < minStrokeLengthPx) return false
        if (totalPathLength <= 0f) return false
        val linearityRatio = directDistance / totalPathLength
        return linearityRatio >= LINEARITY_THRESHOLD
    }

    /**
     * Evaluates the angle between start and end in PDF page coordinates.
     * Snaps to horizontal (y2 = y1) or vertical (x2 = x1) if within ±2.0°.
     * Otherwise preserves the exact angle for tilted/skewed scanned documents.
     */
    fun applyMagneticAxisSnap(
        startNormPoint: NormalizedPoint,
        rawEndNormPoint: NormalizedPoint,
        pageWidth: Float,
        pageHeight: Float
    ): NormalizedPoint {
        val dx = (rawEndNormPoint.x - startNormPoint.x) * pageWidth
        val dy = (rawEndNormPoint.y - startNormPoint.y) * pageHeight

        if (hypot(dx, dy) < 1e-4f) {
            return rawEndNormPoint
        }

        val angleRad = atan2(dy, dx)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
        val absAngle = abs(angleDeg)

        // Deviation from horizontal axis (0° or 180°)
        val devFromHorizontal = min(absAngle, 180f - absAngle)

        // Deviation from vertical axis (90° or -90°)
        val devFromVertical = abs(absAngle - 90f)

        return when {
            devFromHorizontal <= MAGNETIC_AXIS_ANGLE_DEG -> {
                NormalizedPoint.of(rawEndNormPoint.x, startNormPoint.y)
            }
            devFromVertical <= MAGNETIC_AXIS_ANGLE_DEG -> {
                NormalizedPoint.of(startNormPoint.x, rawEndNormPoint.y)
            }
            else -> rawEndNormPoint
        }
    }

    fun cancelHoldTimer() {
        holdJob?.cancel()
        holdJob = null
    }

    fun cancel() {
        cancelHoldTimer()
        isSnapped = false
    }

    fun dispose() {
        cancel()
        coroutineScope.cancel()
    }
}
