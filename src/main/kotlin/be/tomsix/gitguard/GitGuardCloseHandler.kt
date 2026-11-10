package be.tomsix.gitguard

import be.tomsix.GitGuardBundle
import be.tomsix.gitguard.GitGuardDialog.Comparison
import be.tomsix.gitguard.GitGuardDialog.RemoteRef
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseHandler
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<GitGuardCloseHandler>()

// Registered as a `projectCloseHandler` extension, so it is active from the moment
// the plugin is loaded (no startup activity race) and is removed when the plugin is
// unloaded.
internal class GitGuardCloseHandler : ProjectCloseHandler {

    override fun canClose(project: Project): Boolean {
        if (project.isDefault) return true
        val quitting = GitGuardAppState.quitInProgress
        if (GitGuardAppState.skipRemainingChecks) {
            GitGuardAppState.extend()
            return true
        }

        val outgoing = findOutgoing(project)
        val state = GitGuardDialog.State(
            hasUncommitted = ChangeListManager.getInstance(project).allChanges.isNotEmpty(),
            hasUnpushed = outgoing.isNotEmpty(),
            quitInProgress = quitting,
            unpushedDetails = outgoing,
        )
        if (!state.hasUncommitted && !state.hasUnpushed) {
            if (quitting) GitGuardAppState.extend()
            return true
        }

        val choice = showDialog(project, state)
        return when (choice) {
            GitGuardDialog.Choice.CLOSE_ANYWAY -> {
                if (quitting) GitGuardAppState.extend()
                true
            }
            GitGuardDialog.Choice.CLOSE_ALL -> {
                GitGuardAppState.skipRemainingProjects()
                true
            }
            GitGuardDialog.Choice.COMMIT -> {
                GitGuardAppState.cancel()
                invokeActionLater(project, "CheckinProject")
                false
            }
            GitGuardDialog.Choice.PUSH -> {
                GitGuardAppState.cancel()
                invokeActionLater(project, "Vcs.Push")
                false
            }
            GitGuardDialog.Choice.CANCEL, null -> {
                GitGuardAppState.cancel()
                false
            }
        }
    }

    private fun showDialog(project: Project, state: GitGuardDialog.State): GitGuardDialog.Choice? {
        val choices = GitGuardDialog.availableChoices(state)
        val labels = choices.map(GitGuardDialog::labelOf).toTypedArray()
        val defaultIndex = choices.indexOf(GitGuardDialog.Choice.CANCEL)

        ProjectUtil.focusProjectWindow(project, true)
        val parent = WindowManager.getInstance().suggestParentWindow(project)
        val message = GitGuardDialog.renderMessage(state) { key, args ->
            GitGuardBundle.message(key, *args.toTypedArray())
        }
        val title = GitGuardBundle.message("gitguard.dialog.title")
        val icon = Messages.getWarningIcon()

        val index = if (parent != null) {
            Messages.showDialog(parent, message, title, labels, defaultIndex, icon)
        } else {
            Messages.showDialog(project, message, title, labels, defaultIndex, icon)
        }
        return choices.getOrNull(index)
    }

    private fun invokeActionLater(project: Project, actionId: String) {
        ApplicationManager.getApplication().invokeLater({
            val action = ActionManager.getInstance().getAction(actionId) ?: return@invokeLater
            val context = SimpleDataContext.getProjectContext(project)
            val event = AnActionEvent.createEvent(context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
            ActionUtil.performAction(action, event)
        }, project.disposed)
    }

    // Branch and hash lookups read Git4Idea's cached repository state and are cheap.
    // Only repositories whose local branch differs from every remote candidate need a
    // `git rev-list`, and those run off the UI thread behind a modal progress dialog.
    private fun findOutgoing(project: Project): List<GitGuardDialog.OutgoingInfo> {
        val comparisons = GitRepositoryManager.getInstance(project).repositories.map { repo -> repo to compare(repo) }
        val toCount = comparisons.filter { it.second is Comparison.NeedsCount }
        val counts: Map<GitRepository, Int?> = if (toCount.isEmpty()) emptyMap() else countAhead(project, toCount)
        return comparisons.mapNotNull { (repo, comparison) -> GitGuardDialog.outgoingFor(comparison, counts[repo]) }
    }

    private fun compare(repo: GitRepository): Comparison {
        val branch = repo.currentBranch
        val branches = repo.branches
        val upstream = branch
            ?.let { repo.getBranchTrackInfo(it.name)?.remoteBranch }
            ?.let { RemoteRef(it.remote.name, it.name, branches.getHash(it)?.asString()) }
        val sameNamed = if (branch == null) emptyList() else branches.remoteBranches
            .filter { it.nameForRemoteOperations == branch.name }
            .map { RemoteRef(it.remote.name, it.name, branches.getHash(it)?.asString()) }
        return GitGuardDialog.compare(
            hasRemotes = repo.remotes.isNotEmpty(),
            branchName = branch?.name,
            localHash = branch?.let { branches.getHash(it)?.asString() },
            candidates = GitGuardDialog.orderCandidates(upstream, sameNamed),
        )
    }

    private fun countAhead(
        project: Project,
        toCount: List<Pair<GitRepository, Comparison>>,
    ): Map<GitRepository, Int?> {
        val compute = {
            toCount.associate { (repo, comparison) ->
                val needsCount = comparison as Comparison.NeedsCount
                repo to countCommitsAhead(repo, needsCount.remote.hash!!, needsCount.localHash)
            }
        }
        // canClose is normally called on the UI thread; keep it responsive while git runs.
        if (!ApplicationManager.getApplication().isDispatchThread) return compute()
        return runWithModalProgressBlocking(
            ModalTaskOwner.project(project),
            GitGuardBundle.message("gitguard.progress.checking"),
            TaskCancellation.nonCancellable(),
        ) { withContext(Dispatchers.IO) { compute() } }
    }

    private fun countCommitsAhead(repo: GitRepository, remoteHash: String, localHash: String): Int? {
        val handler = GitLineHandler(repo.project, repo.root, GitCommand.REV_LIST)
        handler.addParameters("--count", "$remoteHash..$localHash")
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            LOG.warn("git rev-list --count failed in ${repo.root.path}: ${result.errorOutputAsJoinedString}")
            return null
        }
        return result.outputAsJoinedString.trim().toIntOrNull()
    }
}
