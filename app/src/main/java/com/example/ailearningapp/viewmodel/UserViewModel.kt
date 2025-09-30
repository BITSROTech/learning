// app/src/main/java/com/example/ailearningapp/viewmodel/UserViewModel.kt
package com.example.ailearningapp.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ailearningapp.data.model.AuthUser
import com.example.ailearningapp.data.repository.AiRepository
import com.example.ailearningapp.data.repository.AuthRepository
import com.example.ailearningapp.navigation.GradeBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 앱 전역 사용자 상태(로그인, 학년 등)를 노출하는 뷰모델 */
data class UserUiState(
    val user: AuthUser? = null,
    val gradeBand: GradeBand = GradeBand.ELEMENTARY_UPPER,
    val loading: Boolean = false,
    val error: String? = null
)

class UserViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepo = AuthRepository(app)
    private val prefRepo = AiRepository(app)

    // 개별 소스 플로우
    private val userFlow = authRepo.currentUser()          // Flow<AuthUser?>
    private val gradeFlow = prefRepo.gradeBandFlow()       // Flow<GradeBand>

    // UI 부가 상태(loading/error)는 내부에서 Mutable로 관리
    private data class Extras(val loading: Boolean = false, val error: String? = null)
    private val extras = MutableStateFlow(Extras())

    // Hot-State: 화면에서 이거 하나만 collect 하세요
    val uiState: StateFlow<UserUiState> =
        combine(userFlow, gradeFlow, extras) { u, g, ex ->
            UserUiState(
                user = u,
                gradeBand = g,
                loading = ex.loading,
                error = ex.error
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserUiState()
        )

    fun clearError() {
        extras.value = extras.value.copy(error = null)
    }

    /* -------------------- GradeBand -------------------- */

    fun setGradeBand(grade: GradeBand) {
        viewModelScope.launch {
            runCatching { prefRepo.setGradeBand(grade.name) }
                .onFailure { e ->
                    extras.value = extras.value.copy(
                        error = "학년 저장 실패: ${e.message ?: "알 수 없는 오류"}"
                    )
                }
        }
    }

    /* -------------------- Google Sign-In -------------------- */

    /** Google 로그인 인텐트(Compose ActivityResultLauncher에 전달) */
    fun googleSignInIntent(): Intent = authRepo.googleSignInIntent()

    /** Google 로그인 결과 처리 */
    fun handleGoogleResult(
        data: Intent,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            extras.value = extras.value.copy(loading = true, error = null)
            runCatching { authRepo.handleGoogleResult(data) }
                .onSuccess { onSuccess() }
                .onFailure {
                    val msg = it.message ?: "Google 로그인 실패"
                    extras.value = extras.value.copy(error = msg)
                    onError(msg)
                }
            extras.value = extras.value.copy(loading = false)
        }
    }

    /* -------------------- Kakao Login -------------------- */

    fun kakaoLogin(
        activity: Activity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            extras.value = extras.value.copy(loading = true, error = null)
            runCatching { authRepo.kakaoLogin(activity) }
                .onSuccess { onSuccess() }
                .onFailure {
                    val msg = it.message ?: "Kakao 로그인 실패"
                    extras.value = extras.value.copy(error = msg)
                    onError(msg)
                }
            extras.value = extras.value.copy(loading = false)
        }
    }

    /* -------------------- Sign-out -------------------- */

    fun signOutAll(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            extras.value = extras.value.copy(loading = true, error = null)
            runCatching { authRepo.signOutAll() }
                .onSuccess { onDone?.invoke() }
                .onFailure { e ->
                    extras.value = extras.value.copy(
                        error = "로그아웃 실패: ${e.message ?: "알 수 없는 오류"}"
                    )
                }
            extras.value = extras.value.copy(loading = false)
        }
    }
}
