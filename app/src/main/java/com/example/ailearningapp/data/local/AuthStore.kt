// app/src/main/java/com/example/ailearningapp/data/local/AuthStore.kt
package com.example.ailearningapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ailearningapp.data.model.AuthProvider
import com.example.ailearningapp.data.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 앱 전역에서 단일 인스턴스로 써야 함 (Google 권장 패턴)
private val Context.dataStore by preferencesDataStore(name = "auth_store")

object AuthStore {
    // Keys
    private val K_PROVIDER = stringPreferencesKey("provider")
    private val K_UID = stringPreferencesKey("uid")
    private val K_NAME = stringPreferencesKey("name")
    private val K_EMAIL = stringPreferencesKey("email")
    private val K_PHOTO = stringPreferencesKey("photo")
    private val K_IDTOKEN = stringPreferencesKey("id_token")
    private val K_ACCESSTOKEN = stringPreferencesKey("access_token")

    /**
     * 저장된 사용자 정보를 스트림으로 반환.
     * - provider 가 없거나 파싱 실패하면 null
     * - 빈 문자열로 저장된 값은 null 로 복원
     */
    fun userFlow(ctx: Context): Flow<AuthUser?> =
        ctx.dataStore.data.map { pref ->
            val providerStr = pref[K_PROVIDER] ?: return@map null
            val provider = runCatching { AuthProvider.valueOf(providerStr) }.getOrNull()
                ?: return@map null

            AuthUser(
                provider = provider,
                uid = pref[K_UID].nullIfBlank(),
                name = pref[K_NAME].nullIfBlank(),
                email = pref[K_EMAIL].nullIfBlank(),
                photoUrl = pref[K_PHOTO].nullIfBlank(),
                idToken = pref[K_IDTOKEN].nullIfBlank(),
                accessToken = pref[K_ACCESSTOKEN].nullIfBlank()
            )
        }

    /** 현재 사용자 정보를 즉시 1회 읽기 (suspend) */
    suspend fun currentUserOnce(ctx: Context): AuthUser? = userFlow(ctx).first()

    /** 로그인 여부 스트림(true/false) */
    fun isLoggedInFlow(ctx: Context): Flow<Boolean> = userFlow(ctx).map { it != null }

    /**
     * 사용자 정보 저장.
     * - DataStore는 암호화되지 않음. 액세스 토큰을 저장하는 것이 민감하다면
     *   androidx.security.crypto(EncryptedFile/SharedPreferences) 또는
     *   자체 암호화 레이어와 함께 사용을 고려하세요.
     */
    suspend fun save(ctx: Context, user: AuthUser) {
        ctx.dataStore.edit { p ->
            p[K_PROVIDER] = user.provider.name
            p[K_UID] = user.uid.orEmpty()
            p[K_NAME] = user.name.orEmpty()
            p[K_EMAIL] = user.email.orEmpty()
            p[K_PHOTO] = user.photoUrl.orEmpty()
            p[K_IDTOKEN] = user.idToken.orEmpty()
            p[K_ACCESSTOKEN] = user.accessToken.orEmpty()
        }
    }

    /** 전체 삭제(로그아웃 시 호출) */
    suspend fun clear(ctx: Context) {
        ctx.dataStore.edit { it.clear() }
    }
}

/* ---------- small utils ---------- */

private fun String?.nullIfBlank(): String? =
    if (this.isNullOrBlank()) null else this
