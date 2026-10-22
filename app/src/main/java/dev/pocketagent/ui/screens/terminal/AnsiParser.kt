package dev.pocketagent.ui.screens.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object AnsiParser {

    private val ANSI_REGEX = Regex("\u001B\\[([0-9;]*)m")

    fun parse(text: String): AnnotatedString {
        val clean = text.replace("\r\n", "\n").replace("\r", "\n")
        return buildAnnotatedString {
            var currentIndex = 0
            var currentColor: Color? = null
            var isBold = false

            val matches = ANSI_REGEX.findAll(clean).toList()
            for (match in matches) {
                // Append text before escape code
                if (match.range.first > currentIndex) {
                    val segment = clean.substring(currentIndex, match.range.first)
                    appendStyled(segment, currentColor, isBold)
                }

                // Process code
                val codes = match.groupValues[1].split(";").filter { it.isNotBlank() }
                if (codes.isEmpty() || codes.contains("0")) {
                    currentColor = null
                    isBold = false
                }
                for (code in codes) {
                    when (code) {
                        "1" -> isBold = true
                        "30" -> currentColor = Color(0xFF484F58) // Black / Dark Gray
                        "31" -> currentColor = Color(0xFFFF7B72) // Red
                        "32" -> currentColor = Color(0xFF7EE787) // Green
                        "33" -> currentColor = Color(0xFFFFA657) // Yellow
                        "34" -> currentColor = Color(0xFF79C0FF) // Blue
                        "35" -> currentColor = Color(0xFFD2A8FF) // Magenta
                        "36" -> currentColor = Color(0xFFA5D6FF) // Cyan
                        "37" -> currentColor = Color(0xFFE6EDF3) // White
                        "90" -> currentColor = Color(0xFF8B949E) // Bright Black (Gray)
                        "91" -> currentColor = Color(0xFFFFA198) // Bright Red
                        "92" -> currentColor = Color(0xFF56D364) // Bright Green
                        "93" -> currentColor = Color(0xFFE3B341) // Bright Yellow
                        "94" -> currentColor = Color(0xFF58A6FF) // Bright Blue
                        "95" -> currentColor = Color(0xFFBC8CFF) // Bright Magenta
                        "96" -> currentColor = Color(0xFF39C5CF) // Bright Cyan
                        "97" -> currentColor = Color(0xFFFFFFFF) // Bright White
                    }
                }
                currentIndex = match.range.last + 1
            }

            // Append remaining text
            if (currentIndex < clean.length) {
                val segment = clean.substring(currentIndex)
                appendStyled(segment, currentColor, isBold)
            }
        }
    }

    private fun AnnotatedString.Builder.appendStyled(text: String, color: Color?, bold: Boolean) {
        val style = SpanStyle(
            color = color ?: Color(0xFFE6EDF3),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        pushStyle(style)
        append(text)
        pop()
    }
}
