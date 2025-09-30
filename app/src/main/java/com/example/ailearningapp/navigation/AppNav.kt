// app/src/main/java/com/example/ailearningapp/navigation/AppNav.kt
package com.example.ailearningapp.navigation

import androidx.navigation.NavBackStackEntry
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// ✅ Enum은 대문자 컨벤션
enum class Subject { MATH, ENGLISH }

enum class GradeBand(val label: String) {
    ELEMENTARY_LOWER("초등 저(1~3)"),
    ELEMENTARY_UPPER("초등 고(4~6)"),
    MIDDLE("중학생"),
    HIGH("고등학생");

    companion object {
        fun fromLabel(label: String): GradeBand? = entries.firstOrNull { it.label == label }
    }
}

object Routes {
    // Entry / flows
    const val LOGIN = "login"
    const val GRADE = "grade"
    const val SUBJECT = "subject"
    const val SETTINGS = "settings"
    const val LEADERBOARD = "leaderboard"
    const val PROFILE_SETUP = "profile_setup"

    // ✅ solve 루트 그래프(공유 ViewModel용 부모 라우트)
    const val SOLVE_ROOT = "solve"
    const val ARG_SUBJECT = "subject"

    // 자식 목적지: solve/{subject}  (subject는 소문자 사용)
    const val SOLVE = "$SOLVE_ROOT/{$ARG_SUBJECT}"
    fun solve(subject: Subject) = "$SOLVE_ROOT/${subject.name.lowercase()}"

    // solve 그래프 내부의 결과 화면
    const val FEEDBACK = "feedback"

    // history?subject={subject}&grade={grade}
    private const val HISTORY_BASE = "history"
    const val Q_SUBJECT = "subject"
    const val Q_GRADE = "grade"
    const val HISTORY_PATTERN = "$HISTORY_BASE?$Q_SUBJECT={$Q_SUBJECT}&$Q_GRADE={$Q_GRADE}"

    /** 히스토리 딥링크 생성 */
    fun history(subject: Subject? = null, grade: GradeBand? = null): String {
        val params = buildList {
            subject?.let { add("$Q_SUBJECT=${it.name.lowercase().urlEncode()}") }
            grade?.let { add("$Q_GRADE=${it.name.urlEncode()}") }
        }
        return if (params.isEmpty()) HISTORY_BASE else "$HISTORY_BASE?${params.joinToString("&")}"
    }

    /** solve/{subject} 라우트에서 subject 안전 추출(대문자 Enum 복구) */
    fun NavBackStackEntry.requireSubjectArg(): Subject {
        val raw = arguments?.getString(ARG_SUBJECT) ?: error("missing arg: $ARG_SUBJECT")
        return runCatching { Subject.valueOf(raw.uppercase()) }
            .getOrElse { error("invalid subject: $raw") }
    }

    /** history 쿼리: subject nullable 추출 */
    fun NavBackStackEntry.optSubjectQuery(): Subject? =
        arguments?.getString(Q_SUBJECT)?.let { runCatching { Subject.valueOf(it.uppercase()) }.getOrNull() }

    /** history 쿼리: grade nullable 추출 */
    fun NavBackStackEntry.optGradeQuery(): GradeBand? =
        arguments?.getString(Q_GRADE)?.let { runCatching { GradeBand.valueOf(it) }.getOrNull() }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
}
