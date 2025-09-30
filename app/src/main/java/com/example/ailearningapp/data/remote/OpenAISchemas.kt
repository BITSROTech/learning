// app/src/main/java/com/example/ailearningapp/data/remote/OpenAISchemas.kt
package com.example.ailearningapp.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/* Cloud Run 게이트웨이 응답 DTO (Moshi) */
/* 리플렉션 + 기본값으로 누락 필드 방어, KSP 코드젠도 옵션으로 활성화 가능 */

@JsonClass(generateAdapter = true)
data class GenerateResponse(
    val ok: Boolean = false,
    val problem: ProblemDto? = null
)

@JsonClass(generateAdapter = true)
data class ProblemDto(
    val title: String = "",
    val body: String = "",
    val choices: List<String>? = null,
    val answer: String? = null,          // ← 누락 대비 nullable
    val explain: String? = null,         // ← 누락 대비 nullable
    @Json(name = "difficulty")
    val difficulty: Int? = null,         // ← ✅ 난이도(1~10)
    @Json(name = "difficulty_reason")
    val difficultyReason: String? = null,// ← ✅ 난이도 이유(옵션)
    @Json(name = "seed")
    val seed: String? = null             // ← ✅ 다양성 시드(옵션)
)

@JsonClass(generateAdapter = true)
data class GradeResponse(
    val ok: Boolean = false,
    val feedback: FeedbackDto? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackDto(
    val score: Int = 0,
    val criteria: Map<String, Int> = emptyMap(),
    val summary: String = "",
    val aiConfidence: Int? = null, // ← 누락 대비 nullable
    val timeSpent: Int? = null     // ← 누락 대비 nullable
)
