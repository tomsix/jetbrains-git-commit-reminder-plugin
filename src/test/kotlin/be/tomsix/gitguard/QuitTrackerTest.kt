package be.tomsix.gitguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitTrackerTest {

    private var now = 0L
    private val tracker = QuitTracker(clock = { now }, windowNanos = 10)

    @Test
    fun `not quitting until marked`() {
        assertFalse(tracker.quitInProgress)
        assertFalse(tracker.skipRemainingChecks)
    }

    @Test
    fun `quit state expires after the window`() {
        tracker.markQuitting()
        now = 9
        assertTrue(tracker.quitInProgress)
        now = 10
        assertFalse(tracker.quitInProgress)
    }

    @Test
    fun `extend restarts the window from the last project check`() {
        // Regression: a dialog left open longer than the window used to drop
        // "Close All" from the next project's dialog.
        tracker.markQuitting()
        now = 8
        tracker.extend()
        now = 15
        assertTrue(tracker.quitInProgress)
    }

    @Test
    fun `close all skips remaining checks only while the quit lasts`() {
        tracker.markQuitting()
        tracker.skipRemainingProjects()
        assertTrue(tracker.skipRemainingChecks)
        now = 10
        assertFalse(tracker.skipRemainingChecks)
    }

    @Test
    fun `cancel ends the quit and the skip`() {
        tracker.markQuitting()
        tracker.skipRemainingProjects()
        tracker.cancel()
        assertFalse(tracker.quitInProgress)
        assertFalse(tracker.skipRemainingChecks)
    }

    @Test
    fun `a new quit does not inherit an earlier close all`() {
        tracker.markQuitting()
        tracker.skipRemainingProjects()
        now = 100
        tracker.markQuitting()
        assertFalse(tracker.skipRemainingChecks)
    }
}
