package be.tomsix.gitguard

import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

/**
 * Shared mutable state tracking whether the IDE itself is in the middle of an
 * exit/quit sequence.
 *
 * The flag is set by [GitGuardAppLifecycleListener] when the platform fires
 * [com.intellij.ide.AppLifecycleListener.appWillBeClosed], which happens before
 * the per-project `canClose` iteration. A short auto-clear keeps the flag from
 * sticking on if another listener vetoes the quit.
 */
internal object GitGuardAppState {

    @Volatile
    var quitInProgress: Boolean = false
        private set

    fun markQuitting() {
        quitInProgress = true
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            { quitInProgress = false },
            5, TimeUnit.SECONDS,
        )
    }
}
