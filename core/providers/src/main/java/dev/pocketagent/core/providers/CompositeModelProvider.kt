package dev.pocketagent.core.providers

import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import kotlinx.coroutines.flow.Flow

class CompositeModelProvider(
    private val geminiProvider: ModelProvider,
    private val openAiProvider: ModelProvider
) : ModelProvider {

    override val id: String = "composite"
    override val name: String = "Multi-Model Provider"

    override fun stream(request: ChatRequest): Flow<ChatEvent> {
        val model = request.model.lowercase()
        return if (model.startsWith("gpt") || model.contains("openai") || model.contains("o1") || model.contains("o3")) {
            openAiProvider.stream(request)
        } else {
            geminiProvider.stream(request)
        }
    }
}
