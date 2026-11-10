package be.tomsix.gitguard

import java.util.concurrent.TimeUnit

/**
 * Tracks whether the IDE is in the middle of a quit, and whether the user chose
 * "Close All" during it.
 *
 * [markQuitting] is called from [com.intellij.ide.AppLifecycleListener.appWillBeClosed],
 * before the platform asks each project whether it can close. The platform has no
 * "quit cancelled" callback for vetoes by other plugins, so the quit state expires
 * after a short window. The window is measured from the last project check, not from
 * the start of the quit, so time spent reading a dialog never makes it expire.
 */
internal class QuitTracker(
    private val clock: () -> Long = System::nanoTime,
    private val windowNanos: Long = TimeUnit.SECONDS.toNanos(10),
) {
    @Volatile
    private var deadline: Long? = null

    @Volatile
    private var skipRemaining: Boolean = false

    val quitInProgress: Boolean
        get() = deadline?.let { clock() - it < 0 } ?: false

    /** True when "Close All" was chosen during the current quit. */
    val skipRemainingChecks: Boolean
        get() = skipRemaining && quitInProgress

    fun markQuitting() {
        skipRemaining = false
        extend()
    }

    /** A project check finished during the quit and let it continue: keep the quit state alive. */
    fun extend() {
        deadline = clock() + windowNanos
    }

    fun skipRemainingProjects() {
        skipRemaining = true
        extend()
    }

    /** A project vetoed its close, which aborts the quit. */
    fun cancel() {
        deadline = null
        skipRemaining = false
    }
}

internal val GitGuardAppState = QuitTracker()
