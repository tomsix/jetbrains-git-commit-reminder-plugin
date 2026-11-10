package be.tomsix.gitguard

import be.tomsix.gitguard.GitGuardDialog.Choice
import be.tomsix.gitguard.GitGuardDialog.Comparison
import be.tomsix.gitguard.GitGuardDialog.RemoteRef
import be.tomsix.gitguard.GitGuardDialog.OutgoingInfo
import be.tomsix.gitguard.GitGuardDialog.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitGuardDialogTest {

    // --- availableChoices ---

    @Test
    fun `close anyway and cancel are always available`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, quitInProgress = false)
        )
        assertTrue(Choice.CLOSE_ANYWAY in choices)
        assertTrue(Choice.CANCEL in choices)
    }

    @Test
    fun `close all is hidden when not quitting the IDE`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, quitInProgress = false)
        )
        assertFalse(Choice.CLOSE_ALL in choices)
    }

    @Test
    fun `close all is hidden during single-project close even with both git states active`() {
        // Reproduces the bug report: user closes one project while others are open.
        // quitInProgress is false because this is not an IDE quit, so CLOSE_ALL
        // must not appear regardless of how many other projects happen to be open.
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = true, quitInProgress = false)
        )
        assertFalse(Choice.CLOSE_ALL in choices)
    }

    @Test
    fun `close all is shown when the IDE is quitting`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, quitInProgress = true)
        )
        assertTrue(Choice.CLOSE_ALL in choices)
    }

    @Test
    fun `close all visibility depends only on quit state, not on git state`() {
        // CLOSE_ALL should appear during any quit, even one with only uncommitted
        // changes or only unpushed commits.
        val uncommittedOnly = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, quitInProgress = true)
        )
        val unpushedOnly = GitGuardDialog.availableChoices(
            State(hasUncommitted = false, hasUnpushed = true, quitInProgress = true)
        )
        assertTrue(Choice.CLOSE_ALL in uncommittedOnly)
        assertTrue(Choice.CLOSE_ALL in unpushedOnly)
    }

    @Test
    fun `commit button appears only when there are uncommitted changes`() {
        val withUncommitted = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, quitInProgress = false)
        )
        val withoutUncommitted = GitGuardDialog.availableChoices(
            State(hasUncommitted = false, hasUnpushed = true, quitInProgress = false)
        )
        assertTrue(Choice.COMMIT in withUncommitted)
        assertFalse(Choice.COMMIT in withoutUncommitted)
    }

    @Test
    fun `push button appears only when there are unpushed commits`() {
        val withUnpushed = GitGuardDialog.availableChoices(
            State(hasUncommitted = false, hasUnpushed = true, quitInProgress = false)
        )
        val withoutUnpushed = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = false, quitInProgress = false)
        )
        assertTrue(Choice.PUSH in withUnpushed)
        assertFalse(Choice.PUSH in withoutUnpushed)
    }

    @Test
    fun `both action buttons appear when both conditions are true`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = true, quitInProgress = false)
        )
        assertTrue(Choice.COMMIT in choices)
        assertTrue(Choice.PUSH in choices)
    }

    @Test
    fun `option ordering puts close-anyway first, then close-all, then cancel`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = true, quitInProgress = true)
        )
        assertEquals(Choice.CLOSE_ANYWAY, choices[0])
        assertEquals(Choice.CLOSE_ALL, choices[1])
        assertEquals(Choice.CANCEL, choices[2])
    }

    @Test
    fun `all five choices are present when every condition is true`() {
        val choices = GitGuardDialog.availableChoices(
            State(hasUncommitted = true, hasUnpushed = true, quitInProgress = true)
        )
        assertEquals(5, choices.size)
        assertEquals(setOf(Choice.CLOSE_ANYWAY, Choice.CLOSE_ALL, Choice.CANCEL, Choice.COMMIT, Choice.PUSH), choices.toSet())
    }

    // --- messageKey ---

    @Test
    fun `messageKey picks 'both' when both conditions are true`() {
        val key = GitGuardDialog.messageKey(
            State(hasUncommitted = true, hasUnpushed = true, quitInProgress = false)
        )
        assertEquals("gitguard.dialog.message.both", key)
    }

    @Test
    fun `messageKey picks 'uncommitted' for uncommitted-only state`() {
        val key = GitGuardDialog.messageKey(
            State(hasUncommitted = true, hasUnpushed = false, quitInProgress = false)
        )
        assertEquals("gitguard.dialog.message.uncommitted", key)
    }

    @Test
    fun `messageKey picks 'unpushed' for unpushed-only state`() {
        val key = GitGuardDialog.messageKey(
            State(hasUncommitted = false, hasUnpushed = true, quitInProgress = false)
        )
        assertEquals("gitguard.dialog.message.unpushed", key)
    }

    // --- compare / outgoingFor ---

    private fun ref(remote: String, branch: String, hash: String?) = RemoteRef(remote, "$remote/$branch", hash)

    @Test
    fun `compare is clean when the repo has no remotes`() {
        val result = GitGuardDialog.compare(false, "main", "abc", listOf(ref("origin", "main", "def")))
        assertEquals(Comparison.Clean, result)
    }

    @Test
    fun `compare is clean when there is no current branch`() {
        assertEquals(Comparison.Clean, GitGuardDialog.compare(true, null, null, emptyList()))
    }

    @Test
    fun `compare reports never pushed when no remote counterpart exists`() {
        assertEquals(Comparison.NeverPushed("main"), GitGuardDialog.compare(true, "main", "abc", emptyList()))
    }

    @Test
    fun `compare treats a remote ref without a known hash as missing`() {
        val result = GitGuardDialog.compare(true, "main", "abc", listOf(ref("origin", "main", null)))
        assertEquals(Comparison.NeverPushed("main"), result)
    }

    @Test
    fun `compare is clean when local and remote hashes match`() {
        val result = GitGuardDialog.compare(true, "main", "abc", listOf(ref("origin", "main", "abc")))
        assertEquals(Comparison.Clean, result)
    }

    @Test
    fun `compare is clean when any candidate matches, even if the preferred one lags`() {
        // Fork workflow: pushed to origin, upstream/main is behind.
        val candidates = listOf(ref("upstream", "main", "old"), ref("origin", "main", "abc"))
        assertEquals(Comparison.Clean, GitGuardDialog.compare(true, "main", "abc", candidates))
    }

    @Test
    fun `compare asks for a count against the first candidate when hashes differ`() {
        val origin = ref("origin", "main", "def")
        val result = GitGuardDialog.compare(true, "main", "abc", listOf(origin, ref("upstream", "main", "xyz")))
        assertEquals(Comparison.NeedsCount("main", "abc", origin), result)
    }

    @Test
    fun `outgoingFor reports nothing when the local branch is only behind its remote`() {
        // Regression: after a background fetch the hashes differ but nothing is ahead.
        val comparison = Comparison.NeedsCount("main", "abc", ref("origin", "main", "def"))
        assertNull(GitGuardDialog.outgoingFor(comparison, 0))
    }

    @Test
    fun `outgoingFor reports the ahead count`() {
        val comparison = Comparison.NeedsCount("main", "abc", ref("origin", "main", "def"))
        assertEquals(OutgoingInfo("main", "origin/main", 2), GitGuardDialog.outgoingFor(comparison, 2))
    }

    @Test
    fun `outgoingFor reports an unknown count instead of hiding it`() {
        val comparison = Comparison.NeedsCount("main", "abc", ref("origin", "main", "def"))
        assertEquals(OutgoingInfo("main", "origin/main", null), GitGuardDialog.outgoingFor(comparison, null))
    }

    @Test
    fun `outgoingFor reports never pushed branches and ignores clean ones`() {
        assertEquals(OutgoingInfo("x", null, null), GitGuardDialog.outgoingFor(Comparison.NeverPushed("x"), null))
        assertNull(GitGuardDialog.outgoingFor(Comparison.Clean, null))
    }

    // --- orderCandidates ---

    @Test
    fun `orderCandidates puts the upstream first, then origin, then other remotes`() {
        val upstream = ref("fork", "main", "a")
        val ordered = GitGuardDialog.orderCandidates(
            upstream,
            listOf(ref("zeta", "main", "z"), ref("fork", "main", "a"), ref("origin", "main", "o"), ref("alpha", "main", "x")),
        )
        assertEquals(listOf("fork/main", "origin/main", "alpha/main", "zeta/main"), ordered.map { it.name })
    }

    @Test
    fun `orderCandidates prefers origin over other same-named remotes without upstream`() {
        val ordered = GitGuardDialog.orderCandidates(null, listOf(ref("upstream", "main", "u"), ref("origin", "main", "o")))
        assertEquals("origin/main", ordered.first().name)
    }

    // --- renderMessage ---

    // Echo formatter — keeps the rendering test focused on structure (which keys
    // appear, in what order, with which args) instead of the localized text.
    private val echo: (String, List<Any>) -> String = { key, args ->
        if (args.isEmpty()) key else "$key(${args.joinToString(",")})"
    }

    @Test
    fun `renderMessage assembles lead, single detail bullet, then close question`() {
        val state = State(
            hasUncommitted = false,
            hasUnpushed = true,
            quitInProgress = false,
            unpushedDetails = listOf(OutgoingInfo("main", "origin/main", 3)),
        )
        val rendered = GitGuardDialog.renderMessage(state, echo)
        val expected = "gitguard.dialog.message.unpushed" +
            "\n  • gitguard.dialog.message.detail.ahead.many(main,3,origin/main)" +
            "\n\ngitguard.dialog.message.closeQuestion"
        assertEquals(expected, rendered)
    }

    @Test
    fun `renderMessage uses singular detail key when exactly one commit is ahead`() {
        val state = State(
            hasUncommitted = false,
            hasUnpushed = true,
            quitInProgress = false,
            unpushedDetails = listOf(OutgoingInfo("main", "origin/main", 1)),
        )
        val rendered = GitGuardDialog.renderMessage(state, echo)
        assertTrue(rendered.contains("gitguard.dialog.message.detail.ahead.one(main,origin/main)"))
        assertFalse(rendered.contains("gitguard.dialog.message.detail.ahead.many"))
    }

    @Test
    fun `renderMessage uses the not-pushed detail key when no remote branch was found`() {
        val state = State(
            hasUncommitted = false,
            hasUnpushed = true,
            quitInProgress = false,
            unpushedDetails = listOf(OutgoingInfo("feature/x", null, null)),
        )
        val rendered = GitGuardDialog.renderMessage(state, echo)
        assertTrue(rendered.contains("gitguard.dialog.message.detail.notPushed(feature/x)"))
    }

    @Test
    fun `renderMessage emits one bullet per repository when multiple have unpushed commits`() {
        val state = State(
            hasUncommitted = false,
            hasUnpushed = true,
            quitInProgress = false,
            unpushedDetails = listOf(
                OutgoingInfo("main", "origin/main", 2),
                OutgoingInfo("feature/x", null, null),
            ),
        )
        val rendered = GitGuardDialog.renderMessage(state, echo)
        val bulletCount = rendered.split("\n  • ").size - 1
        assertEquals(2, bulletCount)
    }

    @Test
    fun `renderMessage omits detail bullets when nothing is unpushed`() {
        val state = State(
            hasUncommitted = true,
            hasUnpushed = false,
            quitInProgress = false,
        )
        val rendered = GitGuardDialog.renderMessage(state, echo)
        assertFalse(rendered.contains("•"))
        assertTrue(rendered.startsWith("gitguard.dialog.message.uncommitted"))
        assertTrue(rendered.endsWith("gitguard.dialog.message.closeQuestion"))
    }

    @Test
    fun `renderMessage uses the unknown detail key when the count could not be determined`() {
        val state = State(
            hasUncommitted = false,
            hasUnpushed = true,
            quitInProgress = false,
            unpushedDetails = listOf(OutgoingInfo("main", "origin/main", null)),
        )
        val rendered = GitGuardDialog.renderMessage(state, echo)
        assertTrue(rendered.contains("gitguard.dialog.message.detail.unknown(main,origin/main)"))
    }
}
