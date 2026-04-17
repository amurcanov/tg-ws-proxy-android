package com.amurcanov.tgwsproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amurcanov.tgwsproxy.AppReleaseInfo

private enum class UpdateMarkdownBlockType {
    Heading,
    Paragraph,
    Bullet,
    Code
}

private data class UpdateMarkdownBlock(
    val type: UpdateMarkdownBlockType,
    val text: String,
    val level: Int = 0
)

private enum class InlineMarkdownToken {
    Bold,
    Italic,
    Code
}

private data class InlineMarkdownMatch(
    val index: Int,
    val token: InlineMarkdownToken
)

@Composable
fun AppUpdateDialog(
    release: AppReleaseInfo,
    onPostpone: () -> Unit,
    onUpdate: () -> Unit
) {
    val blocks = parseReleaseMarkdown(release.changelogMarkdown)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    val codeColor = MaterialTheme.colorScheme.onSurface

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Доступно обновление",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = release.versionTag,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Что изменилось",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (blocks.isEmpty()) {
                        Text(
                            text = "Список изменений пока не указан.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    } else {
                        for (block in blocks) {
                            when (block.type) {
                                UpdateMarkdownBlockType.Bullet -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = buildMarkdownAnnotatedString(
                                                text = block.text,
                                                codeBackground = codeBackground,
                                                codeColor = codeColor
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }

                                UpdateMarkdownBlockType.Code -> {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = block.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                        )
                                    }
                                }

                                UpdateMarkdownBlockType.Heading -> {
                                    Text(
                                        text = buildMarkdownAnnotatedString(
                                            text = block.text,
                                            codeBackground = codeBackground,
                                            codeColor = codeColor
                                        ),
                                        style = if (block.level <= 2) {
                                            MaterialTheme.typography.titleMedium
                                        } else {
                                            MaterialTheme.typography.titleSmall
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                UpdateMarkdownBlockType.Paragraph -> {
                                    Text(
                                        text = buildMarkdownAnnotatedString(
                                            text = block.text,
                                            codeBackground = codeBackground,
                                            codeColor = codeColor
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPostpone,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = "Позже",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Unspecified
                        )
                    }

                    Button(
                        onClick = onUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = "Обновить",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun parseReleaseMarkdown(markdown: String): List<UpdateMarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    val blocks = mutableListOf<UpdateMarkdownBlock>()
    val paragraphLines = mutableListOf<String>()
    val codeLines = mutableListOf<String>()
    var inCodeBlock = false

    fun flushParagraph() {
        if (paragraphLines.isEmpty()) return
        val text = paragraphLines.joinToString(" ").trim()
        if (text.isNotEmpty()) {
            blocks += UpdateMarkdownBlock(
                type = UpdateMarkdownBlockType.Paragraph,
                text = text
            )
        }
        paragraphLines.clear()
    }

    fun flushCodeBlock() {
        if (codeLines.isEmpty()) return
        blocks += UpdateMarkdownBlock(
            type = UpdateMarkdownBlockType.Code,
            text = codeLines.joinToString("\n").trimEnd()
        )
        codeLines.clear()
    }

    val lines = markdown.replace("\r\n", "\n").lines()
    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length > 6) {
            flushParagraph()
            flushCodeBlock()
            blocks += UpdateMarkdownBlock(
                type = UpdateMarkdownBlockType.Code,
                text = trimmed.removePrefix("```").removeSuffix("```").trim()
            )
            continue
        }

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                flushCodeBlock()
            } else {
                flushParagraph()
            }
            inCodeBlock = !inCodeBlock
            continue
        }

        if (inCodeBlock) {
            codeLines += line.trimEnd()
            continue
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += UpdateMarkdownBlock(
                type = UpdateMarkdownBlockType.Heading,
                text = headingMatch.groupValues[2].trim(),
                level = headingMatch.groupValues[1].length
            )
            continue
        }

        val bulletMatch = Regex("^([-*+]|\\d+\\.)\\s+(.+)$").find(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            blocks += UpdateMarkdownBlock(
                type = UpdateMarkdownBlockType.Bullet,
                text = bulletMatch.groupValues[2].trim()
            )
            continue
        }

        paragraphLines += trimmed
    }

    flushParagraph()
    if (inCodeBlock || codeLines.isNotEmpty()) {
        flushCodeBlock()
    }

    return blocks
}

private fun buildMarkdownAnnotatedString(
    text: String,
    codeBackground: Color,
    codeColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        appendMarkdownInline(
            text = text,
            codeBackground = codeBackground,
            codeColor = codeColor
        )
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    text: String,
    codeBackground: Color,
    codeColor: Color
) {
    var index = 0

    while (index < text.length) {
        val nextMatch = findNextInlineMarkdown(text, index)
        if (nextMatch == null) {
            append(text.substring(index))
            return
        }

        if (nextMatch.index > index) {
            append(text.substring(index, nextMatch.index))
        }

        when (nextMatch.token) {
            InlineMarkdownToken.Bold -> {
                val closingIndex = text.indexOf("**", nextMatch.index + 2)
                if (closingIndex == -1) {
                    append("**")
                    index = nextMatch.index + 2
                } else {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendMarkdownInline(
                        text = text.substring(nextMatch.index + 2, closingIndex),
                        codeBackground = codeBackground,
                        codeColor = codeColor
                    )
                    pop()
                    index = closingIndex + 2
                }
            }

            InlineMarkdownToken.Italic -> {
                val closingIndex = findItalicClosing(text, nextMatch.index + 1)
                if (closingIndex == -1) {
                    append('*')
                    index = nextMatch.index + 1
                } else {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendMarkdownInline(
                        text = text.substring(nextMatch.index + 1, closingIndex),
                        codeBackground = codeBackground,
                        codeColor = codeColor
                    )
                    pop()
                    index = closingIndex + 1
                }
            }

            InlineMarkdownToken.Code -> {
                val closingIndex = text.indexOf('`', nextMatch.index + 1)
                if (closingIndex == -1) {
                    append('`')
                    index = nextMatch.index + 1
                } else {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeColor
                        )
                    )
                    append(text.substring(nextMatch.index + 1, closingIndex))
                    pop()
                    index = closingIndex + 1
                }
            }
        }
    }
}

private fun findNextInlineMarkdown(text: String, startIndex: Int): InlineMarkdownMatch? {
    var index = startIndex
    while (index < text.length) {
        if (text.startsWith("**", index)) {
            return InlineMarkdownMatch(index = index, token = InlineMarkdownToken.Bold)
        }

        if (text[index] == '`') {
            return InlineMarkdownMatch(index = index, token = InlineMarkdownToken.Code)
        }

        if (
            text[index] == '*' &&
            (index == 0 || text[index - 1] != '*') &&
            (index + 1 >= text.length || text[index + 1] != '*')
        ) {
            return InlineMarkdownMatch(index = index, token = InlineMarkdownToken.Italic)
        }

        index++
    }
    return null
}

private fun findItalicClosing(text: String, startIndex: Int): Int {
    var index = startIndex
    while (index < text.length) {
        if (
            text[index] == '*' &&
            (index == 0 || text[index - 1] != '*') &&
            (index + 1 >= text.length || text[index + 1] != '*')
        ) {
            return index
        }
        index++
    }
    return -1
}
