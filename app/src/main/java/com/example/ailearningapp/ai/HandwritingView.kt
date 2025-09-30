// app/src/main/java/com/example/ailearningapp/ai/HandwritingView.kt
package com.example.ailearningapp.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 한 획(Stroke) 단위 데이터 */
private data class Stroke(
    val path: Path,
    @ColorInt val color: Int,
    val width: Float
)

/**
 * 손글씨 캔버스 View.
 * - 기본 펜(색/굵기) 지원
 * - 스타일러스 전용 입력 옵션(팜 리젝션)
 * - Undo/Redo
 * - 잉크 길이(px) 누적 측정
 * - 비트맵 내보내기(원본/트리밍)
 */
@Suppress("DEPRECATION")
class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /* ---------- Config ---------- */

    /** 펜 색 */
    @ColorInt
    var penColor: Int = Color.BLACK

    /** 펜 굵기(px) */
    var penWidth: Float = 8f

    /** 스타일러스 전용 입력 (true면 손가락/손바닥 무시) */
    var stylusOnly: Boolean = false

    /** 이동 노이즈 필터링 임계값(px). 이 값보다 짧은 이동은 무시 */
    var minMoveThreshold: Float = 1.5f

    /** 잉크 길이 변경 콜백(px 정수, 누적) */
    var onInkChanged: ((Int) -> Unit)? = null

    /* ---------- Internal state ---------- */

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = ArrayDeque<Stroke>() // Undo 후 되돌리기 용

    private var currentStroke: Stroke? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    /** 잉크 길이 누적(px) */
    var inkLengthPx: Int = 0
        private set

    init {
        // 하드웨어 가속 기본 켜짐. CLEAR 모드 미사용(배경색으로 그리는 방식), 안전함.
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /* ---------- Public API ---------- */

    /** 모든 획 삭제 */
    fun clearAll() {
        strokes.clear()
        redoStack.clear()
        currentStroke = null
        inkLengthPx = 0
        onInkChanged?.invoke(inkLengthPx)
        invalidate()
    }

    /** 마지막 획 되돌리기 */
    fun undo() {
        if (currentStroke != null) return
        val s = strokes.removeLastOrNull() ?: return
        redoStack.addLast(s)
        invalidate()
    }

    /** Undo 되돌리기 취소 */
    fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        strokes.add(s)
        invalidate()
    }

    /** 현재 그림을 비트맵으로 내보내기 (배경색 포함) */
    fun exportBitmap(@ColorInt bgColor: Int = Color.WHITE): Bitmap {
        val w = max(width, 1)
        val h = max(height, 1)
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        c.drawColor(bgColor)
        drawToCanvas(c)
        return bmp
    }

    /**
     * 여백(배경색) 트리밍해서 비트맵 내보내기
     * @param bgColor 배경색으로 간주할 색
     * @param marginPx 잘라낸 후 사방에 남길 여백
     * @param tolerance 색 허용 오차(0~255). 페인트 안티앨리어싱 가장자리를 위해 약간의 여유.
     */
    fun exportTrimmedBitmap(
        @ColorInt bgColor: Int = Color.WHITE,
        marginPx: Int = 16,
        tolerance: Int = 8
    ): Bitmap {
        val full = exportBitmap(bgColor)
        val rect = findContentBounds(full, bgColor, tolerance) ?: return full // 잉크 없음
        val left = max(0, rect.left - marginPx)
        val top = max(0, rect.top - marginPx)
        val right = min(full.width, rect.right + marginPx)
        val bottom = min(full.height, rect.bottom + marginPx)
        return Bitmap.createBitmap(full, left, top, right - left, bottom - top)
    }

    /* ---------- Rendering ---------- */

    override fun onDraw(canvas: Canvas) {
        drawToCanvas(canvas)
    }

    private fun drawToCanvas(canvas: Canvas) {
        // 누적 그리기(간단/안전). 필요 시 오프스크린 버퍼 최적화로 바꿀 수 있음.
        for (s in strokes) {
            paint.color = s.color
            paint.strokeWidth = s.width
            canvas.drawPath(s.path, paint)
        }
        currentStroke?.let {
            paint.color = it.color
            paint.strokeWidth = it.width
            canvas.drawPath(it.path, paint)
        }
    }

    /* ---------- Input ---------- */

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 스타일러스 전용 옵션: DOWN 시점의 toolType 확인
        if (stylusOnly) {
            val toolType = event.getToolType(event.actionIndex)
            if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                toolType != MotionEvent.TOOL_TYPE_STYLUS
            ) {
                return false
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ★ 부모 스크롤 가로채기 방지 (드로잉 동안)
                parent?.requestDisallowInterceptTouchEvent(true)

                activePointerId = event.getPointerId(0)
                redoStack.clear()

                val x = event.x
                val y = event.y
                lastX = x; lastY = y

                currentStroke = Stroke(
                    path = Path().apply { moveTo(x, y) },
                    color = penColor,
                    width = penWidth
                )
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 멀티터치가 들어오면 드로잉 유지 위해 부모 인터셉트 계속 차단
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // ★ 이동 중에도 계속 차단
                parent?.requestDisallowInterceptTouchEvent(true)

                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                val x = event.getX(idx)
                val y = event.getY(idx)

                // 직전 좌표 저장
                val prevX = lastX
                val prevY = lastY

                val dx = x - prevX
                val dy = y - prevY
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (dist >= minMoveThreshold) {
                    currentStroke?.path?.lineTo(x, y)

                    // 잉크 길이 누적
                    inkLengthPx += dist.toInt()
                    onInkChanged?.invoke(inkLengthPx)

                    // 변경 구간만 무효화
                    val pad = (penWidth * 1.5f).toInt()
                    val left = min(prevX, x) - pad
                    val top = min(prevY, y) - pad
                    val right = max(prevX, x) + pad
                    val bottom = max(prevY, y) + pad

                    invalidate(
                        left.toInt().coerceAtLeast(0),
                        top.toInt().coerceAtLeast(0),
                        right.toInt().coerceAtMost(width),
                        bottom.toInt().coerceAtMost(height)
                    )

                    lastX = x; lastY = y
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 현재 활성 포인터가 떼졌다면 다른 포인터로 승계
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (event.actionIndex == 0) 1 else 0
                    if (newIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newIndex)
                        lastX = event.getX(newIndex)
                        lastY = event.getY(newIndex)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 스트로크 확정
                currentStroke?.let { s ->
                    strokes.add(s)
                }
                currentStroke = null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                invalidate()

                // ★ 부모 스크롤 차단 해제
                parent?.requestDisallowInterceptTouchEvent(false)

                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /* ---------- Helpers ---------- */

    private fun findContentBounds(
        bmp: Bitmap,
        @ColorInt bgColor: Int,
        tolerance: Int
    ): Rect? {
        // 좌/우/상/하 스캔으로 잉크 영역 찾기
        val w = bmp.width
        val h = bmp.height
        var left = w
        var right = -1
        var top = h
        var bottom = -1

        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)

        val line = IntArray(w)

        for (y in 0 until h) {
            bmp.getPixels(line, 0, w, 0, y, w, 1)
            var rowHasInk = false
            for (x in 0 until w) {
                val c = line[x]
                val a = Color.alpha(c)
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                val isInk = a > 0 &&
                        (kotlin.math.abs(r - bgR) > tolerance ||
                                kotlin.math.abs(g - bgG) > tolerance ||
                                kotlin.math.abs(b - bgB) > tolerance)
                if (isInk) {
                    rowHasInk = true
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
            if (rowHasInk) {
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right >= left && bottom >= top) Rect(left, top, right, bottom) else null
    }
}
