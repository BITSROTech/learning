// app/src/main/java/com/example/ailearningapp/viewmodel/SolveViewModel.kt
package com.example.ailearningapp.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ailearningapp.data.ai.OpenAIInApp
import com.example.ailearningapp.data.local.Prefs
import com.example.ailearningapp.data.repo.GradeFeedback
import com.example.ailearningapp.data.repo.Problem
import com.example.ailearningapp.data.repo.ProblemRepository
import com.example.ailearningapp.navigation.GradeBand
import com.example.ailearningapp.data.repository.UserProfileRepository
import com.example.ailearningapp.data.model.DifficultyLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

data class SolveUiState(
    val problem: Problem? = null,

    // ── 답 입력 상태 ──
    val answerText: String = "",          // 단일 문제용
    val partsCount: Int = 1,              // 감지된 문항 수(1이면 단일)
    val answerParts: List<String> = emptyList(),  // 다문항용 입력값들

    // ── 게이트(찍기 방지) ──
    val minSec: Int = 20,                 // 최소 시간(초)
    val minInk: Int = 120,                // 최소 잉크(px)
    val elapsedSec: Int = 0,
    val inkLength: Int = 0,
    val isSubmitEnabled: Boolean = false,

    // ── UI 상태 ──
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,

    // ── 난이도 상태 ──
    /** 생성 요청에 사용할 목표 난이도(1~10). 기본 5 */
    val difficultyTarget: Int = 3,
    /** 실제 난이도(표시용). 생성된 Problem의 difficulty */
    val actualDifficulty: Int? = null
)

class SolveViewModel(app: Application) : AndroidViewModel(app) {

    // 인앱 OpenAI 레포지토리(원샷 생성)
    private val repo = ProblemRepository(OpenAIInApp())
    
    // 사용자 프로필 저장소  
    private val profileRepo = UserProfileRepository(app)

    private val _ui = MutableStateFlow(SolveUiState())
    val uiState = _ui.asStateFlow()

    /** 캔버스 외부 초기화 신호 */
    val clearCanvasRequest = MutableSharedFlow<Unit>()

    /** 게이트(타이머) 작업 핸들 */
    private var gateJob: Job? = null

    /** 최근 채점 결과 (Feedback 화면에서 구독) */
    private val _lastFeedback = MutableStateFlow<GradeFeedback?>(null)
    val lastFeedback = _lastFeedback.asStateFlow()

    /** 마지막으로 요청한 과목(난이도 변경 시 재요청에 사용) */
    private var lastSubject: String? = null

    /** 외부에서 현재 과목을 필요로 할 때 사용 (기본 "math") */
    val currentSubject: String
        get() = lastSubject ?: "math"

    // ───────────────────────── 생성(원샷) ─────────────────────────

    /** Prefs의 학년대를 레벨 코드로 변환 */
    private fun levelCodeFrom(band: GradeBand): String = when (band) {
        GradeBand.ELEMENTARY_LOWER -> "elementary_low"
        GradeBand.ELEMENTARY_UPPER -> "elementary_high"
        GradeBand.MIDDLE           -> "middle_school"
        GradeBand.HIGH             -> "high_school"
    }

