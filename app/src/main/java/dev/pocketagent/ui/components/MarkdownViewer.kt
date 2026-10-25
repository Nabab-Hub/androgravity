package dev.pocketagent.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletItem(val text: String, val level: Int = 0) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class TaskItem(val checked: Boolean, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

@Composable
fun MarkdownViewer(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (style, topSpace) = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp) to 8.dp
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp) to 6.dp
                        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp) to 4.dp
                        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold) to 2.dp
                    }
                    Spacer(modifier = Modifier.height(topSpace))
                    SelectionContainer {
                        Text(
                            text = renderInlineMarkdown(block.text, textColor),
                            style = style,
                            color = textColor
                        )
                    }
                    if (block.level == 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.level * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = 8.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = renderInlineMarkdown(block.text, textColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}. ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = renderInlineMarkdown(block.text, textColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                is MarkdownBlock.TaskItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = block.checked,
                            onCheckedChange = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = renderInlineMarkdown(block.text, textColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (block.checked) textColor.copy(alpha = 0.6f) else textColor
                            )
                        }
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = renderInlineMarkdown(block.text, textColor),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = renderInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(language: String, code: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column {
            // Header bar with language and Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFAAAAAA)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Code content with horizontal scrolling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFE0E0E0),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 1. Code blocks (```)
        if (line.trimStart().startsWith("```")) {
            val lang = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing ```
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // 2. Dividers
        val trimmed = line.trim()
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 3. Headings (#)
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val headingText = trimmed.drop(level).trim()
            blocks.add(MarkdownBlock.Heading(level.coerceIn(1, 4), headingText))
            i++
            continue
        }

        // 4. Task items (- [ ] or - [x])
        if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
            val checked = trimmed[3] == 'x' || trimmed[3] == 'X'
            val taskText = trimmed.substring(6).trim()
            blocks.add(MarkdownBlock.TaskItem(checked, taskText))
            i++
            continue
        }

        // 5. Bullet items (*, -, +)
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ") || trimmed.startsWith("• ")) {
            val indent = line.takeWhile { it == ' ' || it == '\t' }.length / 2
            val bulletText = trimmed.drop(2).trim()
            blocks.add(MarkdownBlock.BulletItem(bulletText, indent))
            i++
            continue
        }

        // 6. Numbered items (1. , 2. )
        val numberMatch = Regex("""^(\d+)\.\s+(.*)""").find(trimmed)
        if (numberMatch != null) {
            val num = numberMatch.groupValues[1]
            val itemText = numberMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, itemText))
            i++
            continue
        }

        // 7. Blockquotes (> )
        if (trimmed.startsWith("> ")) {
            val quoteText = trimmed.drop(2).trim()
            blocks.add(MarkdownBlock.Blockquote(quoteText))
            i++
            continue
        }

        // 8. Empty lines
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 9. Regular paragraph - collect consecutive non-empty lines
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() && !lines[i].trimStart().startsWith("```") && !lines[i].trim().startsWith("#") && !lines[i].trim().startsWith("- ") && !lines[i].trim().startsWith("* ") && !lines[i].trim().startsWith("> ")) {
            paragraphLines.add(lines[i])
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
    }

    return blocks
}

fun renderInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("""(\*\*(.*?)\*\*|\*(.*?)\*|`([^`]+)`|\[(.*?)\]\((.*?)\))""")
        val matches = regex.findAll(text)

        for (match in matches) {
            // Append preceding plain text
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val full = match.value
            when {
                // Bold: **text**
                full.startsWith("**") && full.endsWith("**") -> {
                    val inner = full.removeSurrounding("**")
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(inner)
                    pop()
                }
                // Inline Code: `code`
                full.startsWith("`") && full.endsWith("`") -> {
                    val inner = full.removeSurrounding("`")
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            background = Color(0x33888888),
                            fontSize = 12.sp
                        )
                    )
                    append(" $inner ")
                    pop()
                }
                // Link: [text](url)
                full.startsWith("[") && full.contains("](") && full.endsWith(")") -> {
                    val label = full.substringAfter("[").substringBefore("](")
                    pushStyle(SpanStyle(color = Color(0xFF64B5F6), fontWeight = FontWeight.SemiBold))
                    append(label)
                    pop()
                }
                // Italic: *text*
                full.startsWith("*") && full.endsWith("*") -> {
                    val inner = full.removeSurrounding("*")
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(inner)
                    pop()
                }
                else -> {
                    append(full)
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
