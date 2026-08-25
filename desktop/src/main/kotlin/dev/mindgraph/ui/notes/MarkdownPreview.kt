package dev.mindgraph.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A deliberately small markdown renderer: headings, bold/italic/inline-code, and bullet
 * lists. Not CommonMark-complete, just enough for personal notes — a real dependency can
 * replace this later if the format needs grow.
 */
@Composable
fun MarkdownPreview(text: String, modifier: Modifier = Modifier) {
    val lines = remember(text) { text.split("\n") }
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        for (line in lines) {
            when {
                line.startsWith("### ") -> Text(
                    inlineMarkdown(line.removePrefix("### ")),
                    style = MaterialTheme.typography.titleMedium,
                )
                line.startsWith("## ") -> Text(
                    inlineMarkdown(line.removePrefix("## ")),
                    style = MaterialTheme.typography.titleLarge,
                )
                line.startsWith("# ") -> Text(
                    inlineMarkdown(line.removePrefix("# ")),
                    style = MaterialTheme.typography.headlineSmall,
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    Text(inlineMarkdown(line.drop(2)), style = MaterialTheme.typography.bodyLarge)
                }
                line.isBlank() -> Spacer(modifier = Modifier.height(8.dp))
                else -> Text(
                    inlineMarkdown(line),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val inlinePattern = Regex("""\*\*(.+?)\*\*|`(.+?)`|\*(.+?)\*""")

private fun inlineMarkdown(line: String): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in inlinePattern.findAll(line)) {
        append(line.substring(lastIndex, match.range.first))
        val bold = match.groups[1]?.value
        val code = match.groups[2]?.value
        val italic = match.groups[3]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            code != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            italic != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        lastIndex = match.range.last + 1
    }
    append(line.substring(lastIndex))
}
