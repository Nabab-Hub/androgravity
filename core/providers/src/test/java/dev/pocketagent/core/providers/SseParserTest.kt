package dev.pocketagent.core.providers

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.providers.sse.SseParser
import dev.pocketagent.core.providers.sse.parseSse
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SseParserTest {

    @Test
    fun parseSimpleDataLine() {
        val parser = SseParser()
        assertThat(parser.parseLine("data: hello world")).isNull()
        val event = parser.parseLine("")
        assertThat(event).isNotNull()
        assertThat(event?.data).isEqualTo("hello world")
        assertThat(event?.event).isNull()
    }

    @Test
    fun parseMultipleDataLinesJoinedByNewline() {
        val parser = SseParser()
        parser.parseLine("data: line 1")
        parser.parseLine("data: line 2")
        val event = parser.parseLine("")
        assertThat(event).isNotNull()
        assertThat(event?.data).isEqualTo("line 1\nline 2")
    }

    @Test
    fun ignoresComments() {
        val parser = SseParser()
        assertThat(parser.parseLine(": this is a heartbeat comment")).isNull()
        parser.parseLine("data: actual message")
        val event = parser.parseLine("")
        assertThat(event?.data).isEqualTo("actual message")
    }

    @Test
    fun parsesEventAndId() {
        val parser = SseParser()
        parser.parseLine("event: message_delta")
        parser.parseLine("id: evt_123")
        parser.parseLine("data: chunk")
        val event = parser.parseLine("")
        assertThat(event?.event).isEqualTo("message_delta")
        assertThat(event?.id).isEqualTo("evt_123")
        assertThat(event?.data).isEqualTo("chunk")
    }

    @Test
    fun flowParserReassemblesChunksAcrossBoundaries() = runBlocking {
        // Stream split across arbitrary byte chunk boundaries
        val chunks = flowOf(
            "data: fir",
            "st part\n\ndata: second ",
            "event\n",
            "\n"
        )
        val events = chunks.parseSse().toList()
        assertThat(events).hasSize(2)
        assertThat(events[0].data).isEqualTo("first part")
        assertThat(events[1].data).isEqualTo("second event")
    }
}
