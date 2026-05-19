package be.webatvantage.gitguard

import be.webatvantage.MyMessageBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
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

        val answer = Messages.showYesNoDialog(
            project,
            MyMessageBundle.message(messageKey),
            MyMessageBundle.message("gitguard.dialog.title"),
            MyMessageBundle.message("gitguard.dialog.closeAnyway"),
            MyMessageBundle.message("gitguard.dialog.cancel"),
            Messages.getWarningIcon(),
        )
        return answer == Messages.YES
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