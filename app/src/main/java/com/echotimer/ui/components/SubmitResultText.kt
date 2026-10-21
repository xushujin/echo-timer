package com.echotimer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.echotimer.R
import com.echotimer.core.leaderboard.ScoreOutcome
import com.echotimer.core.leaderboard.SubmitResult
import com.echotimer.core.time.ElapsedFormatter

/**
 * 成绩提交结果的统一文案：挑战结束与手工录入共用，避免两处措辞不一致。
 */
@Composable
fun submitResultText(result: SubmitResult): String = when (result.outcome) {
    ScoreOutcome.NEW_ENTRY -> stringResource(
        R.string.challenge_result_new,
        ElapsedFormatter.clock(result.submittedMs),
    )

    ScoreOutcome.IMPROVED -> stringResource(
        R.string.challenge_result_improved,
        ElapsedFormatter.clock(result.submittedMs),
        ElapsedFormatter.clock(result.previousBestMs ?: 0L),
    )

    ScoreOutcome.KEPT -> stringResource(
        R.string.challenge_result_kept,
        ElapsedFormatter.clock(result.bestMs),
    )
}
