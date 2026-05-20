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
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.TimeUnit

internal class GitGuardCloseListener(private val targetProject: Project) : VetoableProjectManagerListener {

    override fun canClose(project: Project): Boolean {
        if (skipAllChecks) return true
        if (project !== targetProject) return true

        val state = GitGuardDialog.State(
            hasUncommitted = ChangeListManager.getInstance(project).allChanges.isNotEmpty(),
            hasUnpushed = hasUnpushedCommits(project),
            quitInProgress = GitGuardAppState.quitInProgress,
        )
        if (!state.hasUncommitted && !state.hasUnpushed) return true

        val choices = GitGuardDialog.availableChoices(state)
        val labels = choices.map(GitGuardDialog::labelOf).toTypedArray()
        val defaultIndex = choices.indexOf(GitGuardDialog.Choice.CANCEL)

        ProjectUtil.focusProjectWindow(project, true)
        val parent = WindowManager.getInstance().suggestParentWindow(project)
        val message = MyMessageBundle.message(GitGuardDialog.messageKey(state))
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

    private fun hasUnpushedCommits(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.any(::repoHasOutgoing)
    }

    private fun repoHasOutgoing(repo: GitRepository): Boolean {
        val branch = repo.currentBranch
        val trackInfo = branch?.let { repo.getBranchTrackInfo(it.name) }
        return GitGuardDialog.decideOutgoing(
            hasRemotes = repo.remotes.isNotEmpty(),
            hasCurrentBranch = branch != null,
            upstreamConfigured = trackInfo != null,
            localHash = branch?.let { repo.branches.getHash(it)?.asString() },
            remoteHash = trackInfo?.remoteBranch?.let { repo.branches.getHash(it)?.asString() },
        )
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