package com.echotimer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.theme.LocalEchoColors

/**
 * 拟态弹窗。
 *
 * 不用 Material3 的 `AlertDialog`：它自带纯白（深色下是深灰）直角面板 + 默认排版，
 * 与整体的拟态风格割裂（截图里"手工录入成绩"那个白底方框就是这么来的）。
 * 这里改成与页面同款的圆角拟态面板，标题、正文、按钮全部走同一套配色与字体。
 *
 * [contentMaxHeight] 限制正文高度并允许滚动，避免姓名胶囊很多时把弹窗顶出屏幕。
 */
@Composable
fun NeumorphicDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentMaxHeight: Dp = 400.dp,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalEchoColors.current
    Dialog(
        onDismissRequest = onDismissRequest,
        // 关掉平台默认宽度，由弹窗自己决定边距；decorFitsSystemWindows=false 配合 imePadding，
        // 键盘弹出时弹窗整体上移，输入框不会被挡住。
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 弹窗画在系统栏下面（decorFitsSystemWindows = false），这里把安全区与键盘一起让出来。
                .systemBarsPadding()
                .imePadding()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            NeumorphicSurface(
                modifier = modifier
                    .padding(horizontal = 24.dp)
                    // 平板上不让弹窗拉满整屏，保持"卡片"观感。
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                cornerRadius = 30.dp,
                depth = 26.dp,
                faceColor = colors.background,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
            ) {
                Column {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = contentMaxHeight)
                            .verticalScroll(rememberScrollState()),
                        content = content,
                    )
                    if (actions != null) {
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 弹窗里的操作按钮。
 *
 * [emphasis] 主操作用强调色实心胶囊，其余用与背景同色的拟态胶囊（靠阴影区分层次）；
 * [danger] 用于删除这类不可逆操作。
 */
@Composable
fun NeumorphicDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalEchoColors.current
    val face = when {
        !enabled -> colors.surfaceAlt
        emphasis && danger -> colors.danger
        emphasis -> colors.accent
        else -> colors.surface
    }
    val textColor = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.45f)
        emphasis -> if (danger) colors.background else colors.onAccent
        danger -> colors.danger
        else -> colors.textSecondary
    }
    NeumorphicSurface(
        modifier = modifier
            .height(42.dp)
            .clickable(enabled = enabled, onClick = onClick),
        cornerRadius = 21.dp,
        depth = 8.dp,
        faceColor = face,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 拟态输入框：标签在上、输入区做成"按下去"的凹陷面板。
 *
 * 不用 `OutlinedTextField`：它的描边方框在拟态界面里显得很硬，
 * 而且 Material 的标签浮动动画与整体风格不搭。
 */
@Composable
fun NeumorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    /** 令牌这类敏感内容用圆点显示。 */
    secret: Boolean = false,
) {
    val colors = LocalEchoColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(7.dp))
        NeumorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            // pressed = true 让阴影内翻，看起来像"凹进去的输入槽"。
            pressed = true,
            depth = 8.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                visualTransformation = if (secret) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                color = colors.textSecondary.copy(alpha = 0.65f),
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** 弹窗内的错误提示行，统一字号与颜色，避免每个弹窗各写一份。 */
@Composable
fun DialogErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalEchoColors.current.danger,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

/** 弹窗正文，统一字号、行高与次要色。 */
@Composable
fun DialogBodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalEchoColors.current.textSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}
