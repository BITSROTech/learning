// app/src/main/java/com/example/ailearningapp/ui/screens/HistoryScreen.kt
package com.example.ailearningapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.ailearningapp.data.local.HistoryItem
import com.example.ailearningapp.data.repository.AiRepository
import com.example.ailearningapp.navigation.GradeBand
import com.example.ailearningapp.navigation.Subject
import com.example.ailearningapp.ui.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    initialSubject: Subject? = null,
    initialGrade: GradeBand? = null,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val repo = remember(ctx) { AiRepository(ctx) }

    val all by repo.historyFlow().collectAsState(initial = emptyList())

    // 선택 상태 (초기 쿼리 반영)
    var subject by remember { mutableStateOf(initialSubject) }
    var grade by remember { mutableStateOf(initialGrade) }

    // 필터 적용 (subject는 히스토리에 소문자 문자열로 저장됨)
    val filtered = remember(all, subject, grade) {
        val subjectKey: String? = subject?.name?.lowercase()
        val gradeKey: String? = grade?.name
        all.filter { item ->
            (subjectKey == null || item.subject.equals(subjectKey, ignoreCase = true)) &&
                    (gradeKey == null || item.gradeBand == gradeKey)
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "학습 기록", onBack = onBack, onOpenSettings = onOpenSettings) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            FilterRow(
                subject = subject,
                onChangeSubject = { subject = it },
                grade = grade,
                onChangeGrade = { grade = it }
            )
            Spacer(Modifier.height(12.dp))

            val avg = if (filtered.isNotEmpty()) filtered.sumOf { it.score } / filtered.size else 0
            SummaryBar(total = filtered.size, avgScore = avg)

            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                EmptyState()
            } else {
                HistoryList(filtered)
            }
        }
    }
}

@Composable
private fun FilterRow(
    subject: Subject?, onChangeSubject: (Subject?) -> Unit,
    grade: GradeBand?, onChangeGrade: (GradeBand?) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = subject == null,
            onClick = { onChangeSubject(null) },
            label = { Text("전체") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = subject == Subject.MATH,
            onClick = { onChangeSubject(Subject.MATH) },
            label = { Text("수학") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = subject == Subject.ENGLISH,
            onClick = { onChangeSubject(Subject.ENGLISH) },
            label = { Text("영어") }
        )

        Spacer(Modifier.weight(1f))

        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(grade?.label ?: "전체 학년")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("전체 학년") },
                    onClick = { onChangeGrade(null); expanded = false }
                )
                GradeBand.entries.forEach { g ->
                    DropdownMenuItem(
                        text = { Text(g.label) },
                        onClick = { onChangeGrade(g); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(total: Int, avgScore: Int) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("총 풀이: $total 건", style = MaterialTheme.typography.bodyMedium)
                Text("평균 점수: $avgScore 점", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HistoryList(items: List<HistoryItem>) {
    val fmt = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${item.score}점",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (item.score >= 80) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // subject는 "math"/"english" 로 저장됨 → 한글로 표시
                    val subjectLabel = when (item.subject.lowercase()) {
                        "math" -> "수학"
                        "english" -> "영어"
                        else -> item.subject
                    }
                    Text("과목: $subjectLabel · 학년: ${item.gradeBand}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        fmt.format(item.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(item.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("아직 기록이 없어요.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "문제를 풀고 AI 채점을 받으면 이곳에서 확인할 수 있어요.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
