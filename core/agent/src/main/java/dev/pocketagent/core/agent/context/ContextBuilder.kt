package dev.pocketagent.core.agent.context

import dev.pocketagent.core.model.Message
import java.io.File

data class BuiltContext(
    val systemPrompt: String,
    val messages: List<Message>,
    val estimatedTokens: Int
)

class ContextBuilder(
    val maxTokenBudget: Int = 32_000,
    val thresholdPercent: Double = 0.70
) {

    fun build(
        baseSystemPrompt: String,
        workspaceRoot: File,
        sessionId: String,
        history: List<Message>,
        userPrompt: String
    ): BuiltContext {
        val systemPrompt = buildSystemPromptWithRulesAndSkills(baseSystemPrompt, workspaceRoot, sessionId, userPrompt)
        val initialHistory = history.toMutableList()
        if (userPrompt.isNotBlank() && (initialHistory.isEmpty() || initialHistory.last() !is Message.User || (initialHistory.last() as Message.User).text != userPrompt)) {
            initialHistory.add(Message.User(text = userPrompt))
        }

        // Token budget checking (~4 characters per token heuristic)
        val targetTokenLimit = (maxTokenBudget * thresholdPercent).toInt()
        var estimatedTokens = estimateTokens(systemPrompt, initialHistory)

        val processedMessages = if (estimatedTokens > targetTokenLimit) {
            compressHistory(initialHistory, targetTokenLimit - estimateTokens(systemPrompt, emptyList()))
        } else {
            initialHistory
        }

        estimatedTokens = estimateTokens(systemPrompt, processedMessages)

        return BuiltContext(
            systemPrompt = systemPrompt,
            messages = processedMessages,
            estimatedTokens = estimatedTokens
        )
    }

    private fun buildSystemPromptWithRulesAndSkills(
        basePrompt: String,
        workspaceRoot: File,
        sessionId: String,
        userPrompt: String
    ): String = buildString {
        appendLine(basePrompt)
        appendLine()

        // 1. Project Rules (AGENTS.md and .agent/rules/*.md)
        val agentsFile = File(workspaceRoot, "AGENTS.md")
        if (agentsFile.exists() && agentsFile.isFile) {
            appendLine("=== Project Rules (AGENTS.md) ===")
            appendLine(agentsFile.readText().trim())
            appendLine("=================================")
            appendLine()
        }

        val rulesDir = File(workspaceRoot, ".agent/rules")
        if (rulesDir.exists() && rulesDir.isDirectory) {
            rulesDir.listFiles { f -> f.extension.equals("md", ignoreCase = true) }?.forEach { ruleFile ->
                appendLine("=== Rule: ${ruleFile.name} ===")
                appendLine(ruleFile.readText().trim())
                appendLine("===============================")
                appendLine()
            }
        }

        // 2. Active Skills (scan .agent/skills/*/SKILL.md or skills/*/SKILL.md)
        val skills = scanSkills(workspaceRoot)
        if (skills.isNotEmpty()) {
            appendLine("=== Available Skills ===")
            for (skill in skills) {
                // If user query mentions skill name or keywords, load full text, otherwise show summary
                if (userPrompt.contains(skill.name, ignoreCase = true) || skill.keywords.any { userPrompt.contains(it, ignoreCase = true) }) {
                    appendLine("--- Active Skill: ${skill.name} ---")
                    appendLine(skill.fullContent)
                    appendLine("-----------------------------------")
                } else {
                    appendLine("- ${skill.name}: ${skill.description}")
                }
            }
            appendLine("========================")
            appendLine()
        }

        // 3. Artifacts (plan.md, tasks.md, walkthrough.md)
        val artifactsDir = File(workspaceRoot, ".agent/artifacts/$sessionId")
        if (artifactsDir.exists() && artifactsDir.isDirectory) {
            val planFile = File(artifactsDir, "plan.md")
            if (planFile.exists() && planFile.isFile) {
                appendLine("=== Current Plan (plan.md) ===")
                appendLine(planFile.readText().trim())
                appendLine("==============================")
                appendLine()
            }
            val tasksFile = File(artifactsDir, "tasks.md")
            if (tasksFile.exists() && tasksFile.isFile) {
                appendLine("=== Current Tasks (tasks.md) ===")
                appendLine(tasksFile.readText().trim())
                appendLine("================================")
                appendLine()
            }
            val walkthroughFile = File(artifactsDir, "walkthrough.md")
            if (walkthroughFile.exists() && walkthroughFile.isFile) {
                appendLine("=== Walkthrough (walkthrough.md) ===")
                appendLine(walkthroughFile.readText().trim())
                appendLine("====================================")
                appendLine()
            }

            // 4. User Line/Section Comments
            val commentsManager = dev.pocketagent.core.agent.artifacts.ArtifactManager()
            val formattedComments = commentsManager.formatCommentsForAgent(workspaceRoot, sessionId)
            if (formattedComments.isNotBlank()) {
                appendLine(formattedComments)
                appendLine()
            }
        }
    }.trim()

    data class SkillInfo(
        val name: String,
        val description: String,
        val keywords: List<String>,
        val fullContent: String
    )

    private fun scanSkills(workspaceRoot: File): List<SkillInfo> {
        val skillDirs = listOf(
            File(workspaceRoot, ".agent/skills"),
            File(workspaceRoot, "skills")
        )

        val result = mutableListOf<SkillInfo>()
        for (dir in skillDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            dir.listFiles()?.filter { it.isDirectory }?.forEach { skillFolder ->
                val skillFile = File(skillFolder, "SKILL.md")
                if (skillFile.exists() && skillFile.isFile) {
                    val content = skillFile.readText()
                    val (name, desc, keywords) = parseSkillMetadata(skillFolder.name, content)
                    result.add(SkillInfo(name, desc, keywords, content))
                }
            }
        }
        return result
    }

    private fun parseSkillMetadata(folderName: String, content: String): Triple<String, String, List<String>> {
        var name = folderName
        var desc = "Skill for $folderName"
        val keywords = mutableListOf(folderName.lowercase())

        // Basic frontmatter parser
        if (content.startsWith("---")) {
            val endIdx = content.indexOf("---", 3)
            if (endIdx != -1) {
                val frontmatter = content.substring(3, endIdx)
                for (line in frontmatter.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("name:", ignoreCase = true)) {
                        name = trimmed.substringAfter(":").trim().removeSurrounding("\"")
                    } else if (trimmed.startsWith("description:", ignoreCase = true)) {
                        desc = trimmed.substringAfter(":").trim().removeSurrounding("\"")
                    } else if (trimmed.startsWith("keywords:", ignoreCase = true)) {
                        keywords.addAll(trimmed.substringAfter(":").split(",").map { it.trim().lowercase() })
                    }
                }
            }
        }
        return Triple(name, desc, keywords)
    }

    private fun compressHistory(messages: List<Message>, messageBudgetTokens: Int): List<Message> {
        if (messages.size <= 4) return messages

        // Keep the latest 4 messages untouched
        val recentCount = minOf(4, messages.size)
        val recentMessages = messages.takeLast(recentCount)
        val olderMessages = messages.dropLast(recentCount)

        // Summarize older messages
        val summaryText = buildString {
            appendLine("[Summary of earlier conversation turns]")
            for (msg in olderMessages) {
                when (msg) {
                    is Message.User -> appendLine("User: ${msg.text.take(120)}...")
                    is Message.Assistant -> {
                        if (msg.text.isNotBlank()) appendLine("Assistant: ${msg.text.take(120)}...")
                        if (msg.toolCalls.isNotEmpty()) {
                            appendLine("Called tools: ${msg.toolCalls.joinToString { it.name }}")
                        }
                    }
                    is Message.ToolResult -> {
                        appendLine("Tool (${msg.toolName}): ${if (msg.ok) "Success" else "Failed"} - ${msg.output.take(80)}...")
                    }
                    else -> {}
                }
            }
        }

        val summaryMessage = Message.System(text = summaryText)
        val combined = mutableListOf<Message>()
        combined.add(summaryMessage)
        combined.addAll(recentMessages)
        return combined
    }

    fun estimateTokens(systemPrompt: String, messages: List<Message>): Int {
        var charCount = systemPrompt.length
        for (m in messages) {
            when (m) {
                is Message.User -> charCount += m.text.length
                is Message.Assistant -> {
                    charCount += m.text.length
                    for (tc in m.toolCalls) {
                        charCount += tc.name.length + tc.argsJson.length
                    }
                }
                is Message.ToolResult -> charCount += m.output.length + m.toolName.length
                is Message.System -> charCount += m.text.length
            }
        }
        return (charCount / 4).coerceAtLeast(1)
    }
}
