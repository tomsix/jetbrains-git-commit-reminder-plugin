package be.webatvantage.gitguard

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity

internal class GitGuardStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ProjectManager.getInstance().addProjectManagerListener(project, GitGuardCloseListener(project))
    }
}