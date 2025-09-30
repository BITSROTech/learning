// app/src/main/java/com/example/ailearningapp/ui/components/UiKits.kt
@file:Suppress("DEPRECATION")

package com.example.ailearningapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ailearningapp.data.local.HistoryItem

/* ───────── Colors (브랜드 느낌만 반영) ───────── */

private val GoogleWhite = Color(0xFFFFFFFF)
private val GoogleBorder = Color(0xFFE0E0E0)
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoText = Color(0xFF191919)

/* ───────── Top App Bar (설정 텍스트버튼 포함) ───────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("뒤로") }
            }
        },
        actions = {
            if (onOpenSettings != null) {
                TextButton(onClick = onOpenSettings) { Text("설정") }
            }
        }
    )
}

/* ───────── 브랜드 로그인 버튼 ───────── */

@Composable
fun GoogleLoginButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp).fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = GoogleWhite,
            contentColor = Color.Black
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(GoogleBorder))
    ) {
        Text("Google로 시작하기", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun KakaoLoginButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = KakaoYellow,
            contentColor = KakaoText
        )
    ) {
        Text("카카오로 시작하기", fontWeight = FontWeight.SemiBold)
    }
}

/* ───────── 로딩/메시지 ───────── */

@Composable
fun LoadingOverlay(
    visible: Boolean,
    text: String = "처리중..."
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.26f)),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(text)
            }
        }
    }
}

@Composable
fun MessageBar(
    message: String?,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    if (message == null) return
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        color = bg,
        contentColor = fg,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                Text(
                    text = "닫기",
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(start = 12.dp),
                    color = fg,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/* ───────── 점수/뱃지/칩 ───────── */

@Composable
fun ScoreBadge(score: Int) {
    val (bg, fg) = when {
        score >= 90 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        score >= 70 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    AssistChip(
        onClick = { /* no-op */ },
        label = { Text("점수 $score") },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = bg,
            labelColor = fg
        )
    )
}

@Composable
fun Pill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/* ───────── 공백 헬퍼 ───────── */

@Composable fun SpacerH(h: Int) = Spacer(Modifier.height(h.dp))
@Composable fun SpacerW(w: Int) = Spacer(Modifier.width(w.dp))

/* ───────── 공통 다이얼로그 ───────── */

@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmLabel: String = "확인",
    dismissLabel: String = "취소",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

/* ───────── 히스토리 카드 (HistoryScreen 재사용) ───────── */

@Composable
fun HistoryCard(
    item: HistoryItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill(item.subject)
                Pill(item.gradeBand)
                Spacer(modifier = Modifier.weight(1f))
                ScoreBadge(item.score)
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (item.summary.isNotBlank()) {
                Text(
                    item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val foot = remember(item) {
                "시간 ${item.elapsedSec}s • ${item.problemId.take(8)}…"
            }
            Text(
                foot,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}
