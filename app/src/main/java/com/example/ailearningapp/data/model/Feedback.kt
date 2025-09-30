// app/src/main/java/com/example/ailearningapp/data/model/Feedback.kt
package com.example.ailearningapp.data.model

import androidx.compose.runtime.Immutable

/**
 * AI 채점 결과 (도메인 모델)
 *
 * @param score        총점(0~100 권장) – 서버가 범위를 벗겨도 normalizedScore로 안전 사용
 * @param criteria     세부 채점 기준별 점수(0~100 권장)
 * @param summary      요약 피드백 텍스트
 * @param aiConfidence AI의 확신도(0~100), 없을 수 있음
 * @param timeSpent    풀이 시간(초), 없을 수 있음
 */
@Immutable
data class Feedback(
    val score: Int,
    val criteria: Map<String, Int>,
    val summary: String,
    val aiConfidence: Int?,
    val timeSpent: Int?
) {

    /** 0..100 으로 보정된 총점 */
    val normalizedScore: Int get() = score.coerceIn(0, 100)

    /** 0..100 으로 보정된 확신도 (null-safe) */
    val confidence0to100: Int? get() = aiConfidence?.coerceIn(0, 100)

    /** 합격/패스 기준(60점 이상을 기본값으로 가정) */
    val isPass: Boolean get() = normalizedScore >= 60

    /** UI용 짧은 배지 라벨 */
    val badgeLabel: String
        get() = when {
            normalizedScore >= 90 -> "🏆 최고"
            normalizedScore >= 80 -> "🎉 훌륭해요"
            normalizedScore >= 60 -> "👍 좋아요"
            normalizedScore >= 40 -> "🙂 노력 중"
            else -> "💪 다시 도전"
        }

    /** "m분 s초" 형태의 시간 라벨 (시간 미제공 시 대시) */
    val timeSpentLabel: String
        get() = timeSpent?.let { formatSec(it) } ?: "—"

    /** 기준 점수를 0..100으로 보정해 내림차순 정렬(그래프/리스트 표시용) */
    fun criteriaSorted(): List<Pair<String, Int>> =
        criteria.entries
            .map { it.key to it.value.coerceIn(0, 100) }
            .sortedByDescending { it.second }

    /** 기준 점수를 0..100으로 보정한 맵 */
    fun criteriaNormalized(): Map<String, Int> =
        criteria.mapValues { it.value.coerceIn(0, 100) }

    /** UI에 넣기 좋은 요약(기본 120자 컷) */
    fun shortSummary(maxLen: Int = 120): String =
        if (summary.length <= maxLen) summary else summary.take(maxLen - 1) + "…"

    private fun formatSec(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) "${m}분 ${s}초" else "${s}초"
    }
}
