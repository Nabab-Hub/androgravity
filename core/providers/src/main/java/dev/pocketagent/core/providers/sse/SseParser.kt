package dev.pocketagent.core.providers.sse

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ServerSentEvent(
    val event: String? = null,
    val data: String = "",
    val id: String? = null,
    val retry: Long? = null
)

class SseParser {
    private val dataBuffer = StringBuilder()
    private var currentEvent: String? = null
    private var currentId: String? = null
    private var currentRetry: Long? = null

    /**
     * Parses a single line of an SSE stream.
     * Returns a ServerSentEvent if a full event was completed by an empty line, or null otherwise.
     */
    fun parseLine(line: String): ServerSentEvent? {
        val trimmed = line.trimEnd('\r', '\n')

        // An empty line signals the end of an event block
        if (trimmed.isEmpty()) {
            if (dataBuffer.isNotEmpty() || currentEvent != null || currentId != null) {
                val sse = ServerSentEvent(
                    event = currentEvent,
                    data = dataBuffer.toString(),
                    id = currentId,
                    retry = currentRetry
                )
                // Reset per-event fields
                dataBuffer.clear()
                currentEvent = null
                currentId = null
                return sse
            }
            return null
        }

        // Comment lines start with ':'
        if (trimmed.startsWith(":")) {
            return null
        }

        val colonIndex = trimmed.indexOf(':')
        val field: String
        val value: String

        if (colonIndex != -1) {
            field = trimmed.substring(0, colonIndex).trim()
            var rawVal = trimmed.substring(colonIndex + 1)
            // If value begins with a single space, remove it per SSE spec
            if (rawVal.startsWith(" ")) {
                rawVal = rawVal.substring(1)
            }
            value = rawVal
        } else {
            field = trimmed
            value = ""
        }

        when (field) {
            "data" -> {
                if (dataBuffer.isNotEmpty()) {
                    dataBuffer.append("\n")
                }
                dataBuffer.append(value)
            }
            "event" -> {
                currentEvent = value
            }
            "id" -> {
                currentId = value
            }
            "retry" -> {
                currentRetry = value.toLongOrNull()
            }
        }

        return null
    }

    /**
     * Flushes any remaining data if the stream finishes without a trailing empty line.
     */
    fun flush(): ServerSentEvent? {
        if (dataBuffer.isNotEmpty() || currentEvent != null || currentId != null) {
            val sse = ServerSentEvent(
                event = currentEvent,
                data = dataBuffer.toString(),
                id = currentId,
                retry = currentRetry
            )
            dataBuffer.clear()
            currentEvent = null
            currentId = null
            return sse
        }
        return null
    }
}

/**
 * Transforms a Flow of raw text chunks into a Flow of ServerSentEvents.
 */
fun Flow<String>.parseSse(): Flow<ServerSentEvent> = flow {
    val parser = SseParser()
    var leftover = ""

    collect { chunk ->
        val text = leftover + chunk
        val lines = text.split("\n")
        // The last element is either empty or an incomplete line
        for (i in 0 until lines.size - 1) {
            val event = parser.parseLine(lines[i])
            if (event != null) {
                emit(event)
            }
        }
        leftover = lines.last()
    }

    if (leftover.isNotEmpty()) {
        val event = parser.parseLine(leftover)
        if (event != null) {
            emit(event)
        }
    }

    val finalEvent = parser.flush()
    if (finalEvent != null) {
        emit(finalEvent)
    }
}
