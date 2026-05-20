package be.tomsix.gitguard

import be.tomsix.MyMessageBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.GitLocalBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitBranchTrackInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.TimeUnit

internal class GitGuardCloseListener(private val targetProject: Project) : VetoableProjectManagerListener {

    override fun canClose(project: Project): Boolean {
        if (skipAllChecks) return true
        if (project !== targetProject) return true

        val outgoing = GitRepositoryManager.getInstance(project).repositories
            .mapNotNull(::resolveOutgoingInfo)
        val state = GitGuardDialog.State(
            hasUncommitted = ChangeListManager.getInstance(project).allChanges.isNotEmpty(),
            hasUnpushed = outgoing.isNotEmpty(),
            quitInProgress = GitGuardAppState.quitInProgress,
            unpushedDetails = outgoing,
        )
        if (!state.hasUncommitted && !state.hasUnpushed) return true

        val choices = GitGuardDialog.availableChoices(state)
        val labels = choices.map(GitGuardDialog::labelOf).toTypedArray()
        val defaultIndex = choices.indexOf(GitGuardDialog.Choice.CANCEL)

        ProjectUtil.focusProjectWindow(project, true)
        val parent = WindowManager.getInstance().suggestParentWindow(project)
        val message = GitGuardDialog.renderMessage(state) { key, args ->
            MyMessageBundle.message(key, *args.toTypedArray())
        }
        val title = MyMessageBundle.message("gitguard.dialog.title")
        val icon = Messages.getWarningIcon()

        val choiceIndex = if (parent != null) {
            Messages.showDialog(parent, message, title, labels, defaultIndex, icon)
        } else {
            Messages.showDialog(project, message, title, labels, defaultIndex, icon)
        }

        return when (choices.getOrNull(choiceIndex)) {
            GitGuardDialog.Choice.CLOSE_ANYWAY -> true
            GitGuardDialog.Choice.CLOSE_ALL -> {
                enableSkipAll()
                ApplicationManager.getApplication().invokeLater { clearSkipAll() }
                true
            }
            GitGuardDialog.Choice.COMMIT -> {
                invokeActionLater(project, "CheckinProject")
                false
            }
            GitGuardDialog.Choice.PUSH -> {
                invokeActionLater(project, "Vcs.Push")
                false
            }
            GitGuardDialog.Choice.CANCEL, null -> false
        }
    }

    private fun invokeActionLater(project: Project, actionId: String) {
        ApplicationManager.getApplication().invokeLater({
            val action = ActionManager.getInstance().getAction(actionId) ?: return@invokeLater
            val context = SimpleDataContext.getProjectContext(project)
            val event = AnActionEvent.createEvent(context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
            ActionUtil.performAction(action, event)
        }, project.disposed)
    }

    private fun resolveOutgoingInfo(repo: GitRepository): GitGuardDialog.OutgoingInfo? {
        val branch = repo.currentBranch
        val trackInfo = branch?.let { repo.getBranchTrackInfo(it.name) }
        val localHash = branch?.let { repo.branches.getHash(it)?.asString() }
        val (remoteRef, remoteHash) = resolveRemote(repo, branch, trackInfo)

        val isOutgoing = GitGuardDialog.decideOutgoing(
            hasRemotes = repo.remotes.isNotEmpty(),
            hasCurrentBranch = branch != null,
            localHash = localHash,
            remoteHash = remoteHash,
        )
        if (!isOutgoing) return null

        val count = if (remoteHash != null && localHash != null) {
            countCommitsAhead(repo, remoteHash, localHash)
        } else 0
        return GitGuardDialog.OutgoingInfo(
            branchName = branch!!.name,
            remoteBranchName = remoteRef,
            commitCount = count,
        )
    }

    // Prefer the configured upstream; fall back to a same-named remote branch
    // (e.g. local `main` ↔ `origin/main`) so a branch that's been pushed without
    // `git push -u` isn't misreported as having unpushed commits. Returns the
    // remote branch name (for display) alongside its tip hash.
    private fun resolveRemote(
        repo: GitRepository,
        branch: GitLocalBranch?,
        trackInfo: GitBranchTrackInfo?,
    ): Pair<String?, String?> {
        if (branch == null) return null to null
        trackInfo?.remoteBranch?.let { return it.name to repo.branches.getHash(it)?.asString() }
        val match = repo.branches.remoteBranches.firstOrNull {
            it.nameForRemoteOperations == branch.name
        } ?: return null to null
        return match.name to repo.branches.getHash(match)?.asString()
    }

    private fun countCommitsAhead(repo: GitRepository, remoteHash: String, localHash: String): Int {
        val handler = GitLineHandler(repo.project, repo.root, GitCommand.REV_LIST)
        handler.addParameters("--count", "$remoteHash..$localHash")
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) return 0
        return result.outputAsJoinedString.trim().toIntOrNull() ?: 0
    }

    companion object {
        @Volatile
        private var skipAllChecks: Boolean = false

        // Shared across all project instances: set by "Close All", cleared once the
        // batch close finishes. The 30s reschedule is a safety net for the case
        // where the batch never completes (e.g. another listener vetoes a close).
        private fun enableSkipAll() {
            skipAllChecks = true
            AppExecutorUtil.getAppScheduledExecutorService().schedule(
                { skipAllChecks = false },
                30, TimeUnit.SECONDS,
            )
        }

        private fun clearSkipAll() {
            skipAllChecks = false
        }
    }
}