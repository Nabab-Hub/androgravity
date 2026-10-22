package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.context.ContextBuilder
import dev.pocketagent.core.model.Message
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ContextBuilderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testRulesAndArtifactsInjectedIntoSystemPrompt() {
        val workspace = tempFolder.newFolder("ws_ctx")

        // 1. Create AGENTS.md
        File(workspace, "AGENTS.md").writeText("Must follow clean architecture.")

        // 2. Create .agent/rules/coding.md
        val rulesDir = File(workspace, ".agent/rules").apply { mkdirs() }
        File(rulesDir, "coding.md").writeText("Prefer Kotlin Coroutines.")

        // 3. Create .agent/artifacts/test-session/plan.md
        val artDir = File(workspace, ".agent/artifacts/test-session").apply { mkdirs() }
        File(artDir, "plan.md").writeText("- [x] Build skeleton")

        val builder = ContextBuilder()
        val context = builder.build(
            baseSystemPrompt = "You are PocketAgent.",
            workspaceRoot = workspace,
            sessionId = "test-session",
            history = emptyList(),
            userPrompt = "Hello agent"
        )

        assertThat(context.systemPrompt).contains("=== Project Rules (AGENTS.md) ===")
        assertThat(context.systemPrompt).contains("Must follow clean architecture.")
        assertThat(context.systemPrompt).contains("=== Rule: coding.md ===")
        assertThat(context.systemPrompt).contains("Prefer Kotlin Coroutines.")
        assertThat(context.systemPrompt).contains("=== Current Plan (plan.md) ===")
        assertThat(context.systemPrompt).contains("- [x] Build skeleton")

        assertThat(context.messages.size).isEqualTo(1)
        assertThat((context.messages[0] as Message.User).text).isEqualTo("Hello agent")
    }

    @Test
    fun testSkillsLoadedOnDemand() {
        val workspace = tempFolder.newFolder("ws_skills")
        val skillDir = File(workspace, ".agent/skills/git-workflow").apply { mkdirs() }
        File(skillDir, "SKILL.md").writeText(
            """
            ---
            name: git-workflow
            description: Workflow for branching and pull requests
            keywords: git, branch, pr
            ---
            Detailed instructions for creating feature branches and commits.
            """.trimIndent()
        )

        val builder = ContextBuilder()

        // 1. User prompt doesn't mention skill -> only summary in context
        val context1 = builder.build(
            baseSystemPrompt = "Base",
            workspaceRoot = workspace,
            sessionId = "s1",
            history = emptyList(),
            userPrompt = "Write a calculator in Python"
        )
        assertThat(context1.systemPrompt).contains("- git-workflow: Workflow for branching and pull requests")
        assertThat(context1.systemPrompt).doesNotContain("Detailed instructions for creating feature branches")

        // 2. User prompt mentions "git" -> full skill content loaded
        val context2 = builder.build(
            baseSystemPrompt = "Base",
            workspaceRoot = workspace,
            sessionId = "s1",
            history = emptyList(),
            userPrompt = "Please help me with git branching"
        )
        assertThat(context2.systemPrompt).contains("--- Active Skill: git-workflow ---")
        assertThat(context2.systemPrompt).contains("Detailed instructions for creating feature branches and commits.")
    }

    @Test
    fun testHistoryCompressionWhenExceedingBudget() {
        val workspace = tempFolder.newFolder("ws_budget")
        // Very tight budget to force compression
        val builder = ContextBuilder(maxTokenBudget = 200, thresholdPercent = 0.5)

        val longHistory = (1..10).map { i ->
            if (i % 2 == 1) Message.User(text = "User message $i: ${"hello ".repeat(20)}")
            else Message.Assistant(text = "Assistant response $i: ${"response ".repeat(20)}")
        }

        val context = builder.build(
            baseSystemPrompt = "Short base",
            workspaceRoot = workspace,
            sessionId = "s_budget",
            history = longHistory,
            userPrompt = "Final question"
        )

        // Older turns should be summarized into a single System message
        assertThat(context.messages.size).isLessThan(longHistory.size)
        val firstMsg = context.messages.first()
        assertThat(firstMsg).isInstanceOf(Message.System::class.java)
        assertThat((firstMsg as Message.System).text).contains("[Summary of earlier conversation turns]")
    }
}
