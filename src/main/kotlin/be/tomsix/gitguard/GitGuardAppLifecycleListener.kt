package be.tomsix.gitguard

import com.intellij.ide.AppLifecycleListener

internal class GitGuardAppLifecycleListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
        GitGuardAppState.markQuitting()
    }
}
