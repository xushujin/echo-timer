package com.echotimer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.BuildConfig
import com.echotimer.R
import com.echotimer.ui.components.SectionTitle
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.theme.LocalEchoColors

/**
 * 关于页：应用名、版本号、技术栈、支持范围、权限说明。
 * 版本号与技术栈版本全部取自 BuildConfig（由构建脚本注入），不手写常量。
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val colors = LocalEchoColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.about_title),
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(16.dp))

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeumorphicSurface(
                    modifier = Modifier
                        .width(64.dp)
                        .height(64.dp),
                    cornerRadius = 20.dp,
                    depth = 12.dp,
                ) {
                    Text(
                        text = "E",
                        modifier = Modifier.align(Alignment.Center),
                        color = colors.accent,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.about_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.about_section_stack))
            Spacer(Modifier.height(12.dp))
            AboutRow(
                stringResource(R.string.about_stack_language),
                stringResource(R.string.about_stack_language_value, BuildConfig.TECH_KOTLIN),
            )
            AboutRow(
                stringResource(R.string.about_stack_ui),
                stringResource(R.string.about_stack_ui_value, BuildConfig.TECH_COMPOSE_BOM),
            )
            AboutRow(
                stringResource(R.string.about_stack_build),
                stringResource(R.string.about_stack_build_value, BuildConfig.TECH_GRADLE, BuildConfig.TECH_AGP),
            )
            AboutRow(
                stringResource(R.string.about_stack_storage),
                stringResource(R.string.about_stack_storage_value),
            )
            AboutRow(
                stringResource(R.string.about_stack_voice),
                stringResource(R.string.about_stack_voice_value),
            )
        }

        Spacer(Modifier.height(14.dp))
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.about_section_support))
            Spacer(Modifier.height(12.dp))
            Paragraph(stringResource(R.string.about_support_os))
            Paragraph(stringResource(R.string.about_support_locale))
            Paragraph(stringResource(R.string.about_support_tts))
            Paragraph(stringResource(R.string.about_support_network))
        }

        Spacer(Modifier.height(14.dp))
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.about_section_permissions))
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                name = stringResource(R.string.about_perm_mic),
                description = stringResource(R.string.about_perm_mic_desc),
            )
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                name = stringResource(R.string.about_perm_internet),
                description = stringResource(R.string.about_perm_internet_desc),
            )
        }

        Spacer(Modifier.height(14.dp))
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.about_section_data))
            Spacer(Modifier.height(12.dp))
            Paragraph(stringResource(R.string.about_data_body))
            Spacer(Modifier.height(10.dp))
            SectionTitle(stringResource(R.string.about_section_licenses))
            Spacer(Modifier.height(10.dp))
            Paragraph(stringResource(R.string.about_licenses_body))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutRow(title: String, value: String) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            modifier = Modifier.width(96.dp),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun Paragraph(text: String) {
    val colors = LocalEchoColors.current
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 3.dp),
        color = colors.textSecondary,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun PermissionRow(name: String, description: String) {
    val colors = LocalEchoColors.current
    Column {
        Text(text = name, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(text = description, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
