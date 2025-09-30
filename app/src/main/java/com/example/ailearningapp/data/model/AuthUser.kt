// app/src/main/java/com/example/ailearningapp/data/model/AuthUser.kt
package com.example.ailearningapp.data.model

import androidx.compose.runtime.Immutable

enum class AuthProvider { Google, Kakao }

/**
 * 인증된 사용자 모델.
 * - tokens는 DataStore에 저장되지만, 로그 출력 등 외부로 노출할 땐 redacted() 사용 권장
 */
@Immutable
data class AuthUser(
    val provider: AuthProvider,
    val uid: String?,
    val name: String?,
    val email: String?,
    val photoUrl: String?,
    val idToken: String?,     // Google: ID Token / Kakao: null
    val accessToken: String?,  // Kakao: Access Token / Google: null
    val school: String? = null,  // 사용자의 학교명
    val grade: Int? = null,  // 사용자의 학년 (1-12)
    val totalScore: Int = 0  // 사용자의 총 누적 점수
) {
    val isGoogle: Boolean get() = provider == AuthProvider.Google
    val isKakao: Boolean get() = provider == AuthProvider.Kakao

    /** UI 표시용 이름 (이름 > 이메일 로컬파트 > '사용자') */
    fun displayName(): String =
        name ?: email?.substringBefore('@') ?: "사용자"

    /** 로그/디버그 출력 등 외부 공유용: 토큰 제거한 사본 */
    fun redacted(): AuthUser = copy(idToken = null, accessToken = null)

    /** 민감정보 마스킹된 문자열 (로그에 직접 쓰지 말고 redacted() 후 toString 사용 권장) */
    override fun toString(): String {
        val maskedEmail = email?.let { e ->
            val at = e.indexOf('@')
            if (at > 2) e.take(2) + "***" + e.substring(at) else "***"
        }
        return "AuthUser(provider=$provider, uid=$uid, name=$name, email=$maskedEmail, photoUrl=$photoUrl, idToken=${idToken?.let { "***" }}, accessToken=${accessToken?.let { "***" }})"
    }
}