package be.webatvantage.gitguard

import be.webatvantage.MyMessageBundle
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
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

internal class GitGuardCloseListener(private val targetProject: Project) : VetoableProjectManagerListener {

    override fun canClose(project: Project): Boolean {
        if (project !== targetProject) return true

        val hasUncommitted = ChangeListManager.getInstance(project).allChanges.isNotEmpty()
        val hasUnpushed = hasUnpushedCommits(project)
        if (!hasUncommitted && !hasUnpushed) return true

        val messageKey = when {
            hasUncommitted && hasUnpushed -> "gitguard.dialog.message.both"
            hasUncommitted -> "gitguard.dialog.message.uncommitted"
            else -> "gitguard.dialog.message.unpushed"
        }

        val closeText = MyMessageBundle.message("gitguard.dialog.closeAnyway")
        val cancelText = MyMessageBundle.message("gitguard.dialog.cancel")
        val commitText = MyMessageBundle.message("gitguard.dialog.commit")
        val pushText = MyMessageBundle.message("gitguard.dialog.push")

        val options = buildList {
            add(closeText)
            add(cancelText)
            if (hasUncommitted) add(commitText)
            if (hasUnpushed) add(pushText)
        }

        ProjectUtil.focusProjectWindow(project, true)
        val parent = WindowManager.getInstance().suggestParentWindow(project)
        val message = MyMessageBundle.message(messageKey)
        val title = MyMessageBundle.message("gitguard.dialog.title")
        val optionsArray = options.toTypedArray()
        val defaultIndex = options.indexOf(cancelText)
        val icon = Messages.getWarningIcon()

        val choiceIndex = if (parent != null) {
            Messages.showDialog(parent, message, title, optionsArray, defaultIndex, icon)
        } else {
            Messages.showDialog(project, message, title, optionsArray, defaultIndex, icon)
        }

        return when (options.getOrNull(choiceIndex)) {
            closeText -> true
            commitText -> {
                invokeActionLater(project, "CheckinProject")
                false
            }
            pushText -> {
                invokeActionLater(project, "Vcs.Push")
                false
            }
            else -> false
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
        if (repo.remotes.isEmpty()) return false
        val localBranch = repo.currentBranch ?: return false
        val trackInfo = repo.getBranchTrackInfo(localBranch.name)
            ?: return repo.branches.getHash(localBranch) != null
        val localHash = repo.branches.getHash(localBranch) ?: return false
        val remoteHash = repo.branches.getHash(trackInfo.remoteBranch) ?: return false
        return localHash != remoteHash
    }
}