// app/src/main/java/com/example/ailearningapp/data/repository/AiRepository.kt
package com.example.ailearningapp.data.repository

import android.content.Context
import com.example.ailearningapp.data.ai.OpenAIInApp
import com.example.ailearningapp.data.local.HistoryItem
import com.example.ailearningapp.data.local.Prefs
import com.example.ailearningapp.data.model.Feedback
import com.example.ailearningapp.data.model.Problem as LegacyProblem
import com.example.ailearningapp.data.model.Submission
import com.example.ailearningapp.data.repo.ProblemRepository
import com.example.ailearningapp.data.repo.Problem as RepoProblem
import com.example.ailearningapp.navigation.GradeBand
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class AiRepository(private val ctx: Context) {

    private val inApp = OpenAIInApp(
        apiKey = ""
    )
    private val repo = ProblemRepository(inApp)

    private val moshi: Moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun gradeBandFlow() = Prefs.gradeBandFlow(ctx)
    fun historyFlow(): Flow<List<HistoryItem>> = Prefs.historyFlow(ctx)

    suspend fun setGradeBand(name: String) {
        val band = GradeBand.valueOf(name)
        Prefs.setGradeBand(ctx, band)
    }

    /**
     * 문제 생성(원샷)
     * - Cloud Run 제거 후 인앱 OpenAI 호출을 래핑한 ProblemRepository.generate 사용
     */
    suspend fun generateProblem(subject: String, targetDifficulty: Int? = null): LegacyProblem =
        withContext(Dispatchers.IO) {
            val band = Prefs.gradeBandFlow(ctx).first()
            val level = gradeBandToLevel(band)
            val target = (targetDifficulty ?: 3).coerceIn(1, 5)

            val meta: RepoProblem = repo.generate(
                subject = subject.lowercase(Locale.ROOT),
                level = level,
                difficulty = target
            )

            LegacyProblem(
                id = UUID.randomUUID().toString(),
                subject = subject.lowercase(Locale.ROOT),
                title = meta.title,
                body = meta.body,
                choices = meta.choices,
                answer = meta.answer,
                explain = meta.explain,
                difficulty = meta.difficulty
            )
        }

    /**
     * 채점 + 히스토리 저장
     */
    suspend fun gradeAndSave(problem: LegacyProblem, submission: Submission): Feedback =
        withContext(Dispatchers.IO) {
            val result = repo.grade(
                subject = problem.subject,
                answerText = submission.answerText.trim(),
                elapsedSec = submission.elapsedSec,
                expectedAnswer = problem.answer,
                problemText = problem.body,
                processImage = submission.processImage
            )

            val bandName = Prefs.gradeBandFlow(ctx).first().name

            val criteriaJson = try {
                @Suppress("UNCHECKED_CAST")
                val crit = result.criteria
                mapAdapter.toJson(crit as Map<String, Any?>)
            } catch (_: Throwable) {
                "{}"
            }

            Prefs.appendHistory(
                ctx,
                HistoryItem(
                    problemId = problem.id,
                    subject = problem.subject,
                    gradeBand = bandName,
                    title = problem.title,
                    score = result.score,
                    summary = result.summary,
                    elapsedSec = submission.elapsedSec,
                    criteriaJson = criteriaJson,
                    createdAt = System.currentTimeMillis()
                )
            )

            // 레거시 Feedback 모델로 매핑
            Feedback(
                score = result.score,
                criteria = result.criteria, // Map<String, Int>
                summary = result.summary,
                aiConfidence = result.aiConfidence,
                timeSpent = result.timeSpent
            )
        }

    private fun gradeBandToLevel(band: GradeBand): String = when (band) {
        GradeBand.ELEMENTARY_LOWER -> "elementary_low"
        GradeBand.ELEMENTARY_UPPER -> "elementary_high"
        GradeBand.MIDDLE          -> "middle_school"
        GradeBand.HIGH            -> "high_school"
    }
}
