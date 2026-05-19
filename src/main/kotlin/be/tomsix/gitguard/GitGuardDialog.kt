package be.tomsix.gitguard

import be.tomsix.MyMessageBundle

internal object GitGuardDialog {

    enum class Choice {
        CLOSE_ANYWAY,
        CLOSE_ALL,
        CANCEL,
        COMMIT,
        PUSH,
    }

    data class State(
        val hasUncommitted: Boolean,
        val hasUnpushed: Boolean,
        val multipleProjectsOpen: Boolean,
    )

    fun availableChoices(state: State): List<Choice> = buildList {
        add(Choice.CLOSE_ANYWAY)
        if (state.multipleProjectsOpen) add(Choice.CLOSE_ALL)
        add(Choice.CANCEL)
        if (state.hasUncommitted) add(Choice.COMMIT)
        if (state.hasUnpushed) add(Choice.PUSH)
    }

    fun messageKey(state: State): String = when {
        state.hasUncommitted && state.hasUnpushed -> "gitguard.dialog.message.both"
        state.hasUncommitted -> "gitguard.dialog.message.uncommitted"
        else -> "gitguard.dialog.message.unpushed"
    }

    fun decideOutgoing(
        hasRemotes: Boolean,
        hasCurrentBranch: Boolean,
        upstreamConfigured: Boolean,
        localHash: String?,
        remoteHash: String?,
    ): Boolean {
        if (!hasRemotes) return false
        if (!hasCurrentBranch) return false
        if (!upstreamConfigured) return localHash != null
        return localHash != null && remoteHash != null && localHash != remoteHash
    }

    fun labelOf(choice: Choice): String = when (choice) {
        Choice.CLOSE_ANYWAY -> MyMessageBundle.message("gitguard.dialog.closeAnyway")
        Choice.CLOSE_ALL -> MyMessageBundle.message("gitguard.dialog.closeAll")
        Choice.CANCEL -> MyMessageBundle.message("gitguard.dialog.cancel")
        Choice.COMMIT -> MyMessageBundle.message("gitguard.dialog.commit")
        Choice.PUSH -> MyMessageBundle.message("gitguard.dialog.push")
    }
}