    /** 새 문제 로드(원샷 생성) */
    fun loadNewProblem(subject: String) {
        lastSubject = subject

        // 기존 타이머 중지
        gateJob?.cancel()

        viewModelScope.launch {
            // 초기 로딩 상태로 리셋 (사용자 설정/난이도는 유지)
            _ui.value = _ui.value.copy(
                problem = null,
                loading = true,
                error = null,
                // 답 상태 초기화
                answerText = "",
                answerParts = emptyList(),
                partsCount = 1,
                // 게이트 초기화
                inkLength = 0,
                elapsedSec = 0,
                isSubmitEnabled = false,
                actualDifficulty = null
            )

            // 현재 설정된 학년대 → 레벨 코드로
            val band = Prefs.gradeBandFlow(getApplication()).first()
            val levelCode = levelCodeFrom(band)

            runCatching {
                repo.generate(
                    subject = subject,
                    level = levelCode,
                    difficulty = _ui.value.difficultyTarget
                )
            }.onSuccess { p ->
                // 본문에서 문항 수 감지
                val parts = derivePartsCount(p.body)
                _ui.value = _ui.value.copy(
                    problem = p,
                    loading = false,
                    error = null,
                    actualDifficulty = p.difficulty,
                    // 표시/후속 생성 동기화를 위해 target도 실제 난이도에 맞춤
                    difficultyTarget = p.difficulty.coerceIn(1, 5),
                    // 다문항 입력 초기화
                    partsCount = parts,
                    answerParts = List(parts) { "" }
                )
                // 문제 로드가 끝나면 게이트(타이머) 시작
                startGate()
            }.onFailure { e ->
                _ui.value = _ui.value.copy(
                    loading = false,
                    error = "문제 불러오기 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    /** 본문에서 ①~⑩, 1), 2., (3) 등 패턴을 찾아 최대 5개까지 감지 */
    private fun derivePartsCount(body: String): Int {
        val circled = "①②③④⑤⑥⑦⑧⑨⑩".toCharArray()
        val circledCnt = circled.count { body.contains(it) }
        if (circledCnt >= 2) return circledCnt.coerceIn(2, 5)

        val regexes = listOf(
            Regex("(?m)^\\s*\\(?([1-9])\\)\\s"),   // 1) 2) (3)
            Regex("(?m)^\\s*([1-9])\\.\\s"),       // 1. 2.
            Regex("(?m)^\\s*([1-9])\\s")           // "1 " 시작
        )
        val hits = regexes.maxOf { r -> r.findAll(body).count() }
        return hits.coerceIn(1, 5).let { if (it == 1) 1 else it }
    }

    /** 난이도 직접 설정(1~10) — 기본 동작: 즉시 재생성 */
    fun setDifficultyTarget(value: Int, autoReload: Boolean = true) {
        val clamped = value.coerceIn(1, 5)
        if (_ui.value.difficultyTarget == clamped) return
        _ui.value = _ui.value.copy(difficultyTarget = clamped)
        if (autoReload) reloadCurrentSubject()
    }

    /** 마지막 과목으로 재로딩 */
    private fun reloadCurrentSubject() {
        val s = lastSubject ?: return
        loadNewProblem(s)
    }

    // ────────────────────── 게이트 타이머 수명 관리 ──────────────────────

    /** 찍기 방지 타이머 시작(기존 실행 중이면 교체) */
    fun startGate() {
        gateJob?.cancel()
        gateJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _ui.value = _ui.value.copy(elapsedSec = _ui.value.elapsedSec + 1)
                checkGate()
            }
        }
    }

    /** 화면 표시(START/RESUME)에 맞춰 재개 */
    fun resumeGate() {
        if (gateJob == null && _ui.value.problem != null && !_ui.value.submitting) {
            startGate()
        }
    }

    /** 화면 비가시(STOP/PAUSE) 시 일시정지 */
    fun pauseGate() {
        gateJob?.cancel()
        gateJob = null
    }

    /** 화면 dispose/VM clear 시 완전 정지 */
    fun stopGate() {
        gateJob?.cancel()
        gateJob = null
    }

    // ───────────────────────── 입력 업데이트 ─────────────────────────

    /** 단일 답 텍스트 변경 */
    fun updateAnswer(s: String) {
        _ui.value = _ui.value.copy(answerText = s)
        checkGate()
    }

    /** 다문항 답 텍스트 변경 */
    fun updateAnswerPart(index: Int, text: String) {
        val parts = _ui.value.answerParts.toMutableList()
        if (index in parts.indices) {
            parts[index] = text
            _ui.value = _ui.value.copy(answerParts = parts)
            checkGate()
        }
    }

    /** 잉크 길이 갱신 (감소하지 않도록 보정) */
    fun onInkChanged(len: Int) {
        _ui.value = _ui.value.copy(inkLength = max(_ui.value.inkLength, len))
        checkGate()
    }

    /** 제출 가능 여부 계산 */
    private fun checkGate() {
        val u = _ui.value

        val answersFilled =
            if (u.partsCount > 1)
                (u.answerParts.size == u.partsCount && u.answerParts.all { it.isNotBlank() })
            else
                u.answerText.isNotBlank()

        val canSubmit =
            u.elapsedSec >= u.minSec &&
                    u.inkLength >= u.minInk &&
                    answersFilled &&
                    !u.loading &&
                    !u.submitting &&
                    u.problem != null

        if (u.isSubmitEnabled != canSubmit) {
            _ui.value = u.copy(isSubmitEnabled = canSubmit)
        }
    }

    /** 캔버스 지우기 */
    fun clearCanvas() = viewModelScope.launch {
        clearCanvasRequest.emit(Unit)
        // 잉크 길이도 초기화
        _ui.value = _ui.value.copy(inkLength = 0)
        checkGate()
    }

    // ───────────────────────── 제출/채점 ─────────────────────────

    /** 제출 → 채점 */
    fun submit(canvasBitmap: Bitmap?, subject: String, onDone: () -> Unit) {
        val u = _ui.value
        val prob = u.problem ?: return
        if (u.submitting) return

        // 다문항이면 답을 합쳐서 보냄(백엔드에서 문맥으로 평가)
        val finalAnswer = if (u.partsCount > 1) {
            u.answerParts.joinToString(" || ").trim()
        } else {
            u.answerText.trim()
        }

        viewModelScope.launch {
            // 제출 시작
            _ui.value = _ui.value.copy(submitting = true, error = null)

            runCatching {
                repo.grade(
                    subject = subject,
                    answerText = finalAnswer,
                    elapsedSec = u.elapsedSec,
                    expectedAnswer = prob.answer,   // 생성에서 받은 정답(다문항일 수도 있음)
                    problemText = prob.body,
                    processImage = canvasBitmap
                )
            }.onSuccess { fb ->
                _lastFeedback.value = fb
                
                // 정답인 경우 점수 추가
                if (fb.isCorrect) {
                    viewModelScope.launch {
                        val difficulty = prob.difficulty ?: 3 // 기본 난이도 3
                        val level = DifficultyLevel.fromDifficulty(difficulty)
                        profileRepo.addScore(level)
                    }
                }
                
                // 제출 완료 후 타이머 정지(선택)
                pauseGate()
                onDone()
            }.onFailure { e ->
                _ui.value = _ui.value.copy(
                    error = "채점 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }

            // 제출 끝
            _ui.value = _ui.value.copy(submitting = false)
            checkGate()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopGate()
    }
}
