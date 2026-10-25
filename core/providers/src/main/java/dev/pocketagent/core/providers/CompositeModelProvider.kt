package dev.pocketagent.core.providers

import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import kotlinx.coroutines.flow.Flow

class CompositeModelProvider(
    private val geminiProvider: ModelProvider,
    private val openAiProvider: ModelProvider,
    private val groqGrokProvider: ModelProvider? = null
) : ModelProvider {

    override val id: String = "composite"
    override val name: String = "Multi-Model Provider"

    override fun stream(request: ChatRequest): Flow<ChatEvent> {
        val model = request.model.lowercase()
        return when {
            groqGrokProvider != null && (model.startsWith("qwen") || model.startsWith("grok") || model.startsWith("llama") || model.startsWith("deepseek") || model.startsWith("mixtral")) -> {
                groqGrokProvider.stream(request)
            }
            model.startsWith("gpt") || model.contains("openai") || model.contains("o1") || model.contains("o3") -> {
                openAiProvider.stream(request)
            }
            else -> {
                geminiProvider.stream(request)
            }
        }
    }
}
