package com.example.ailearningapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.ailearningapp.data.repository.AiRepository
import com.example.ailearningapp.navigation.GradeBand
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeSelectScreen(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember(ctx) { AiRepository(ctx) }
    val current by repo.gradeBandFlow().collectAsState(initial = GradeBand.ELEMENTARY_UPPER)
    var selected by remember(current) { mutableStateOf(current) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("학년 / 수준 선택") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "선택한 수준에 맞춰 문제 난이도를 자동 조절해 드려요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GradeList(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch {
                        repo.setGradeBand(selected.name)
                        onNext()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("계속") }
        }
    }
}

/* ────────────────────────── List & Card ────────────────────────── */

@Composable
private fun GradeList(
    selected: GradeBand,
    onSelect: (GradeBand) -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = listOf(
        GradeCardModel(
            band = GradeBand.ELEMENTARY_LOWER,
            badge = "E 1–3",
            title = "초등 저(1~3)",
            subtitle = "기초 연습 · 개념 맛보기"
        ),
        GradeCardModel(
            band = GradeBand.ELEMENTARY_UPPER,
            badge = "E 4–6",
            title = "초등 고(4~6)",
            subtitle = "기본 개념 · 유형 적응"
        ),
        GradeCardModel(
            band = GradeBand.MIDDLE,
            badge = "M",
            title = "중학생",
            subtitle = "응용 문제 · 사고력 확장"
        ),
        GradeCardModel(
            band = GradeBand.HIGH,
            badge = "H",
            title = "고등학생",
            subtitle = "심화 학습 · 서술형 대비"
        )
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.forEach { model ->
            GradeSelectCard(
                model = model,
                selected = selected == model.band,
                onClick = { onSelect(model.band) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class GradeCardModel(
    val band: GradeBand,
    val badge: String,
    val title: String,
    val subtitle: String
)

@Composable
private fun GradeSelectCard(
    model: GradeCardModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    )

    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 110.dp)
            .border(1.dp, borderColor, shape),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (selected) 6.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                tonalElevation = if (selected) 4.dp else 0.dp,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        model.badge,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = content
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    model.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selected) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
