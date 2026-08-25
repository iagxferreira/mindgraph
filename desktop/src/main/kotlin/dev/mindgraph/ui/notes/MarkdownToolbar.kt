package dev.mindgraph.ui.notes

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Small formatting toolbar for the markdown editor: wraps the selection or the current line. */
@Composable
fun MarkdownToolbar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        IconButton(onClick = { onValueChange(wrapSelection(value, "**")) }) {
            Icon(Icons.Default.FormatBold, contentDescription = "Bold")
        }
        IconButton(onClick = { onValueChange(wrapSelection(value, "*")) }) {
            Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
        }
        IconButton(onClick = { onValueChange(wrapSelection(value, "`")) }) {
            Icon(Icons.Default.Code, contentDescription = "Inline code")
        }
        IconButton(onClick = { onValueChange(prefixCurrentLine(value, "## ")) }) {
            Icon(Icons.Default.Title, contentDescription = "Heading")
        }
        IconButton(onClick = { onValueChange(prefixCurrentLine(value, "- ")) }) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bullet list")
        }
    }
}

fun wrapSelection(value: TextFieldValue, marker: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val text = value.text
    val newText = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
    val newSelection = if (start == end) {
        TextRange(start + marker.length)
    } else {
        TextRange(start + marker.length, end + marker.length)
    }
    return value.copy(text = newText, selection = newSelection)
}

fun prefixCurrentLine(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start
    val searchFrom = (cursor - 1).coerceAtLeast(0)
    val lineStart = if (cursor == 0) 0 else text.lastIndexOf('\n', searchFrom).let { if (it == -1) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return value.copy(text = newText, selection = TextRange(cursor + prefix.length))
}
