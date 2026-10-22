package dev.pocketagent.core.agent.policy

import dev.pocketagent.core.model.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

enum class AutonomyMode {
    MANUAL,
    SEMI_AUTONOMY,
    FULL_AUTONOMY
}

sealed interface PolicyDecision {
    object Allow : PolicyDecision
    data class RequireApproval(val reason: String) : PolicyDecision
    data class Deny(val reason: String) : PolicyDecision
}

class PolicyEngine(
    var autonomyMode: AutonomyMode = AutonomyMode.SEMI_AUTONOMY,
    var mcpPolicyProvider: ((serverName: String) -> dev.pocketagent.core.model.McpPolicy)? = null
) {

    // Hardcoded safety deny list - non-overridable by the model or user project config
    private val dangerousCommandPatterns = listOf(
        Regex("""\brm\s+-[a-zA-Z0-9]*r[a-zA-Z0-9]*\s+(/|\.\./|\*|/\*)"""), // rm -rf /, rm -rf ../, rm -rf *, rm -rf /*
        Regex("""\brm\s+-[a-zA-Z0-9]*r[a-zA-Z0-9]*\s+/data\b"""),
        Regex("""\bmkfs(\.\w+)?\b"""),
        Regex("""\b(fdisk|sfdisk|parted)\b"""),
        Regex("""\bdd\s+(if|of)=/dev/"""),
        Regex("""\b(reboot|poweroff|halt|shutdown|init\s+[06])\b"""),
        Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:"""), // fork bomb
        Regex("""/dev/block/"""),
        Regex("""/dev/(mem|kmem|port)"""),
        Regex("""/(system|vendor|product|apex)/bin/"""),
        Regex("""\b(su|sudo)\b"""), // privilege escalation
        Regex("""\bgit\s+push\s+.*(--force|-f)\b"""), // force push
        Regex("""\bgit\s+clean\s+.*-[a-zA-Z]*x"""), // git clean destructive
        Regex("""(\.ssh/(id_rsa|id_ed25519)|id_rsa|id_ed25519|\.aws/credentials|keystore\.(jks|bks)|\.android/debug\.keystore|/etc/shadow)""") // credential theft
    )

    private val readOnlyTools = setOf(
        "fs_read_file",
        "fs_list_dir",
        "search_grep",
        "search_glob",
        "git_status",
        "git_diff",
        "git_log",
        "git_branch",
        "artifact_write",
        "browser_screenshot",
        "browser_console",
        "browser_eval"
    )

    fun evaluate(
        toolCall: ToolCall,
        workspaceRoot: File,
        commandText: String? = null
    ): PolicyDecision {
        // 1. Check Hardcoded Deny List (never overridable)
        val effectiveCommand = commandText ?: if (toolCall.name == "terminal_run") extractCommandParam(toolCall.argsJson) else null
        if (effectiveCommand != null) {
            for (pattern in dangerousCommandPatterns) {
                if (pattern.containsMatchIn(effectiveCommand)) {
                    return PolicyDecision.Deny(
                        "Command blocked by safety policy: matches forbidden dangerous pattern '$pattern'."
                    )
                }
            }
        }

        // 2. Path Traversal Safety Check
        val pathParam = extractPathParam(toolCall.argsJson)
        if (pathParam != null) {
            val normalized = pathParam.replace('\\', '/')
            if (normalized.startsWith("../") || normalized.contains("/../") || normalized.startsWith("/") || File(pathParam).isAbsolute) {
                // Verify against workspace directory
                val candidate = if (normalized.startsWith("/")) {
                    File(normalized)
                } else if (File(pathParam).isAbsolute) {
                    File(pathParam)
                } else {
                    File(workspaceRoot, normalized)
                }

                try {
                    val candidateCanon = candidate.canonicalPath
                    val rootCanon = workspaceRoot.canonicalPath
                    if (!candidateCanon.startsWith(rootCanon)) {
                        return PolicyDecision.Deny(
                            "Access blocked: path '$pathParam' escapes active workspace boundary."
                        )
                    }
                } catch (_: Throwable) {
                    return PolicyDecision.Deny("Access blocked: invalid file path '$pathParam'.")
                }
            }

            // Also check for reading/writing sensitive files in path
            for (pattern in dangerousCommandPatterns) {
                if (pattern.containsMatchIn(normalized)) {
                    return PolicyDecision.Deny("Access blocked: path '$pathParam' targets sensitive protected resources.")
                }
            }
        }

        // 3. Project-level policy overrides from .agent/policy.json
        val projectPolicy = loadProjectPolicy(workspaceRoot)
        if (effectiveCommand != null && projectPolicy != null) {
            for (denyPat in projectPolicy.denyCommands) {
                if (Regex(denyPat).containsMatchIn(effectiveCommand)) {
                    return PolicyDecision.Deny("Command blocked by project policy (.agent/policy.json): '$denyPat'.")
                }
            }
        }

        // 4. MCP Tools Evaluation
        if (toolCall.name.startsWith("mcp.") || toolCall.name.startsWith("mcp_")) {
            val serverName = extractMcpServerName(toolCall.name)
            val policy = mcpPolicyProvider?.invoke(serverName) ?: dev.pocketagent.core.model.McpPolicy.ASK
            return when (policy) {
                dev.pocketagent.core.model.McpPolicy.DENY -> {
                    PolicyDecision.Deny("MCP server '$serverName' is disabled by policy.")
                }
                dev.pocketagent.core.model.McpPolicy.ALLOW -> {
                    if (autonomyMode == AutonomyMode.MANUAL) {
                        PolicyDecision.RequireApproval("All tool executions require approval in Manual mode.")
                    } else {
                        PolicyDecision.Allow
                    }
                }
                dev.pocketagent.core.model.McpPolicy.ASK -> {
                    if (autonomyMode == AutonomyMode.FULL_AUTONOMY) {
                        PolicyDecision.Allow
                    } else {
                        PolicyDecision.RequireApproval("MCP tool '${toolCall.name}' on server '$serverName' requires user approval.")
                    }
                }
            }
        }

        // 5. Browser Tools Evaluation
        val urlParam = extractUrlParam(toolCall.argsJson)
        if (toolCall.name == "browser_open") {
            if (urlParam != null && isLocalhostUrl(urlParam)) {
                if (autonomyMode == AutonomyMode.MANUAL) {
                    return PolicyDecision.RequireApproval("All tool executions require approval in Manual mode.")
                }
                return PolicyDecision.Allow
            } else {
                if (autonomyMode == AutonomyMode.FULL_AUTONOMY) {
                    return PolicyDecision.Allow
                }
                return PolicyDecision.RequireApproval("Opening external URL '$urlParam' requires approval.")
            }
        }
        if (toolCall.name == "browser_click" || toolCall.name == "browser_type") {
            if (autonomyMode == AutonomyMode.MANUAL) {
                return PolicyDecision.RequireApproval("All tool executions require approval in Manual mode.")
            }
            return PolicyDecision.Allow
        }

        // 6. Autonomy Mode Evaluation
        return when (autonomyMode) {
            AutonomyMode.FULL_AUTONOMY -> {
                PolicyDecision.Allow
            }

            AutonomyMode.SEMI_AUTONOMY -> {
                if (readOnlyTools.contains(toolCall.name)) {
                    PolicyDecision.Allow
                } else if (effectiveCommand != null && projectPolicy != null && projectPolicy.allowCommands.any { Regex(it).containsMatchIn(effectiveCommand) }) {
                    // Terminal command is explicitly allow-listed in project policy
                    PolicyDecision.Allow
                } else {
                    val reason = when (toolCall.name) {
                        "terminal_run" -> "Terminal command '$effectiveCommand' requires approval in Semi-Autonomy mode."
                        "fs_write_file" -> "Creating/overwriting file '$pathParam' requires approval."
                        "fs_edit_file" -> "Editing file '$pathParam' requires approval."
                        "fs_delete_file" -> "Deleting file or directory '$pathParam' requires approval."
                        "git_commit" -> "Committing changes requires approval."
                        else -> "Action '${toolCall.name}' requires approval in Semi-Autonomy mode."
                    }
                    PolicyDecision.RequireApproval(reason)
                }
            }

            AutonomyMode.MANUAL -> {
                PolicyDecision.RequireApproval("All tool executions require approval in Manual mode.")
            }
        }
    }

    data class ProjectPolicyConfig(
        val allowCommands: List<String> = emptyList(),
        val denyCommands: List<String> = emptyList()
    )

    private fun loadProjectPolicy(workspaceRoot: File): ProjectPolicyConfig? {
        val policyFile = File(workspaceRoot, ".agent/policy.json")
        if (!policyFile.exists() || !policyFile.isFile) return null

        return try {
            val root = Json.parseToJsonElement(policyFile.readText()).jsonObject
            val allow = root["allowCommands"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
            val deny = root["denyCommands"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
            ProjectPolicyConfig(allow, deny)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractMcpServerName(toolName: String): String {
        val separator = if (toolName.contains('.')) '.' else '_'
        val parts = toolName.split(separator)
        return if (parts.size >= 2) parts[1] else "unknown"
    }

    private fun extractUrlParam(json: String): String? {
        val regex = Regex(""""url"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun isLocalhostUrl(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.startsWith("http://localhost") ||
                lower.startsWith("https://localhost") ||
                lower.startsWith("http://127.0.0.1") ||
                lower.startsWith("https://127.0.0.1") ||
                lower.startsWith("http://0.0.0.0") ||
                lower.startsWith("http://[::1]")
    }

    private fun extractPathParam(json: String): String? {
        val regex = Regex(""""path"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractCommandParam(json: String): String? {
        val regex = Regex(""""command"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.get(1)
    }
}
