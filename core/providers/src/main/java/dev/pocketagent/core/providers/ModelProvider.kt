package dev.pocketagent.core.providers

import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import kotlinx.coroutines.flow.Flow

interface ModelProvider {
    val id: String
    val name: String
    fun stream(request: ChatRequest): Flow<ChatEvent>
}
