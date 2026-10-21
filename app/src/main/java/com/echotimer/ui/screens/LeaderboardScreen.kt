package com.echotimer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.leaderboard.CreateBoardResult
import com.echotimer.core.leaderboard.Leaderboard
import com.echotimer.core.leaderboard.LeaderboardEntry
import com.echotimer.core.leaderboard.LeaderboardRules
import com.echotimer.core.leaderboard.NameProblem
import com.echotimer.core.leaderboard.SubmitResult
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.core.time.TimeInputParser
import com.echotimer.ui.ChallengeState
import com.echotimer.ui.DateFormats
import com.echotimer.ui.LeaderboardRoute
import com.echotimer.ui.components.Glyph
import com.echotimer.ui.components.GlyphIcon
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.components.NeumorphicChip
import com.echotimer.ui.components.SectionTitle
import com.echotimer.ui.components.submitResultText
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.theme.LocalEchoColors
import kotlinx.coroutines.launch

/**
 * 天梯 tab：榜单列表 → 榜单详情 → 挑战计时。
 * 挑战状态非空时优先显示挑战页（避免返回栈与计时状态不同步）。
 */
@Composable
fun LeaderboardTab(
    boards: List<Leaderboard>,
    entries: List<LeaderboardEntry>,
    route: LeaderboardRoute,
    challenge: ChallengeState?,
    onCreateBoard: suspend (String) -> CreateBoardResult,
    onDeleteBoard: (Long) -> Unit,
    onOpenBoard: (Long) -> Unit,
    onBack: () -> Unit,
    onStartChallenge: (Leaderboard, String) -> Unit,
    onManualSubmit: suspend (Long, String, Long) -> SubmitResult,
    onDeleteEntry: (Long) -> Unit,
    onTogglePause: () -> Unit,
    onFinishChallenge: () -> Unit,
    onDiscardChallenge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeChallenge = challenge
    if (activeChallenge != null) {
        ChallengeScreen(
            state = activeChallenge,
            onTogglePause = onTogglePause,
            onFinish = onFinishChallenge,
            onExit = onDiscardChallenge,
            modifier = modifier,
        )
        return
    }

    val boardRoute = route as? LeaderboardRoute.Board
    val board = boardRoute?.let { current -> boards.firstOrNull { it.id == current.boardId } }
    if (boardRoute != null && board != null) {
        BoardDetailScreen(
            board = board,
            entries = entries,
            onBack = onBack,
            onDelete = { onDeleteBoard(board.id) },
            onStartChallenge = { player -> onStartChallenge(board, player) },
            onManualSubmit = { player, ms -> onManualSubmit(board.id, player, ms) },
            onDeleteEntry = onDeleteEntry,
            modifier = modifier,
        )
    } else {
        BoardListScreen(
            boards = boards,
            onCreateBoard = onCreateBoard,
            onOpenBoard = onOpenBoard,
            modifier = modifier,
        )
    }
}

