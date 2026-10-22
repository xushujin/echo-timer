package com.echotimer.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.leaderboard.CreateBoardResult
import com.echotimer.core.leaderboard.Leaderboard
import com.echotimer.core.leaderboard.LeaderboardEntry
import com.echotimer.core.leaderboard.LeaderboardRules
import com.echotimer.core.leaderboard.NameProblem
import com.echotimer.core.leaderboard.SubmitResult
import com.echotimer.core.share.PosterLayout
import com.echotimer.core.share.PosterMark
import com.echotimer.core.share.PosterRow
import com.echotimer.core.share.PosterSpec
import com.echotimer.core.share.ShareNaming
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.core.time.TimeInputParser
import com.echotimer.ui.ChallengeState
import com.echotimer.ui.DateFormats
import com.echotimer.ui.LeaderboardRoute
import com.echotimer.ui.components.DialogBodyText
import com.echotimer.ui.components.DialogErrorText
import com.echotimer.ui.components.Glyph
import com.echotimer.ui.components.GlyphIcon
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.components.NeumorphicChip
import com.echotimer.ui.components.NeumorphicDialog
import com.echotimer.ui.components.NeumorphicDialogButton
import com.echotimer.ui.components.NeumorphicTextField
import com.echotimer.ui.components.SectionTitle
import com.echotimer.ui.components.submitResultText
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.share.ImageSharer
import com.echotimer.ui.share.LeaderboardPoster
import com.echotimer.ui.share.toPosterPalette
import com.echotimer.ui.theme.LocalEchoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var poster by remember { mutableStateOf<Bitmap?>(null) }
    var posterName by remember { mutableStateOf("") }
    var posterBusy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bestMs = board.bestMs
    val subtitleText = if (bestMs != null) {
        stringResource(R.string.leaderboard_players, board.entryCount) + " · " +
            stringResource(R.string.leaderboard_best, ElapsedFormatter.clock(bestMs))
    } else {
        stringResource(R.string.leaderboard_no_score)
    }
    val posterFooter = stringResource(R.string.leaderboard_share_footer)
    val posterNote = if (entries.size > PosterLayout.MAX_ROWS) {
        stringResource(R.string.leaderboard_share_note, PosterLayout.MAX_ROWS, entries.size)
    } else {
        null
    }

    /** 生成分享图：按数据排版（不是截屏），长榜单也能整张导出。 */
    fun buildPoster() {
        if (posterBusy) return
        posterBusy = true
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                LeaderboardPoster.render(
                    PosterSpec(
                        title = board.name,
                        subtitle = subtitleText,
                        rows = entries.mapIndexed { index, entry ->
                            PosterRow(
                                rank = index + 1,
                                name = entry.player,
                                time = ElapsedFormatter.clock(entry.bestMs),
                                date = DateFormats.dateTime(entry.updatedAt),
                                mark = if (index == 0) PosterMark.CROWN else PosterMark.NONE,
                            )
                        },
                        footer = posterFooter,
                        note = posterNote,
                        palette = colors.toPosterPalette(),
                    ),
                )
            }
            poster = bitmap
            posterName = ShareNaming.fileNameFor(board.name, System.currentTimeMillis())
            posterBusy = false
        }
    }

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
                text = stringResource(R.string.leaderboard_share),
                modifier = Modifier
                    .clickable(onClick = ::buildPoster)
                    .padding(vertical = 4.dp),
                color = colors.accent,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(14.dp))
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
        Text(
            text = subtitleText,
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
        NeumorphicDialog(
            title = stringResource(R.string.leaderboard_entry_delete_title),
            onDismissRequest = { deleteEntryTarget = null },
            contentMaxHeight = 160.dp,
            actions = {
                Spacer(Modifier.weight(1f))
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = { deleteEntryTarget = null },
                )
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_confirm),
                    emphasis = true,
                    danger = true,
                    onClick = {
                        deleteEntryTarget = null
                        onDeleteEntry(entryToDelete.id)
                    },
                )
            },
        ) {
            DialogBodyText(
                stringResource(
                    R.string.leaderboard_entry_delete_body,
                    entryToDelete.player,
                    ElapsedFormatter.clock(entryToDelete.bestMs),
                ),
            )
        }
    }

    if (showManualDialog) {
        ManualScoreDialog(
            existingNames = entries.map { it.player },
            onSubmit = onManualSubmit,
            onDismiss = { showManualDialog = false },
        )
    }

    if (showDeleteDialog) {
        NeumorphicDialog(
            title = stringResource(R.string.leaderboard_delete_title),
            onDismissRequest = { showDeleteDialog = false },
            contentMaxHeight = 160.dp,
            actions = {
                Spacer(Modifier.weight(1f))
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = { showDeleteDialog = false },
                )
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_confirm),
                    emphasis = true,
                    danger = true,
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                )
            },
        ) {
            DialogBodyText(stringResource(R.string.leaderboard_delete_body))
        }
    }

    val currentPoster = poster
    if (currentPoster != null) {
        SharePosterDialog(
            bitmap = currentPoster,
            onShare = {
                val shared = ImageSharer.shareBitmap(
                    context = context,
                    bitmap = currentPoster,
                    fileName = posterName,
                    chooserTitle = context.getString(R.string.leaderboard_share_chooser),
                )
                if (!shared) {
                    Toast.makeText(context, R.string.leaderboard_share_failed, Toast.LENGTH_SHORT).show()
                }
                poster = null
            },
            onSave = if (ImageSharer.canSaveToGallery) {
                {
                    val saved = ImageSharer.saveToGallery(context, currentPoster, posterName)
                    Toast.makeText(
                        context,
                        if (saved) R.string.leaderboard_share_saved else R.string.leaderboard_share_save_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (saved) poster = null
                }
            } else {
                null
            },
            onDismiss = { poster = null },
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
    // 最后一名整行标红；只有一个人上榜时按第一名处理，不标红。
    val lastPlace = isLast && rank > 1
    val faceColor = if (lastPlace) lerp(colors.surface, colors.danger, 0.16f) else colors.surface
    val nameColor = if (lastPlace) colors.danger else colors.textPrimary
    NeumorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        depth = 10.dp,
        faceColor = faceColor,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(rank = rank, lastPlace = lastPlace)
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.player,
                        color = nameColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    // 第一名带小皇冠。
                    if (rank == 1) {
                        Spacer(Modifier.size(5.dp))
                        GlyphIcon(glyph = Glyph.CROWN, size = 14.dp, tint = colors.accent)
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
                color = nameColor,
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
private fun RankBadge(rank: Int, lastPlace: Boolean = false) {
    val colors = LocalEchoColors.current
    val faceColor = when {
        lastPlace -> lerp(colors.surface, colors.danger, 0.30f)
        rank <= 3 -> colors.accent
        else -> colors.surface
    }
    val textColor = when {
        lastPlace -> colors.danger
        rank <= 3 -> colors.onAccent
        else -> colors.textSecondary
    }
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        NeumorphicSurface(
            modifier = Modifier.fillMaxSize(),
            circle = true,
            depth = 6.dp,
            faceColor = faceColor,
        ) {}
        Text(
            text = stringResource(R.string.leaderboard_rank, rank),
            color = textColor,
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

    NeumorphicDialog(
        title = stringResource(R.string.leaderboard_create_title),
        onDismissRequest = onDismiss,
        contentMaxHeight = 200.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            NeumorphicDialogButton(
                label = stringResource(R.string.action_cancel),
                onClick = onDismiss,
            )
            NeumorphicDialogButton(
                label = stringResource(R.string.action_confirm),
                enabled = !submitting && problem == null,
                emphasis = true,
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
            )
        },
    ) {
        NeumorphicTextField(
            value = name,
            onValueChange = {
                name = it
                nameTaken = false
            },
            label = stringResource(R.string.leaderboard_create_hint),
            imeAction = ImeAction.Done,
        )
        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            DialogErrorText(errorText)
        }
    }
}

@Composable
private fun StartChallengeDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val problem = LeaderboardRules.validateName(name)
    val suggestions = existingNames.distinct().take(12)

    NeumorphicDialog(
        title = stringResource(R.string.leaderboard_start_title),
        onDismissRequest = onDismiss,
        contentMaxHeight = 340.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            NeumorphicDialogButton(
                label = stringResource(R.string.action_cancel),
                onClick = onDismiss,
            )
            NeumorphicDialogButton(
                label = stringResource(R.string.leaderboard_start),
                enabled = problem == null,
                emphasis = true,
                onClick = { onStart(name) },
            )
        },
    ) {
        NeumorphicTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.leaderboard_start_hint),
            imeAction = ImeAction.Done,
        )
        if (problem == NameProblem.TOO_LONG) {
            Spacer(Modifier.height(8.dp))
            DialogErrorText(
                stringResource(
                    R.string.leaderboard_name_too_long,
                    LeaderboardRules.MAX_NAME_LENGTH,
                ),
            )
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            NameSuggestions(
                names = suggestions,
                selected = name,
                onPick = { name = it },
            )
        }
    }
}

