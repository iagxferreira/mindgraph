package dev.mindgraph.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.Workspace
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Border
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.TextMuted

/**
 * Which selection the vault is being read through.
 *
 * Reads "All" rather than empty when nothing is chosen, because the whole vault is a real choice
 * and the control should say what is being looked at rather than only what has been narrowed to.
 *
 * The offered folders come from paths already stored on imported nodes, so a workspace can be
 * made from a folder someone maintained for years without them retyping where it lives.
 */
@Composable
fun WorkspaceSwitcher(
    workspaces: List<Node>,
    active: Node?,
    suggestions: List<Triple<String, String, Int>>,
    onSelect: (NodeId?) -> Unit,
    onCreate: (String, Workspace.Rule) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    if (workspaces.isEmpty() && suggestions.isEmpty()) return

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .border(1.dp, if (active != null) Accent else Border, RoundedCornerShape(10.dp))
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Layers,
            contentDescription = "Workspace",
            tint = if (active != null) Accent else TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            active?.title ?: "All",
            style = MaterialTheme.typography.labelMedium,
            color = if (active != null) Accent else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All") },
                onClick = { onSelect(null); open = false },
            )
            workspaces.forEach { workspace ->
                DropdownMenuItem(
                    text = { Text(workspace.title) },
                    onClick = { onSelect(workspace.id); open = false },
                )
            }
            if (suggestions.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    enabled = false,
                    text = {
                        Text(
                            "Make one from a folder",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    },
                    onClick = {},
                )
                suggestions.forEach { (folder, path, count) ->
                    DropdownMenuItem(
                        // The count is the useful half: it says whether the folder is worth
                        // looking at before the workspace exists.
                        text = { Text("$folder  ($count)") },
                        onClick = {
                            onCreate(folder, Workspace.Rule.OriginUnder(path))
                            open = false
                        },
                    )
                }
            }
        }
    }
}
