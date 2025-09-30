package com.example.ailearningapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ailearningapp.navigation.Subject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    onPick: (String) -> Unit,
    onOpenHistory: (() -> Unit)? = null
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("과목 선택") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "원하는 과목을 선택하세요. 난이도는 ‘학년/수준’에서 조절됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubjectCard(
                    title = "수학",
                    subtitle = "개념·유형 연습 및 서술형 대비",
                    badge = "MATH",
                    icon = Icons.Outlined.Calculate,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 120.dp),
                    onClick = { onPick(Subject.MATH.name) }
                )
                SubjectCard(
                    title = "영어",
                    subtitle = "독해·문법·어휘 균형 학습",
                    badge = "ENG",
                    icon = Icons.Outlined.Language, // 문제가 있으면 Icons.Outlined.Translate 로 교체
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 120.dp),
                    onClick = { onPick(Subject.ENGLISH.name) }
                )
            }

            if (onOpenHistory != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Text("학습 기록 보기")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val container by animateColorAsState(MaterialTheme.colorScheme.surface)
    val content = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.border(1.dp, borderColor, shape),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = content)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = onClick,
                        label = { Text(badge) },
                        shape = CircleShape,
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