/** 已有选手的快捷选择，几个弹窗共用。 */
@Composable
private fun NameSuggestions(names: List<String>, selected: String, onPick: (String) -> Unit) {
    val colors = LocalEchoColors.current
    Text(
        text = stringResource(R.string.leaderboard_existing_players),
        color = colors.textSecondary,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
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

    NeumorphicDialog(
        title = stringResource(R.string.leaderboard_manual_title),
        onDismissRequest = onDismiss,
        contentMaxHeight = 360.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            if (done != null) {
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    emphasis = true,
                )
            } else {
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                )
                NeumorphicDialogButton(
                    label = stringResource(R.string.leaderboard_manual_submit),
                    enabled = !submitting && problem == null && millis != null,
                    emphasis = true,
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
                )
            }
        },
    ) {
        if (done != null) {
            Text(
                text = submitResultText(done),
                color = colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        } else {
            NeumorphicTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.leaderboard_manual_name),
            )
            Spacer(Modifier.height(12.dp))
            NeumorphicTextField(
                value = timeText,
                onValueChange = { timeText = it },
                label = stringResource(R.string.leaderboard_manual_time),
                placeholder = stringResource(R.string.leaderboard_manual_time_hint),
                imeAction = ImeAction.Done,
            )
            if (timeText.isNotEmpty() && millis == null) {
                Spacer(Modifier.height(8.dp))
                DialogErrorText(stringResource(R.string.leaderboard_manual_time_invalid))
            }
            if (problem == NameProblem.TOO_LONG) {
                Spacer(Modifier.height(8.dp))
                DialogErrorText(
                    stringResource(
                        R.string.leaderboard_name_too_long,
                        LeaderboardRules.MAX_NAME_LENGTH,
                    ),
                )
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                NameSuggestions(
                    names = suggestions,
                    selected = name,
                    onPick = { name = it },
                )
            }
        }
    }
}

/** 榜单图片预览 + 分享/保存。预览按比例缩放，长榜单也不会撑爆弹窗。 */
@Composable
private fun SharePosterDialog(
    bitmap: Bitmap,
    onShare: () -> Unit,
    onSave: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    NeumorphicDialog(
        title = stringResource(R.string.leaderboard_share_title),
        onDismissRequest = onDismiss,
        contentMaxHeight = 320.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            val save = onSave
            if (save != null) {
                NeumorphicDialogButton(
                    label = stringResource(R.string.leaderboard_share_save),
                    onClick = save,
                )
            }
            NeumorphicDialogButton(
                label = stringResource(R.string.leaderboard_share_action),
                onClick = onShare,
                emphasis = true,
            )
        },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.leaderboard_share_preview),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}