@Composable
private fun BoardListScreen(
    boards: List<Leaderboard>,
    onCreateBoard: suspend (String) -> CreateBoardResult,
    onOpenBoard: (Long) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalEchoColors.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.leaderboard_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            if (boards.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.leaderboard_count, boards.size),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        NeumorphicChip(
            label = stringResource(R.string.leaderboard_create),
            selected = false,
            onClick = { showCreateDialog = true },
        )
        Spacer(Modifier.height(16.dp))

        if (boards.isEmpty()) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.leaderboard_empty_title),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.leaderboard_empty_body),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(items = boards, key = { it.id }) { board ->
                    BoardCard(board = board, onClick = { onOpenBoard(board.id) })
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBoardDialog(
            onCreate = onCreateBoard,
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun BoardCard(board: Leaderboard, onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    NeumorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = board.name,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                val bestMs = board.bestMs
                Text(
                    text = if (bestMs != null) {
                        stringResource(R.string.leaderboard_players, board.entryCount) + " · " +
                            stringResource(R.string.leaderboard_best, ElapsedFormatter.clock(bestMs))
                    } else {
                        stringResource(R.string.leaderboard_no_score)
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            GlyphIcon(glyph = Glyph.TROPHY, size = 20.dp, tint = colors.accent)
        }
    }
}

@Composable
private fun BoardDetailScreen(
    board: Leaderboard,
    entries: List<LeaderboardEntry>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onStartChallenge: (String) -> Unit,
    onManualSubmit: suspend (String, Long) -> SubmitResult,
    onDeleteEntry: (Long) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalEchoColors.current
    var showStartDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteEntryTarget by remember { mutableStateOf<LeaderboardEntry?>(null) }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ " + stringResource(R.string.leaderboard_back),
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 4.dp),
                color = colors.accent,
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.leaderboard_delete),
                modifier = Modifier
                    .clickable { showDeleteDialog = true }
                    .padding(vertical = 4.dp),
                color = colors.danger,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = board.name,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        val bestMs = board.bestMs
        Text(
            text = if (bestMs != null) {
                stringResource(R.string.leaderboard_players, board.entryCount) + " · " +
                    stringResource(R.string.leaderboard_best, ElapsedFormatter.clock(bestMs))
            } else {
                stringResource(R.string.leaderboard_no_score)
            },
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.leaderboard_start),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.leaderboard_start_sub),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeumorphicChip(
                    label = stringResource(R.string.leaderboard_start),
                    selected = true,
                    onClick = { showStartDialog = true },
                )
                NeumorphicChip(
                    label = stringResource(R.string.leaderboard_manual),
                    selected = false,
                    onClick = { showManualDialog = true },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.leaderboard_entries_title))
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.leaderboard_entries_empty),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                itemsIndexed(items = entries, key = { _, entry -> entry.id }) { index, entry ->
                    EntryRow(
                        rank = index + 1,
                        entry = entry,
                        isLast = index == entries.lastIndex,
                        onDelete = { deleteEntryTarget = entry },
                    )
                }
            }
        }
    }

    if (showStartDialog) {
        StartChallengeDialog(
            existingNames = entries.map { it.player },
            onDismiss = { showStartDialog = false },
            onStart = { player ->
                showStartDialog = false
                onStartChallenge(player)
            },
        )
    }

    val entryToDelete = deleteEntryTarget
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteEntryTarget = null },
            title = { Text(stringResource(R.string.leaderboard_entry_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.leaderboard_entry_delete_body,
                        entryToDelete.player,
                        ElapsedFormatter.clock(entryToDelete.bestMs),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteEntryTarget = null
                        onDeleteEntry(entryToDelete.id)
                    },
                ) {
                    Text(stringResource(R.string.action_confirm), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteEntryTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showManualDialog) {
        ManualScoreDialog(
            existingNames = entries.map { it.player },
            onSubmit = onManualSubmit,
            onDismiss = { showManualDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.leaderboard_delete_title)) },
            text = { Text(stringResource(R.string.leaderboard_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.action_confirm), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EntryRow(
    rank: Int,
    entry: LeaderboardEntry,
    isLast: Boolean,
    onDelete: () -> Unit,
) {
    val colors = LocalEchoColors.current
    NeumorphicCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        depth = 10.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(rank = rank)
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.player,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    // 第一名小皇冠；最后一名小菜刀（只有一个人时按第一名处理）。
                    if (rank == 1) {
                        Spacer(Modifier.size(5.dp))
                        GlyphIcon(glyph = Glyph.CROWN, size = 14.dp, tint = colors.accent)
                    } else if (isLast) {
                        Spacer(Modifier.size(5.dp))
                        GlyphIcon(glyph = Glyph.CLEAVER, size = 14.dp, tint = colors.textSecondary)
                    }
                }
                Text(
                    text = DateFormats.dateTime(entry.updatedAt),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = ElapsedFormatter.clock(entry.bestMs),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.size(6.dp))
            val deleteLabel = stringResource(R.string.leaderboard_entry_delete)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = deleteLabel }
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph = Glyph.TRASH, size = 16.dp, tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val colors = LocalEchoColors.current
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        NeumorphicSurface(
            modifier = Modifier.fillMaxSize(),
            circle = true,
            depth = 6.dp,
            faceColor = if (rank <= 3) colors.accent else colors.surface,
        ) {}
        Text(
            text = stringResource(R.string.leaderboard_rank, rank),
            color = if (rank <= 3) colors.onAccent else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CreateBoardDialog(
    onCreate: suspend (String) -> CreateBoardResult,
    onDismiss: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var nameTaken by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val problem = LeaderboardRules.validateName(name)
    val errorText = when {
        nameTaken -> stringResource(R.string.leaderboard_name_taken)
        problem == NameProblem.TOO_LONG ->
            stringResource(R.string.leaderboard_name_too_long, LeaderboardRules.MAX_NAME_LENGTH)

        problem == NameProblem.EMPTY && name.isNotEmpty() -> stringResource(R.string.leaderboard_name_empty)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leaderboard_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameTaken = false
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.leaderboard_create_hint)) },
                )
                if (errorText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        color = colors.danger,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && problem == null,
                onClick = {
                    submitting = true
                    scope.launch {
                        when (onCreate(name)) {
                            is CreateBoardResult.Created -> onDismiss()
                            CreateBoardResult.NameTaken -> {
                                nameTaken = true
                                submitting = false
                            }

                            CreateBoardResult.InvalidName -> submitting = false
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun StartChallengeDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    val colors = LocalEchoColors.current
    var name by remember { mutableStateOf("") }
    val problem = LeaderboardRules.validateName(name)
    val suggestions = existingNames.distinct().take(12)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leaderboard_start_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.leaderboard_start_hint)) },
                )
                if (problem == NameProblem.TOO_LONG) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.leaderboard_name_too_long,
                            LeaderboardRules.MAX_NAME_LENGTH,
                        ),
                        color = colors.danger,
                        fontSize = 12.sp,
                    )
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    NameSuggestions(
                        names = suggestions,
                        selected = name,
                        onPick = { name = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = problem == null,
                onClick = { onStart(name) },
            ) {
                Text(stringResource(R.string.leaderboard_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 已有选手的快捷选择，两个对话框共用。 */
@Composable
private fun NameSuggestions(names: List<String>, selected: String, onPick: (String) -> Unit) {
    val colors = LocalEchoColors.current
    Text(
        text = stringResource(R.string.leaderboard_existing_players),
        color = colors.textSecondary,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(6.dp))
    names.chunked(2).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { candidate ->
                NeumorphicChip(
                    label = candidate,
                    selected = candidate == selected,
                    onClick = { onPick(candidate) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 手工录入成绩：姓名 + 成绩（12.34 / 1:23.45），提交后走与挑战相同的"只有更大才更新"规则。
 * 录入结果直接显示在同一个对话框里，避免用户不确定是否生效。
 */
@Composable
private fun ManualScoreDialog(
    existingNames: List<String>,
    onSubmit: suspend (String, Long) -> SubmitResult,
    onDismiss: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SubmitResult?>(null) }

    val problem = LeaderboardRules.validateName(name)
    val millis = TimeInputParser.parse(timeText)
    val suggestions = existingNames.distinct().take(12)
    val done = result

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leaderboard_manual_title)) },
        text = {
            Column {
                if (done != null) {
                    Text(
                        text = submitResultText(done),
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                    )
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.leaderboard_manual_name)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.leaderboard_manual_time)) },
                        placeholder = { Text(stringResource(R.string.leaderboard_manual_time_hint)) },
                    )
                    if (timeText.isNotEmpty() && millis == null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.leaderboard_manual_time_invalid),
                            color = colors.danger,
                            fontSize = 12.sp,
                        )
                    }
                    if (problem == NameProblem.TOO_LONG) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.leaderboard_name_too_long,
                                LeaderboardRules.MAX_NAME_LENGTH,
                            ),
                            color = colors.danger,
                            fontSize = 12.sp,
                        )
                    }
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        NameSuggestions(
                            names = suggestions,
                            selected = name,
                            onPick = { name = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (done != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            } else {
                TextButton(
                    enabled = !submitting && problem == null && millis != null,
                    onClick = {
                        val parsedMs = millis
                        if (parsedMs != null) {
                            submitting = true
                            scope.launch {
                                result = onSubmit(name, parsedMs)
                                submitting = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.leaderboard_manual_submit))
                }
            }
        },
        dismissButton = if (done != null) {
            null
        } else {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        },
    )
}
