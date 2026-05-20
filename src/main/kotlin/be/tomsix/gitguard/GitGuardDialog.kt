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

    data class OutgoingInfo(
        val branchName: String,
        // null when no matching remote branch was found (branch never pushed).
        val remoteBranchName: String?,
        // Commits ahead of remoteBranchName. 0 when remoteBranchName is null.
        val commitCount: Int,
    )

    data class State(
        val hasUncommitted: Boolean,
        val hasUnpushed: Boolean,
        val quitInProgress: Boolean,
        val unpushedDetails: List<OutgoingInfo> = emptyList(),
    )

    fun availableChoices(state: State): List<Choice> = buildList {
        add(Choice.CLOSE_ANYWAY)
        if (state.quitInProgress) add(Choice.CLOSE_ALL)
        add(Choice.CANCEL)
        if (state.hasUncommitted) add(Choice.COMMIT)
        if (state.hasUnpushed) add(Choice.PUSH)
    }

    fun messageKey(state: State): String = when {
        state.hasUncommitted && state.hasUnpushed -> "gitguard.dialog.message.both"
        state.hasUncommitted -> "gitguard.dialog.message.uncommitted"
        else -> "gitguard.dialog.message.unpushed"
    }

    // Assembles the dialog body: lead line, optional per-repo detail bullets, then
    // the "Close the project anyway?" question. `format` looks up a bundle key with
    // its positional args; passing a lambda keeps this function pure & testable
    // without the platform's resource-bundle infrastructure.
    fun renderMessage(state: State, format: (String, List<Any>) -> String): String {
        val sb = StringBuilder()
        sb.append(format(messageKey(state), emptyList()))
        if (state.hasUnpushed) {
            for (info in state.unpushedDetails) {
                sb.append("\n  • ")
                sb.append(format(detailKey(info), detailArgs(info)))
            }
        }
        sb.append("\n\n")
        sb.append(format("gitguard.dialog.message.closeQuestion", emptyList()))
        return sb.toString()
    }

    private fun detailKey(info: OutgoingInfo): String = when {
        info.remoteBranchName == null -> "gitguard.dialog.message.detail.notPushed"
        info.commitCount == 1 -> "gitguard.dialog.message.detail.ahead.one"
        else -> "gitguard.dialog.message.detail.ahead.many"
    }

    private fun detailArgs(info: OutgoingInfo): List<Any> = when {
        info.remoteBranchName == null -> listOf(info.branchName)
        info.commitCount == 1 -> listOf(info.branchName, info.remoteBranchName)
        else -> listOf(info.branchName, info.commitCount, info.remoteBranchName)
    }

    fun decideOutgoing(
        hasRemotes: Boolean,
        hasCurrentBranch: Boolean,
        localHash: String?,
        remoteHash: String?,
    ): Boolean {
        if (!hasRemotes) return false
        if (!hasCurrentBranch) return false
        if (localHash == null) return false
        if (remoteHash == null) return true
        return localHash != remoteHash
    }

    fun labelOf(choice: Choice): String = when (choice) {
        Choice.CLOSE_ANYWAY -> MyMessageBundle.message("gitguard.dialog.closeAnyway")
        Choice.CLOSE_ALL -> MyMessageBundle.message("gitguard.dialog.closeAll")
        Choice.CANCEL -> MyMessageBundle.message("gitguard.dialog.cancel")
        Choice.COMMIT -> MyMessageBundle.message("gitguard.dialog.commit")
        Choice.PUSH -> MyMessageBundle.message("gitguard.dialog.push")
    }
}