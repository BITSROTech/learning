// app/src/main/java/com/example/ailearningapp/data/model/Problem.kt
package com.example.ailearningapp.data.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import java.math.MathContext
import kotlin.random.Random

@Immutable
data class Problem(
    val id: String,
    /** "math" | "english" (백엔드/라우팅과 일치) */
    val subject: String,
    val title: String,
    val body: String,
    /** 객관식이 아니면 빈 리스트로 둘 것 */
    val choices: List<String> = emptyList(),
    /** 정답(객관식: 보기에 대응하는 텍스트/번호/문자 가능, 주관식: 자유 텍스트/숫자) */
    val answer: String? = null,
    /** 해설(옵션) */
    val explain: String? = null,
    /** 난이도(1~10). 백엔드에서 미포함일 수 있어 null 허용 */
    val difficulty: Int? = null

) {

    /** 1~10 범위로 클램프된 난이도 (없으면 null) */
    val difficultyClamped: Int?
        get() = difficulty?.coerceIn(1, 10)

    /** 객관식 여부 */
    val isMultipleChoice: Boolean get() = choices.isNotEmpty()

    /** 제목이 비어있으면 본문 첫 줄을 대체 제목으로 */
    val displayTitle: String
        get() = title.ifBlank { body.lineSequence().firstOrNull().orEmpty() }

    /** A, B, C ... 라벨과 보기 텍스트 조합 */
    fun labeledChoices(): List<String> =
        choices.mapIndexed { idx, text -> "${indexToLetter(idx)}. $text" }

    /** 정답이 가리키는 보기 인덱스(0-base) 추정: "2", "B", "b", 보기 텍스트 등 모두 처리 */
    fun correctChoiceIndex(): Int? {
        if (!isMultipleChoice) return null
        val ans = answer?.trim().orEmpty()
        if (ans.isEmpty()) return null

        // 1) 숫자형 ("1".."n") → 0-base
        ans.toIntOrNull()?.let { n ->
            if (n in 1..choices.size) return n - 1
        }
        // 2) 문자형 ("A".."Z")
        if (ans.length == 1) {
            val idx = letterToIndex(ans[0])
            if (idx in choices.indices) return idx
        }
        // 3) 보기 텍스트가 그대로 들어온 케이스 (공백/대소문자 무시)
        val normAns = ans.normalize()
        choices.forEachIndexed { i, c ->
            if (c.normalize() == normAns) return i
        }
        return null
    }

    /**
     * 사용자 입력이 정답과 일치하는지 대략 판정.
     * - 객관식: 번호/문자/텍스트 모두 비교
     * - 주관식: (1) 숫자 비교(관용, 분수 "3/4" 허용, 허용 오차 1e-9) → (2) 텍스트 정규화 비교
     */
    fun isCorrect(userAnswer: String?): Boolean {
        val ua = userAnswer?.trim().orEmpty()
        if (ua.isEmpty()) return false

        return if (isMultipleChoice) {
            val userIdx = parseChoiceIndex(ua)
            val correctIdx = correctChoiceIndex()
            when {
                userIdx != null && correctIdx != null -> userIdx == correctIdx
                else -> {
                    val normU = ua.normalize()
                    val normA = answer?.normalize() ?: return false
                    normU == normA
                }
            }
        } else {
            // 주관식: 숫자 우선 비교 후 텍스트 비교
            val (un, an) = parseNumber(ua) to parseNumber(answer ?: "")
            if (un != null && an != null) {
                numbersAlmostEqual(un, an)
            } else {
                ua.normalize() == (answer?.normalize() ?: return false)
            }
        }
    }

    /**
     * 보기 셔플(정답 인덱스 보정 포함)
     * @return Pair(셔플된 Problem, 셔플 후 정답 인덱스 or null)
     */
    fun shuffled(seed: Long? = null): Pair<Problem, Int?> {
        if (!isMultipleChoice) return this to null
        val indices = choices.indices.toMutableList()
        val rnd = if (seed == null) Random.Default else Random(seed)
        indices.shuffle(rnd)

        val newChoices = indices.map { choices[it] }
        val oldCorrect = correctChoiceIndex()
        val newCorrect = oldCorrect?.let { indices.indexOf(it) }.takeIf { it != -1 }

        // answer가 텍스트라면 그대로 두어도 일치 비교에 문제 없음
        val shuffled = copy(choices = newChoices)
        return shuffled to newCorrect
    }

    /* ---------- 내부 유틸 ---------- */

    private fun indexToLetter(i: Int): String = ('A' + i).toString()
    private fun letterToIndex(ch: Char): Int = (ch.uppercaseChar() - 'A')

    private fun parseChoiceIndex(input: String): Int? {
        val t = input.trim()
        // "2" → 1-base
        t.toIntOrNull()?.let { n -> if (n in 1..choices.size) return n - 1 }
        // "B"
        if (t.length == 1) {
            val idx = letterToIndex(t[0])
            if (idx in choices.indices) return idx
        }
        // 텍스트 일치
        val norm = t.normalize()
        choices.forEachIndexed { i, c -> if (c.normalize() == norm) return i }
        return null
    }

    private fun String.normalize(): String =
        lowercase().replace("\\s+".toRegex(), " ").trim()

    /** "1,234.5" / "  3/4 " / "2.0" 등 관용 파싱 */
    private fun parseNumber(s: String): BigDecimal? {
        val t = s.trim().replace(",", "")
        if (t.isEmpty()) return null
        // 분수 "a/b"
        if (t.contains('/')) {
            val parts = t.split('/')
            if (parts.size == 2) {
                val a = parts[0].toBigDecimalOrNull()
                val b = parts[1].toBigDecimalOrNull()
                if (a != null && b != null && b.compareTo(BigDecimal.ZERO) != 0) {
                    return a.divide(b, MathContext.DECIMAL64)
                }
            }
        }
        return t.toBigDecimalOrNull()
    }

    /** 숫자 비교: 상대/절대 오차 허용 */
    private fun numbersAlmostEqual(a: BigDecimal, b: BigDecimal): Boolean {
        val scaleA = a.stripTrailingZeros()
        val scaleB = b.stripTrailingZeros()
        if (scaleA.compareTo(scaleB) == 0) return true

        // 허용 오차: max(1e-9, 1e-9 * max(|a|, |b|))
        val absA = scaleA.abs()
        val absB = scaleB.abs()
        val rel = maxOf(absA, absB).multiply(BigDecimal("1e-9"))
        val eps = if (rel < BigDecimal("1e-9")) BigDecimal("1e-9") else rel

        val diff = scaleA.subtract(scaleB).abs()
        return diff <= eps
    }
}
