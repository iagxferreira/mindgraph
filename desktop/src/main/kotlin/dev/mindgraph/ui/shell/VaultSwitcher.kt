package dev.mindgraph.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mindgraph.storage.Vault
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.TextMuted
import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * Which vault is open, and how to open another.
 *
 * Lives on the rail beside the destinations because it is a different axis from them: those
 * choose what you are looking at *within* a vault, and this chooses which vault that is.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VaultSwitcher(current: Path, recent: List<Path>, onOpen: (Path) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val others = recent.filterNot { it.toAbsolutePath().normalize() == current.toAbsolutePath().normalize() }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("Vault: ${Vault(current).displayName}") } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (open) Accent.copy(alpha = 0.16f) else Color.Transparent)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Inventory2,
                contentDescription = "Vault",
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )

            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    enabled = false,
                    text = {
                        Text(
                            Vault(current).displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {},
                )
                if (others.isNotEmpty()) {
                    HorizontalDivider()
                    others.forEach { path ->
                        DropdownMenuItem(
                            text = { Text(Vault(path).displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { onOpen(path); open = false },
                        )
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Open or create a vault…") },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                    onClick = {
                        open = false
                        chooseVault(current)?.let(onOpen)
                    },
                )
            }
        }
    }
}

/**
 * Picks a directory to use as a vault, existing or not.
 *
 * Deliberately does not require one to be there already: choosing an empty folder inside a
 * project is how a project vault gets made, and `prepare()` creates what is missing. Refusing
 * anything without a `nodes/` directory would make creating one impossible from here.
 */
private fun chooseVault(current: Path): Path? {
    val chooser = JFileChooser(current.toFile()).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choose a folder for the vault"
        isMultiSelectionEnabled = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.toPath()
    } else {
        null
    }
}
