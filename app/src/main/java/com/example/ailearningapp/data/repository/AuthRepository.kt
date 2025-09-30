@file:Suppress("DEPRECATION")

package com.example.ailearningapp.data.repository

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ailearningapp.R
import com.example.ailearningapp.data.local.AuthStore
import com.example.ailearningapp.data.model.AuthProvider
import com.example.ailearningapp.data.model.AuthUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.common.util.Utility
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ▼ Firestore 추가
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private const val TAG = "AuthRepo"

class AuthRepository(private val ctx: Context) {

    /* -------------------- Google(Firebase Auth) -------------------- */

    private val gso: GoogleSignInOptions by lazy {
        val webClientId = resolveDefaultWebClientId(ctx) // google-services.json이 생성한 default_web_client_id
        Log.i(TAG, "Using default_web_client_id=$webClientId, pkg=${ctx.packageName}")

        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId) // Firebase로 교환할 ID 토큰 요청
            .build()
    }

    @SuppressLint("DiscouragedApi")
    private fun resolveDefaultWebClientId(ctx: Context): String {
        // google-services.json이 제대로 붙었다면 이 리소스가 반드시 생성됨
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        require(id != 0) {
            "default_web_client_id 리소스를 찾을 수 없습니다. google-services.json을 app/에 넣고 Gradle Sync를 다시 하세요."
        }
        return ctx.getString(id)
    }

    private val googleClient: GoogleSignInClient by lazy { GoogleSignIn.getClient(ctx, gso) }

    /** 구글 로그인 인텐트 */
    fun googleSignInIntent(): Intent = googleClient.signInIntent

    /**
     * 구글 로그인 결과 처리 → Firebase로 교환까지 수행
     * @throws ApiException 실패 시 예외
     */
    suspend fun handleGoogleResult(data: Intent): AuthUser {
        return try {
            // 1) Google 결과 파싱
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)

            val googleIdToken = account.idToken
                ?: error("Google idToken이 비어있습니다. SHA-1 및 default_web_client_id를 확인하세요.")

            // 2) Firebase로 교환
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            val fUser = authResult.user ?: error("Firebase user가 null 입니다.")

            // 3) (선택) Firebase ID Token 가져오기(백엔드 검증 등에 활용 가능)
            val firebaseIdToken = runCatching {
                fUser.getIdToken(true).await().token
            }.getOrNull()

            // 4) 로컬 저장 + Firestore upsert
            val user = fUser.toAuthUser(firebaseIdToken, fallback = account)
            AuthStore.save(ctx, user)
            runCatching { upsertUserProfile(user) }
                .onFailure { Log.w(TAG, "Firestore upsert 실패(Google): ${it.message}") }

            Log.i(TAG, "Google/Firebase sign-in OK: uid=${user.uid}, email=${user.email}")
            user
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: code=${e.statusCode}, msg=${e.message}", e)
            throw e
        }
    }

    /** 앱 재실행 시 조용히 세션 복원 (Firebase 우선) */
    @Suppress("unused")
    suspend fun silentGoogleSignIn(): AuthUser? {
        // Firebase에 세션이 남아있다면 그것을 신뢰
        FirebaseAuth.getInstance().currentUser?.let { fUser ->
            val token = runCatching { fUser.getIdToken(false).await().token }.getOrNull()
            val user = fUser.toAuthUser(token, fallback = null)
            AuthStore.save(ctx, user)
            return user
        }

        // (보조) Play Services 캐시에 남아있다면 보여주기용으로만 복원
        return runCatching {
            val acc = GoogleSignIn.getLastSignedInAccount(ctx) ?: return null
            val user = acc.toAuthUser() // 토큰 교환은 안함
            AuthStore.save(ctx, user)
            user
        }.onFailure { Log.w(TAG, "silentGoogleSignIn failed: ${it.message}") }.getOrNull()
    }

    /** 구글 로그아웃 */
    suspend fun googleSignOut() {
        // Firebase 세션 종료
        FirebaseAuth.getInstance().signOut()
        // Google 클라이언트도 로그아웃(선택)
        runCatching { googleClient.signOut().await() }
            .onFailure { Log.w(TAG, "Google signOut failed: ${it.message}") }
    }

    /** 구글 계정 연결 해제(앱 권한 철회) */
    @Suppress("unused")
    suspend fun revokeGoogleAccess() {
        runCatching { googleClient.revokeAccess().await() }
            .onFailure { Log.w(TAG, "Google revokeAccess failed: ${it.message}") }
        // Firebase 쪽은 별도의 revoke 개념이 없으니 signOut만 수행
        FirebaseAuth.getInstance().signOut()
    }

    private fun GoogleSignInAccount.toAuthUser(): AuthUser =
        AuthUser(
            provider = AuthProvider.Google,
            uid = id,
            name = displayName,
            email = email,
            photoUrl = photoUrl?.toString(),
            idToken = idToken,   // Google ID Token (Firebase로 교환 이전 값)
            accessToken = null
        )

    private fun FirebaseUser.toAuthUser(idToken: String?, fallback: GoogleSignInAccount?): AuthUser =
        AuthUser(
            provider = AuthProvider.Google,
            uid = uid,
            name = displayName ?: fallback?.displayName,
            email = email ?: fallback?.email,
            photoUrl = photoUrl?.toString() ?: fallback?.photoUrl?.toString(),
            idToken = idToken,   // Firebase ID Token
            accessToken = null
        )

    /* -------------------- Firestore: 사용자 프로필 저장 -------------------- */

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** Firestore에 사용자 프로필 upsert (구글/카카오 공통) */
    suspend fun upsertUserProfile(user: AuthUser) {
        // Google은 FirebaseAuth uid 사용, Kakao는 임시 prefix (커스텀 토큰 쓰면 Firebase uid 사용 가능)
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        val docId = firebaseUid ?: "kakao:${user.uid ?: "unknown"}"

        val data = hashMapOf(
            "uid" to docId,
            "provider" to user.provider.name,
            "name" to (user.name ?: ""),
            "email" to (user.email ?: ""),
            "photoUrl" to (user.photoUrl ?: ""),
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(docId).set(data, SetOptions.merge()).await()
    }

    /* -------------------- Kakao -------------------- */

    /**
     * 카카오 로그인.
     * - 카카오톡 가능 시 우선 시도(사용자 취소면 그대로 throw)
     * - 기타 오류면 카카오계정 로그인으로 폴백
     */
    suspend fun kakaoLogin(activity: Activity): AuthUser {
        val client = UserApiClient.instance

        val token: OAuthToken = if (client.isKakaoTalkLoginAvailable(activity)) {
            runCatching {
                kakaoAwait { cb -> client.loginWithKakaoTalk(activity) { t, e -> cb(t, e) } }
            }.recoverCatching { e ->
                if (e is ClientError && e.reason == ClientErrorCause.Cancelled) throw e
                kakaoAwait { cb -> client.loginWithKakaoAccount(activity) { t, e2 -> cb(t, e2) } }
            }.getOrThrow()
        } else {
            kakaoAwait { cb -> client.loginWithKakaoAccount(activity) { t, e -> cb(t, e) } }
        }

        val me = kakaoAwait { cb -> client.me { user, error -> cb(user, error) } }

        val authUser = AuthUser(
            provider = AuthProvider.Kakao,
            uid = me.id?.toString(),
            name = me.kakaoAccount?.profile?.nickname,
            email = me.kakaoAccount?.email,
            photoUrl = me.kakaoAccount?.profile?.profileImageUrl,
            idToken = null,
            accessToken = token.accessToken
        )
        AuthStore.save(ctx, authUser)

        // Firestore upsert
        runCatching { upsertUserProfile(authUser) }
            .onFailure { Log.w(TAG, "Firestore upsert 실패(Kakao): ${it.message}") }

        Log.i(TAG, "Kakao sign-in OK: uid=${authUser.uid}, email=${authUser.email}")
        return authUser
    }

    /** 카카오 로그아웃(토큰 삭제). 로컬 저장소는 signOutAll()에서 정리 */
    suspend fun kakaoLogout() {
        runCatching {
            kakaoAwait { cb ->
                UserApiClient.instance.logout { error -> cb(Unit, error) }
            }
        }.onFailure { Log.w(TAG, "Kakao logout failed: ${it.message}") }
    }

    /* -------------------- Common -------------------- */

    fun currentUser(): Flow<AuthUser?> = AuthStore.userFlow(ctx)

    /** 모든 공급자 로그아웃 + 로컬 사용자 정보 삭제 */
    suspend fun signOutAll() {
        runCatching { googleSignOut() }
        runCatching { kakaoLogout() }
        AuthStore.clear(ctx)
    }

    /* -------------------- Debug utilities -------------------- */

    /** 현재 빌드의 카카오 KeyHash 출력 (경고 제거용 표기) */
    @Suppress("unused")
    fun dumpKakaoKeyHash(copyToClipboard: Boolean = false): String {
        val keyHash = Utility.getKeyHash(ctx)
        Log.i(
            TAG,
            "KAKAO keyHash=$keyHash, package=${ctx.packageName}, appKey=${ctx.getString(R.string.kakao_app_key)}"
        )
        if (copyToClipboard) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("kakao_key_hash", keyHash))
        }
        return keyHash
    }
}

/* ---------- Kakao 콜백 → 코루틴 변환 ---------- */
private suspend fun <T> kakaoAwait(block: (callback: (T?, Throwable?) -> Unit) -> Unit): T =
    suspendCancellableCoroutine { cont ->
        block { value, error ->
            if (!cont.isActive) return@block
            when {
                error != null -> cont.resumeWithException(error)
                value != null -> cont.resume(value)
                else -> cont.resumeWithException(IllegalStateException("Null result"))
            }
        }
    }
