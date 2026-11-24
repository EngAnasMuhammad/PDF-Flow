package com.anasdev.pdfreader.presentation.pdf

import com.anasdev.pdfreader.domain.model.NormalizedPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StrokeSnapHelperTest {

    private val density = 2.0f // 2x screen density
    private val testScope = CoroutineScope(Dispatchers.Default)

    @Test
    fun `isLineValid returns true for straight lines exceeding 24dp`() {
        val helper = StrokeSnapHelper(density, testScope)
        helper.onStrokeStart(100f, 100f) {}

        // Move 100px (~50dp > 24dp) in a straight line
        helper.onStrokeMove(150f, 100f) {}
        helper.onStrokeMove(200f, 100f) {}

        // directDistance = 100px, totalPathLength = 100px -> linearity = 1.0f
        assertTrue(helper.isLineValid())
        helper.dispose()
    }

    @Test
    fun `isLineValid returns false for short strokes under 24dp`() {
        val helper = StrokeSnapHelper(density, testScope)
        helper.onStrokeStart(100f, 100f) {}

        // Move 20px (10dp < 24dp)
        helper.onStrokeMove(120f, 100f) {}

        assertFalse(helper.isLineValid())
        helper.dispose()
    }

    @Test
    fun `isLineValid returns false for curved or looping strokes`() {
        val helper = StrokeSnapHelper(density, testScope)
        helper.onStrokeStart(100f, 100f) {}

        // Draw a circle/loop: (100,100) -> (150,150) -> (200,100) -> (150,50) -> (110,100)
        helper.onStrokeMove(150f, 150f) {}
        helper.onStrokeMove(200f, 100f) {}
        helper.onStrokeMove(150f, 50f) {}
        helper.onStrokeMove(110f, 100f) {}

        // direct distance is only 10px, while path length is ~280px -> linearity < 0.85
        assertFalse(helper.isLineValid())
        helper.dispose()
    }

    @Test
    fun `magnetic snap snaps to horizontal when within 2 degrees`() {
        val helper = StrokeSnapHelper(density, testScope)
        val start = NormalizedPoint.of(0.1f, 0.5f)
        // End is slightly tilted by ~0.5 degree
        val end = NormalizedPoint.of(0.8f, 0.505f)

        val snapped = helper.applyMagneticAxisSnap(start, end, pageWidth = 1000f, pageHeight = 1000f)

        // y should snap exactly to start.y
        assertEquals(start.y, snapped.y, 1e-6f)
        assertEquals(end.x, snapped.x, 1e-6f)
        helper.dispose()
    }

    @Test
    fun `magnetic snap snaps to horizontal in reverse direction within 2 degrees`() {
        val helper = StrokeSnapHelper(density, testScope)
        val start = NormalizedPoint.of(0.8f, 0.5f)
        val end = NormalizedPoint.of(0.1f, 0.505f)

        val snapped = helper.applyMagneticAxisSnap(start, end, pageWidth = 1000f, pageHeight = 1000f)

        assertEquals(start.y, snapped.y, 1e-6f)
        assertEquals(end.x, snapped.x, 1e-6f)
        helper.dispose()
    }

    @Test
    fun `magnetic snap snaps to vertical when within 2 degrees`() {
        val helper = StrokeSnapHelper(density, testScope)
        val start = NormalizedPoint.of(0.5f, 0.1f)
        // End is slightly tilted by ~0.5 degree
        val end = NormalizedPoint.of(0.505f, 0.8f)

        val snapped = helper.applyMagneticAxisSnap(start, end, pageWidth = 1000f, pageHeight = 1000f)

        // x should snap exactly to start.x
        assertEquals(start.x, snapped.x, 1e-6f)
        assertEquals(end.y, snapped.y, 1e-6f)
        helper.dispose()
    }

    @Test
    fun `magnetic snap preserves arbitrary angles beyond 2 degrees for tilted scans`() {
        val helper = StrokeSnapHelper(density, testScope)
        val start = NormalizedPoint.of(0.1f, 0.1f)
        // 45 degree diagonal
        val end = NormalizedPoint.of(0.6f, 0.6f)

        val snapped = helper.applyMagneticAxisSnap(start, end, pageWidth = 1000f, pageHeight = 1000f)

        assertEquals(end.x, snapped.x, 1e-6f)
        assertEquals(end.y, snapped.y, 1e-6f)
        helper.dispose()
    }

    @Test
    fun `hold timer fires snap callback after stillness delay`() {
        val helper = StrokeSnapHelper(density, testScope)
        val latch = CountDownLatch(1)

        helper.onStrokeStart(100f, 100f) {
            latch.countDown()
        }

        // Draw straight line > 24dp
        helper.onStrokeMove(250f, 100f) {
            latch.countDown()
        }

        // Wait up to 600ms for the 300ms timer
        val triggered = latch.await(600, TimeUnit.MILLISECONDS)
        assertTrue(triggered)
        assertTrue(helper.isSnapped)
        helper.dispose()
    }

    @Test
    fun `cancelling stops hold timer`() {
        val helper = StrokeSnapHelper(density, testScope)
        val latch = CountDownLatch(1)

        helper.onStrokeStart(100f, 100f) {
            latch.countDown()
        }
        helper.onStrokeMove(200f, 100f) {
            latch.countDown()
        }

        // Cancel almost immediately
        Thread.sleep(50)
        helper.cancel()

        // Wait beyond the 300ms delay to verify it does not trigger
        val triggered = latch.await(450, TimeUnit.MILLISECONDS)
        assertFalse(triggered)
        assertFalse(helper.isSnapped)
        helper.dispose()
    }
}
