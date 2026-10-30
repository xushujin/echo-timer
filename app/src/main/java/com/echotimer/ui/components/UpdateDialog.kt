package com.echotimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.update.UpdateState
import com.echotimer.core.update.releaseInfo
import com.echotimer.ui.theme.LocalEchoColors

/**
 * 应用内更新弹窗。
 *
 * 同一个弹窗覆盖整条流程：发现新版本 → 下载（带进度）→ 已交给系统安装器 / 失败重试，
 * 避免"点一下弹一个框"的碎片感。点外部或返回键只是隐藏（[onHide]），
 * 不算「稍后」——只有明确点「稍后」才会记住这个版本不再自动提醒。
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onHide: () -> Unit,
    onOpenPage: () -> Unit,
    onRetry: () -> Unit,
) {
    val info = state.releaseInfo ?: return
    val colors = LocalEchoColors.current

    NeumorphicDialog(
        title = stringResource(R.string.update_dialog_title, info.version.toString()),
        onDismissRequest = onHide,
        contentMaxHeight = 360.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            when (state) {
                is UpdateState.Downloading -> NeumorphicDialogButton(
                    label = stringResource(R.string.update_action_hide),
                    onClick = onHide,
                )

                is UpdateState.Installing -> NeumorphicDialogButton(
                    label = stringResource(R.string.update_action_close),
                    onClick = onHide,
                )

                is UpdateState.NeedsPermission -> {
                    NeumorphicDialogButton(
                        label = stringResource(R.string.update_action_later),
                        onClick = onLater,
                    )
                    NeumorphicDialogButton(
                        label = stringResource(R.string.update_action_grant),
                        emphasis = true,
                        onClick = onDownload,
                    )
                }

                is UpdateState.Failed -> {
                    NeumorphicDialogButton(
                        label = stringResource(R.string.update_action_open_page),
                        onClick = onOpenPage,
                    )
                    NeumorphicDialogButton(
                        label = stringResource(R.string.update_action_retry),
                        emphasis = true,
                        onClick = onRetry,
                    )
                }

                else -> {
                    NeumorphicDialogButton(
                        label = stringResource(R.string.update_action_later),
                        onClick = onLater,
                    )
                    NeumorphicDialogButton(
                        label = stringResource(R.string.update_action_download),
                        emphasis = true,
                        onClick = onDownload,
                    )
                }
            }
        },
    ) {
        Text(
            text = stringResource(
                R.string.update_dialog_versions,
                currentVersion,
                info.version.toString(),
            ),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.update_dialog_notes_title),
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        DialogBodyText(
            info.notes.ifBlank { stringResource(R.string.update_dialog_notes_empty) },
        )

        when (state) {
            is UpdateState.Downloading -> DownloadProgress(state.progress)

            is UpdateState.NeedsPermission -> {
                Spacer(Modifier.height(12.dp))
                DialogBodyText(stringResource(R.string.update_dialog_permission))
            }

            is UpdateState.Installing -> {
                Spacer(Modifier.height(12.dp))
                DialogBodyText(stringResource(R.string.update_dialog_installing))
            }

            is UpdateState.Failed -> {
                Spacer(Modifier.height(12.dp))
                DialogErrorText(
                    stringResource(R.string.update_dialog_failed, state.message),
                )
            }

            else -> Unit
        }
    }
}

/** 下载进度条：轨道用按下感的浅色槽，进度用强调色填充。 */
@Composable
private fun DownloadProgress(progress: Float) {
    val colors = LocalEchoColors.current
    val ratio = progress.coerceIn(0f, 1f)
    Column {
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.surfaceAlt),
        ) {
            if (ratio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.accent),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (ratio <= 0f) {
                stringResource(R.string.update_dialog_downloading_unknown)
            } else {
                stringResource(R.string.update_dialog_downloading, (ratio * 100).toInt())
            },
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
    }
}
