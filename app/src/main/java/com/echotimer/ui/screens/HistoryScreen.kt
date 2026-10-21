package com.echotimer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.data.LapRecord
import com.echotimer.data.TimingSession
import com.echotimer.ui.DateFormats
import com.echotimer.ui.components.NeumorphicChip
import com.echotimer.ui.components.SectionTitle
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.theme.LocalEchoColors

/** 历史记录页。 */
@Composable
fun HistoryScreen(
    sessions: List<TimingSession>,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<TimingSession?>(null) }
    var renameTarget by remember { mutableStateOf<TimingSession?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.history_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            if (sessions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.history_count, sessions.size),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.history_empty_title),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.history_empty_body),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(items = sessions, key = { it.id }) { session ->
                    HistoryCard(
                        session = session,
                        expanded = expandedId == session.id,
                        onToggleExpand = {
                            expandedId = if (expandedId == session.id) null else session.id
                        },
                        onRename = { renameTarget = session },
                        onDelete = { deleteTarget = session },
                    )
                }
            }
        }
    }

    val targetToDelete = deleteTarget
    if (targetToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(targetToDelete.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.action_confirm), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val targetToRename = renameTarget
    if (targetToRename != null) {
        var draft by remember(targetToRename.id) { mutableStateOf(targetToRename.note) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.history_rename_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.history_rename_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(targetToRename.id, draft)
                        renameTarget = null
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistoryCard(
    session: TimingSession,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalEchoColors.current
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ElapsedFormatter.clock(session.totalMs),
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DateFormats.dateTime(session.startedAt),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (session.laps.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.timer_laps_count, session.laps.size),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (expanded) {
                        stringResource(R.string.history_collapse)
                    } else {
                        stringResource(R.string.history_expand)
                    },
                    color = colors.accent,
                    fontSize = 12.sp,
                )
            }
        }

        if (session.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = session.note,
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            SectionTitle(stringResource(R.string.timer_laps_title))
            Spacer(Modifier.height(6.dp))
            // 计次统一「最后一次在最前」：库里按 lap_index 升序存，展示时倒序。
            session.laps.asReversed().forEach { lap -> LapRow(lap) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeumorphicChip(
                    label = stringResource(R.string.history_rename),
                    selected = false,
                    onClick = onRename,
                )
                NeumorphicChip(
                    label = stringResource(R.string.history_delete),
                    selected = false,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun LapRow(lap: LapRecord) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = stringResource(R.string.timer_lap_item, lap.index),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        // 记录按下时的累计时长（不是相邻两次的差值）
        Text(
            text = ElapsedFormatter.clock(lap.totalMs),
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
