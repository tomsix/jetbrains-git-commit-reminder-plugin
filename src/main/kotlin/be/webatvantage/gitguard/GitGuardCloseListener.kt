package be.webatvantage.gitguard

import be.webatvantage.MyMessageBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.ui.MessageDialogBuilder
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

        ProjectUtil.focusProjectWindow(project, true)
        val parent = WindowManager.getInstance().suggestParentWindow(project)

        return MessageDialogBuilder.yesNo(
            MyMessageBundle.message("gitguard.dialog.title"),
            MyMessageBundle.message(messageKey),
        )
            .yesText(MyMessageBundle.message("gitguard.dialog.closeAnyway"))
            .noText(MyMessageBundle.message("gitguard.dialog.cancel"))
            .icon(Messages.getWarningIcon())
            .ask(parent)
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