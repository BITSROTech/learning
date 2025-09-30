// app/src/main/java/com/example/ailearningapp/data/repo/ProblemRepository.kt
package com.example.ailearningapp.data.repo

import android.graphics.Bitmap
import android.util.Base64
import com.example.ailearningapp.data.ai.OpenAIInApp
import com.example.ailearningapp.data.ai.ProblemMeta
import com.example.ailearningapp.data.ai.GradeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 앱 내부 OpenAI 호출로 교체된 레포지토리 (스트리밍 OFF: 한 번에 생성)
 */
class ProblemRepository(
    private val ai: OpenAIInApp
) {

    // ----- 생성 (한 번에) -----
    suspend fun generate(
        subject: String,
        level: String,              // "elementary_low" | "elementary_high" | "middle_school" | "high_school"
        difficulty: Int = 5,
        previousTitle: String? = null,
        previousBody: String? = null,
        previousAnswer: String? = null
    ): Problem = withContext(Dispatchers.IO) {
        val meta: ProblemMeta = ai.generateProblemOneShot(
            subject = subject,
            level = level,
            targetDifficulty = difficulty.coerceIn(1, 10),
            previousTitle = previousTitle,
            previousBody = previousBody,
            previousAnswer = previousAnswer
        )
        Problem(
            title = meta.title,
            body = meta.body,
            choices = meta.choices,
            answer = meta.answer,
            explain = meta.explain,
            difficulty = meta.difficulty
        )
    }

    // ----- 채점 (텍스트 + 선택적 필기 이미지) -----
    suspend fun grade(
        subject: String,
        answerText: String,
        elapsedSec: Int,
        expectedAnswer: String?,
        problemText: String?,
        processImage: Bitmap? // null 가능
    ): GradeFeedback = withContext(Dispatchers.IO) {
        val b64 = processImage?.let { bmpToBase64Png(it) }

        val r: GradeResult = ai.grade(
            subject = subject,
            answerText = answerText,
            expectedAnswer = expectedAnswer,
            problemText = problemText,
            elapsedSec = elapsedSec,
            processImageBase64 = b64
        )

        GradeFeedback(
            score = r.score,
            criteria = if (r.criteria.isEmpty())
                mapOf("정확도" to if (r.correct) 6 else 3, "접근법" to 5, "완성도" to 5, "창의성" to 5)
            else r.criteria,
            strengths = r.strengths,
            improvements = r.improvements,
            summary = r.summary,           // OpenAIInApp.grade에서 요약을 이미 조립
            aiConfidence = r.aiConfidence,
            timeSpent = r.timeSpent
        )
    }

    private fun bmpToBase64Png(bmp: Bitmap): String {
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

/* ---------- 앱 내부 사용 모델 ---------- */
data class Problem(
    val title: String,
    val body: String,
    val choices: List<String>,
    val answer: String,
    val explain: String,
    val difficulty: Int
)

data class GradeFeedback(
    val score: Int,
    val criteria: Map<String, Int>,     // 4지표(정확도/접근법/완성도/창의성)
    val strengths: List<String>,        // 잘한 점
    val improvements: List<String>,     // 개선 제안
    val summary: String,
    val aiConfidence: Int,
    val timeSpent: Int
)
