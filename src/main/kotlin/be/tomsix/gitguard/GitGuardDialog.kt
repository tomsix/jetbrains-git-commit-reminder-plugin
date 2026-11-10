package be.tomsix.gitguard

import be.tomsix.GitGuardBundle

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
        // Commits ahead of remoteBranchName; null when the count could not be determined.
        val commitCount: Int?,
    )

    data class State(
        val hasUncommitted: Boolean,
        val hasUnpushed: Boolean,
        val quitInProgress: Boolean,
        val unpushedDetails: List<OutgoingInfo> = emptyList(),
    )

    // A remote branch the current local branch could be compared against.
    // `remote` is the remote's name (e.g. `origin`), `name` the full ref name (e.g. `origin/main`).
    data class RemoteRef(val remote: String, val name: String, val hash: String?)

    // What still has to be done to decide whether a repository has unpushed commits.
    sealed interface Comparison {
        // Nothing to report: no remotes, no current branch, or already in sync.
        data object Clean : Comparison

        // The branch has no counterpart on any remote.
        data class NeverPushed(val branchName: String) : Comparison

        // Hashes differ; count the commits in `remote..localHash` to know whether
        // the local branch is ahead (unpushed) or merely behind (nothing to push).
        data class NeedsCount(val branchName: String, val localHash: String, val remote: RemoteRef) : Comparison
    }

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
        info.commitCount == null -> "gitguard.dialog.message.detail.unknown"
        info.commitCount == 1 -> "gitguard.dialog.message.detail.ahead.one"
        else -> "gitguard.dialog.message.detail.ahead.many"
    }

    private fun detailArgs(info: OutgoingInfo): List<Any> = when {
        info.remoteBranchName == null -> listOf(info.branchName)
        info.commitCount == null || info.commitCount == 1 -> listOf(info.branchName, info.remoteBranchName)
        else -> listOf(info.branchName, info.commitCount, info.remoteBranchName)
    }

    // Orders the remote branches to compare against: the configured upstream first,
    // then same-named branches on `origin`, then on any other remote. The first entry
    // is the one reported in the dialog; the others only count as "already pushed"
    // when they point at exactly the local commit (e.g. a fork pushed to `origin`
    // whose `upstream/main` lags behind).
    fun orderCandidates(upstream: RemoteRef?, sameNamed: List<RemoteRef>): List<RemoteRef> {
        val others = sameNamed
            .filter { it.name != upstream?.name }
            .sortedWith(compareBy<RemoteRef>({ it.remote != "origin" }, { it.remote }))
        return listOfNotNull(upstream) + others
    }

    fun compare(
        hasRemotes: Boolean,
        branchName: String?,
        localHash: String?,
        candidates: List<RemoteRef>,
    ): Comparison {
        if (!hasRemotes || branchName == null || localHash == null) return Comparison.Clean
        val known = candidates.filter { it.hash != null }
        if (known.isEmpty()) return Comparison.NeverPushed(branchName)
        if (known.any { it.hash == localHash }) return Comparison.Clean
        return Comparison.NeedsCount(branchName, localHash, known.first())
    }

    // `aheadCount` is the number of local commits missing from the remote branch, or
    // null when it could not be determined. Zero means the local branch is only behind
    // (or equal): nothing to push. An unknown count is reported, to stay on the safe side.
    fun outgoingFor(comparison: Comparison, aheadCount: Int?): OutgoingInfo? = when (comparison) {
        Comparison.Clean -> null
        is Comparison.NeverPushed -> OutgoingInfo(comparison.branchName, null, null)
        is Comparison.NeedsCount ->
            if (aheadCount == 0) null
            else OutgoingInfo(comparison.branchName, comparison.remote.name, aheadCount)
    }

    fun labelOf(choice: Choice): String = when (choice) {
        Choice.CLOSE_ANYWAY -> GitGuardBundle.message("gitguard.dialog.closeAnyway")
        Choice.CLOSE_ALL -> GitGuardBundle.message("gitguard.dialog.closeAll")
        Choice.CANCEL -> GitGuardBundle.message("gitguard.dialog.cancel")
        Choice.COMMIT -> GitGuardBundle.message("gitguard.dialog.commit")
        Choice.PUSH -> GitGuardBundle.message("gitguard.dialog.push")
    }
}
