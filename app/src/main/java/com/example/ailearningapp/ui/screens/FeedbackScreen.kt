// app/src/main/java/com/example/ailearningapp/ui/screens/FeedbackScreen.kt
package com.example.ailearningapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ailearningapp.viewmodel.SolveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNext: () -> Unit,
    vm: SolveViewModel
) {
    val fb by vm.lastFeedback.collectAsState()
    val ui by vm.uiState.collectAsState()
    val scroll = rememberScrollState()

    Scaffold(
        bottomBar = {
            if (fb != null) {
                // ✅ 과목은 ViewModel이 들고 있는 최근 값 사용 (없으면 math)
                val subject = vm.currentSubject
                val requested = ui.difficultyTarget

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 난이도 조절 액션들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                vm.setDifficultyTarget((requested - 1).coerceAtLeast(1), autoReload = false)
                                vm.loadNewProblem(subject)
                                onNext()
                            },
                            enabled = !ui.loading && !ui.submitting && requested > 1,
                            modifier = Modifier.weight(1f)
                        ) { Text("더 쉽게") }

                        OutlinedButton(
                            onClick = {
                                vm.setDifficultyTarget(requested, autoReload = false)
                                vm.loadNewProblem(subject)
                                onNext()
                            },
                            enabled = !ui.loading && !ui.submitting,
                            modifier = Modifier.weight(1f)
                        ) { Text("같은 난이도") }

                        OutlinedButton(
                            onClick = {
                                vm.setDifficultyTarget((requested + 1).coerceAtMost(10), autoReload = false)
                                vm.loadNewProblem(subject)
                                onNext()
                            },
                            enabled = !ui.loading && !ui.submitting && requested < 10,
                            modifier = Modifier.weight(1f)
                        ) { Text("더 어렵게") }
                    }

                    // 다음 문제(기본 동작: 현재 목표 난이도로 새 문제)
                    Button(
                        onClick = {
                            vm.setDifficultyTarget(requested, autoReload = false)
                            vm.loadNewProblem(subject)
                            onNext()
                        },
                        enabled = !ui.loading && !ui.submitting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("다음 문제") }
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("AI 채점 결과", style = MaterialTheme.typography.headlineSmall)

            if (fb == null) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("결과를 불러오는 중입니다…", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            // 본문은 스크롤 가능
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ✅ 정답/오답 배너 (총점 카드 위)
                parseVerdictFromSummary(fb!!.summary)?.let { correct ->
                    VerdictBanner(correct = correct)
                }

                // 점수 + 4지표 카드
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("총점: ${fb!!.score}점", style = MaterialTheme.typography.titleMedium)

                        val crit = fb!!.criteria // Map<String, Int>
                        val acc = crit["정확도"]
                        val app = crit["접근법"]
                        val com = crit["완성도"]
                        val cre = crit["창의성"]

                        CriteriaRow("정확도", acc)
                        CriteriaRow("접근법", app)
                        CriteriaRow("완성도", com)
                        CriteriaRow("창의성", cre)

                        // 모든 지표가 동일할 때 안내
                        val allScores = listOfNotNull(acc, app, com, cre)
                        if (allScores.size == 4 && allScores.toSet().size == 1) {
                            AssistiveText(
                                text = "모든 지표 점수가 동일합니다. 필기 분량/가독성을 높이면 지표가 더 다양하게 반영될 수 있어요.",
                                tone = AssistTone.Info
                            )
                        }

                        fb!!.timeSpent.let { s ->
                            Spacer(Modifier.height(4.dp))
                            Text("풀이 시간: ${s}s", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // 잘한 점 카드 (불릿)
                if (fb!!.strengths.isNotEmpty()) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("잘한 점", style = MaterialTheme.typography.titleMedium)
                            BulletList(items = fb!!.strengths)
                        }
                    }
                }

                // 개선 제안 카드 (불릿)
                if (fb!!.improvements.isNotEmpty()) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("개선 제안", style = MaterialTheme.typography.titleMedium)
                            BulletList(items = fb!!.improvements)
                        }
                    }
                }

                // ❌ 요약 피드백 카드는 제거했습니다.

                // 난이도 안내 카드(읽기 전용)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    val actual = ui.actualDifficulty ?: ui.problem?.difficulty
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "이번 문제 난이도: ${actual?.coerceIn(1, 10) ?: "-"} / 10",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "아래 버튼으로 난이도를 조절해 새 문제를 받으세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/* ---------- Verdict / Criteria / Bullets ---------- */

@Composable
private fun VerdictBanner(correct: Boolean) {
    val bg = if (correct) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer
    val fg = if (correct) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = bg, shape = MaterialTheme.shapes.medium) {
        Text(
            text = if (correct) "정답입니다 🎉" else "오답입니다",
            color = fg,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun CriteriaRow(label: String, score: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${score ?: "-"} / 10", style = MaterialTheme.typography.bodyMedium)
        if (score != null) {
            LinearProgressIndicator(
                progress = { (score.coerceIn(0, 10) / 10f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { line ->
            Text("• $line", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/* ---------- Helpers ---------- */

/**
 * 요약 문자열에서 정답/오답 단서를 간단히 파싱.
 * 서버/모델 요약이 "정답입니다." 또는 "정답 판정: 정답입니다." 등을 포함한다는 가정.
 */
private fun parseVerdictFromSummary(summary: String): Boolean? {
    val s = summary.trim()
    return when {
        // 정답 패턴
        s.startsWith("정답입니다") ||
                s.contains("정답 판정: 정답입니다") ||
                s.contains("정답 판정 : 정답입니다") -> true

        // 오답 패턴
        s.startsWith("오답입니다") ||
                s.contains("정답 판정: 오답입니다") ||
                s.contains("정답 판정 : 오답입니다") -> false

        else -> null
    }
}

/** 작은 안내 문구 */
@Composable
private fun AssistiveText(text: String, tone: AssistTone) {
    val color = when (tone) {
        AssistTone.Info -> MaterialTheme.colorScheme.secondary
        AssistTone.Warn -> MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}

private enum class AssistTone { Info, Warn }
