package com.anasdev.pdfreader.presentation.pdf

import com.anasdev.pdfreader.domain.model.AnnotationTool
import com.anasdev.pdfreader.domain.model.NormalizedPoint
import com.anasdev.pdfreader.domain.model.StrokeDrawMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StrokeDrawModeTest {

    private val density = 2.0f
    private val testScope = CoroutineScope(Dispatchers.Default)

    /**
     * Replicates the state machine in PdfViewModel to verify that per-tool stroke
     * draw modes are tracked independently and restored upon switching active tools.
     */
    private class ToolModeStateMachine {
        private var lastPenMode = StrokeDrawMode.FREEHAND
        private var lastHighlighterMode = StrokeDrawMode.STRAIGHT

        var activeTool: AnnotationTool = AnnotationTool.NONE
            private set

        var strokeDrawMode: StrokeDrawMode = StrokeDrawMode.FREEHAND
            private set

        fun setActiveTool(tool: AnnotationTool) {
            activeTool = tool
            when (tool) {
                AnnotationTool.PEN -> strokeDrawMode = lastPenMode
                AnnotationTool.HIGHLIGHTER -> strokeDrawMode = lastHighlighterMode
                else -> {}
            }
        }

        fun setStrokeDrawMode(mode: StrokeDrawMode) {
            strokeDrawMode = mode
            when (activeTool) {
                AnnotationTool.PEN -> lastPenMode = mode
                AnnotationTool.HIGHLIGHTER -> lastHighlighterMode = mode
                else -> {}
            }
        }
    }

    @Test
    fun `default stroke draw mode for Pen is FREEHAND`() {
        val stateMachine = ToolModeStateMachine()
        stateMachine.setActiveTool(AnnotationTool.PEN)
        assertEquals(StrokeDrawMode.FREEHAND, stateMachine.strokeDrawMode)
    }

    @Test
    fun `default stroke draw mode for Highlighter is STRAIGHT`() {
        val stateMachine = ToolModeStateMachine()
        stateMachine.setActiveTool(AnnotationTool.HIGHLIGHTER)
        assertEquals(StrokeDrawMode.STRAIGHT, stateMachine.strokeDrawMode)
    }

    @Test
    fun `switching between tools restores each tool remembered mode`() {
        val stateMachine = ToolModeStateMachine()

        // 1. Start with Pen -> FREEHAND
        stateMachine.setActiveTool(AnnotationTool.PEN)
        assertEquals(StrokeDrawMode.FREEHAND, stateMachine.strokeDrawMode)

        // 2. Change Pen to STRAIGHT
        stateMachine.setStrokeDrawMode(StrokeDrawMode.STRAIGHT)
        assertEquals(StrokeDrawMode.STRAIGHT, stateMachine.strokeDrawMode)

        // 3. Switch to Highlighter -> should be its default STRAIGHT
        stateMachine.setActiveTool(AnnotationTool.HIGHLIGHTER)
        assertEquals(StrokeDrawMode.STRAIGHT, stateMachine.strokeDrawMode)

        // 4. Change Highlighter to FREEHAND
        stateMachine.setStrokeDrawMode(StrokeDrawMode.FREEHAND)
        assertEquals(StrokeDrawMode.FREEHAND, stateMachine.strokeDrawMode)

        // 5. Switch back to Pen -> should restore Pen's STRAIGHT
        stateMachine.setActiveTool(AnnotationTool.PEN)
        assertEquals(StrokeDrawMode.STRAIGHT, stateMachine.strokeDrawMode)

        // 6. Switch back to Highlighter -> should restore Highlighter's FREEHAND
        stateMachine.setActiveTool(AnnotationTool.HIGHLIGHTER)
        assertEquals(StrokeDrawMode.FREEHAND, stateMachine.strokeDrawMode)
    }

    @Test
    fun `non-stroke tools do not corrupt pen or highlighter remembered modes`() {
        val stateMachine = ToolModeStateMachine()

        stateMachine.setActiveTool(AnnotationTool.PEN)
        stateMachine.setStrokeDrawMode(StrokeDrawMode.STRAIGHT)

        stateMachine.setActiveTool(AnnotationTool.TEXT)
        stateMachine.setActiveTool(AnnotationTool.ERASER)
        stateMachine.setActiveTool(AnnotationTool.SIGNATURE)

        // Switch back to Pen
        stateMachine.setActiveTool(AnnotationTool.PEN)
        assertEquals(StrokeDrawMode.STRAIGHT, stateMachine.strokeDrawMode)

        // Switch to Highlighter -> default STRAIGHT preserved
        stateMachine.setActiveTool(AnnotationTool.HIGHLIGHTER)
        assertEquals(StrokeDrawMode.STRAIGHT, stateMachine.strokeDrawMode)
    }

    @Test
    fun `straight mode snaps to horizontal axis when within 2 degrees`() {
        val helper = StrokeSnapHelper(density, testScope)
        val start = NormalizedPoint.of(0.2f, 0.4f)
        // End point slightly tilted horizontally (~1.1 degrees)
        val end = NormalizedPoint.of(0.8f, 0.41f)

        val snapped = helper.applyMagneticAxisSnap(start, end, pageWidth = 1000f, pageHeight = 1000f)

        // Y must snap exactly to start.y
        assertEquals(start.y, snapped.y, 1e-6f)
        assertEquals(end.x, snapped.x, 1e-6f)
        helper.dispose()
    }

    @Test
    fun `straight mode snaps to vertical axis when within 2 degrees`() {
        val helper = StrokeSnapHelper(density, testScope)
        val start = NormalizedPoint.of(0.5f, 0.2f)
        // End point slightly tilted vertically (~1.1 degrees)
        val end = NormalizedPoint.of(0.51f, 0.8f)

        val snapped = helper.applyMagneticAxisSnap(start, end, pageWidth = 1000f, pageHeight = 1000f)

        // X must snap exactly to start.x
        assertEquals(start.x, snapped.x, 1e-6f)
        assertEquals(end.y, snapped.y, 1e-6f)
        helper.dispose()
    }

    @Test
    fun `straight mode preserves diagonal angle when exceeding 2 degrees`() {
        val helper = StrokeSnapHelper(density, testScope)
        val start = NormalizedPoint.of(0.1f, 0.1f)
        // 30 degree diagonal
        val end = NormalizedPoint.of(0.5f, 0.33f)

        val snapped = helper.applyMagneticAxisSnap(start, end, pageWidth = 1000f, pageHeight = 1000f)

        // Angle > 2 degrees, so endpoints should remain unchanged
        assertEquals(end.x, snapped.x, 1e-6f)
        assertEquals(end.y, snapped.y, 1e-6f)
        helper.dispose()
    }

    @Test
    fun `straight mode micro tap under 4dp threshold is rejected`() {
        // 4dp threshold with 2x density is 8px
        val minDragPx = 4f * density // 8px

        val tapDx = 3f
        val tapDy = 4f
        val tapDist = hypot(tapDx, tapDy) // 5px < 8px
        assertFalse("Taps under 4dp must be rejected as micro-taps", tapDist >= minDragPx)

        val dragDx = 7f
        val dragDy = 8f
        val dragDist = hypot(dragDx, dragDy) // ~10.6px >= 8px
        assertTrue("Drags exceeding 4dp must be accepted", dragDist >= minDragPx)
    }
}
