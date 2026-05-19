package be.tomsix.gitguard

import be.tomsix.gitguard.GitGuardDialog.Choice
import be.tomsix.gitguard.GitGuardDialog.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitGuardDialogTest {

    // --- availableChoices ---

    @Test
    fun `close anyway and cancel are always available`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, multipleProjectsOpen = false)
        )
        assertTrue(Choice.CLOSE_ANYWAY in choices)
        assertTrue(Choice.CANCEL in choices)
    }

    @Test
    fun `close all is hidden when only one project is open`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, multipleProjectsOpen = false)
        )
        assertFalse(Choice.CLOSE_ALL in choices)
    }

    @Test
    fun `close all is shown when multiple projects are open`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, multipleProjectsOpen = true)
        )
        assertTrue(Choice.CLOSE_ALL in choices)
    }

    @Test
    fun `commit button appears only when there are uncommitted changes`() {
        val withUncommitted = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, multipleProjectsOpen = false)
        )
        val withoutUncommitted = GitGuardDialog.availableChoices(
            State(hasUncommitted = false, hasUnpushed = true, multipleProjectsOpen = false)
        )
        assertTrue(Choice.COMMIT in withUncommitted)
        assertFalse(Choice.COMMIT in withoutUncommitted)
    }

    @Test
    fun `push button appears only when there are unpushed commits`() {
        val withUnpushed = GitGuardDialog.availableChoices(
            State(hasUncommitted = false, hasUnpushed = true, multipleProjectsOpen = false)
        )
        val withoutUnpushed = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, multipleProjectsOpen = false)
        )
        assertTrue(Choice.PUSH in withUnpushed)
        assertFalse(Choice.PUSH in withoutUnpushed)
    }

    @Test
    fun `both action buttons appear when both conditions are true`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = true, multipleProjectsOpen = false)
        )
        assertTrue(Choice.COMMIT in choices)
        assertTrue(Choice.PUSH in choices)
    }

    @Test
    fun `option ordering puts close-anyway first, then close-all, then cancel`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = true, multipleProjectsOpen = true)
        )
        assertEquals(Choice.CLOSE_ANYWAY, choices[0])
        assertEquals(Choice.CLOSE_ALL, choices[1])
        assertEquals(Choice.CANCEL, choices[2])
    }

    @Test
    fun `all five choices are present when every condition is true`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = true, multipleProjectsOpen = true)
        )
        assertEquals(5, choices.size)
        assertEquals(setOf(Choice.CLOSE_ANYWAY, Choice.CLOSE_ALL, Choice.CANCEL, Choice.COMMIT, Choice.PUSH), choices.toSet())
    }

    // --- messageKey ---

    @Test
    fun `messageKey picks 'both' when both conditions are true`() {
        val key = GitGuardDialog.messageKey(
            State(hasUncommitted = true, hasUnpushed = true, multipleProjectsOpen = false)
        )
        assertEquals("gitguard.dialog.message.both", key)
    }

    @Test
    fun `messageKey picks 'uncommitted' for uncommitted-only state`() {
        val key = GitGuardDialog.messageKey(
            State(hasUncommitted = true, hasUnpushed = false, multipleProjectsOpen = false)
        )
        assertEquals("gitguard.dialog.message.uncommitted", key)
    }

    @Test
    fun `messageKey picks 'unpushed' for unpushed-only state`() {
        val key = GitGuardDialog.messageKey(
            State(hasUncommitted = false, hasUnpushed = true, multipleProjectsOpen = false)
        )
        assertEquals("gitguard.dialog.message.unpushed", key)
    }

    // --- decideOutgoing ---

    @Test
    fun `decideOutgoing returns false when the repo has no remotes`() {
        val result = GitGuardDialog.decideOutgoing(
            hasRemotes = false,
            hasCurrentBranch = true,
            upstreamConfigured = true,
            localHash = "abc",
            remoteHash = "abc",
        )
        assertFalse(result)
    }

    @Test
    fun `decideOutgoing returns false when there is no current branch`() {
        val result = GitGuardDialog.decideOutgoing(
            hasRemotes = true,
            hasCurrentBranch = false,
            upstreamConfigured = false,
            localHash = null,
            remoteHash = null,
        )
        assertFalse(result)
    }

    @Test
    fun `decideOutgoing returns true when branch has no upstream but has commits`() {
        val result = GitGuardDialog.decideOutgoing(
            hasRemotes = true,
            hasCurrentBranch = true,
            upstreamConfigured = false,
            localHash = "abc",
            remoteHash = null,
        )
        assertTrue(result)
    }

    @Test
    fun `decideOutgoing returns true when local and upstream hashes differ`() {
        val result = GitGuardDialog.decideOutgoing(
            hasRemotes = true,
            hasCurrentBranch = true,
            upstreamConfigured = true,
            localHash = "abc",
            remoteHash = "def",
        )
        assertTrue(result)
    }

    @Test
    fun `decideOutgoing returns false when local and upstream hashes match`() {
        val result = GitGuardDialog.decideOutgoing(
            hasRemotes = true,
            hasCurrentBranch = true,
            upstreamConfigured = true,
            localHash = "abc",
            remoteHash = "abc",
        )
        assertFalse(result)
    }
}