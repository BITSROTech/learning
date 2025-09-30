// app/src/main/java/com/example/ailearningapp/ui/screens/ProblemSolveScreen.kt
package com.example.ailearningapp.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ailearningapp.ui.components.DrawingCanvas
import com.example.ailearningapp.ui.components.ProblemBody
import com.example.ailearningapp.ui.components.rememberCanvasController
import com.example.ailearningapp.viewmodel.SolveViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemSolveScreen(
    subject: String,
    onShowFeedback: () -> Unit,
    onBack: () -> Unit,
    vm: SolveViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val ui by vm.uiState.collectAsState()

    LaunchedEffect(subject) { vm.loadNewProblem(subject) }

    var showCanvas by rememberSaveable { mutableStateOf(false) }
    var canvasBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val canvas = rememberCanvasController()

    val screenH = LocalConfiguration.current.screenHeightDp
    val canvasHeight = (screenH * 0.45f).dp

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(showCanvas) {
        if (showCanvas) {
            scope.launch {
                kotlinx.coroutines.yield()
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.resumeGate()
                Lifecycle.Event.ON_STOP  -> vm.pauseGate()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopGate()
        }
    }

    // ===== 드래그 가능한 설정 FAB 위치(정수 오프셋으로 저장) =====
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val screenWpx = with(density) { conf.screenWidthDp.dp.toPx() }
    val screenHpx = with(density) { conf.screenHeightDp.dp.toPx() }
    val fabSizePx = with(density) { 56.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }

    var fabX by rememberSaveable { mutableIntStateOf((screenWpx - fabSizePx - marginPx).roundToInt()) }
    var fabY by rememberSaveable { mutableIntStateOf((screenHpx * 0.7f).roundToInt()) }

    fun clampX(x: Int): Int = x.coerceIn(0, (screenWpx - fabSizePx).roundToInt())
    fun clampY(y: Int): Int = y.coerceIn(0, (screenHpx - fabSizePx).roundToInt())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("문제 풀기") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = { showCanvas = !showCanvas }) {
                        Text(if (showCanvas) "숨기기" else "판서하기")
                    }
                }
            )
        }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // ------ 본문 ------
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (ui.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    if (ui.loading || ui.problem == null) {
                        ProblemLoadingSkeleton()
                    } else {
                        val problem = ui.problem!!

                        Text(
                            text = problem.title.ifBlank { "새 문제 생성 중..." },
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(8.dp))

                        val actual = ui.actualDifficulty ?: problem.difficulty
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) { DifficultyBadgeBar(level = actual) }

                        Spacer(Modifier.height(12.dp))

                        ProblemBody(
                            body = problem.body,
                            modifier = Modifier.fillMaxWidth(),
                            fontSizeSp = 18
                        )

                        val choices = problem.choices
                        if (choices.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("선택지", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                choices.forEach { choice ->
                                    val selected = ui.answerText.trim() == choice
                                    ChoiceMathChip(
                                        text = choice,
                                        selected = selected,
                                        onClick = { vm.updateAnswer(choice) }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (ui.partsCount > 1) {
                            val circled = listOf('①','②','③','④','⑤','⑥','⑦','⑧','⑨','⑩')
                            Text("각 문항의 최종 답을 입력하세요.", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            repeat(ui.partsCount) { idx ->
                                val label = if (idx < circled.size) "${circled[idx]} 문항 답" else "문항 ${idx + 1} 답"
                                OutlinedTextField(
                                    value = ui.answerParts.getOrElse(idx) { "" },
                                    onValueChange = { vm.updateAnswerPart(idx, it) },
                                    label = { Text(label) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !ui.submitting
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        } else {
                            OutlinedTextField(
                                value = ui.answerText,
                                onValueChange = vm::updateAnswer,
                                label = { Text("최종 정답(텍스트)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !ui.submitting
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        if (showCanvas) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = { canvas.undo() }, enabled = !ui.submitting) { Text("되돌리기") }
                                OutlinedButton(onClick = { canvas.redo() }, enabled = !ui.submitting) { Text("다시") }
                                OutlinedButton(onClick = {
                                    canvas.clear()
                                    vm.clearCanvas()
                                }, enabled = !ui.submitting) { Text("지우기") }
                            }
                            Spacer(Modifier.height(8.dp))

                            Surface(
                                tonalElevation = 2.dp,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp)
                                    .height(canvasHeight)
                            ) {
                                DrawingCanvas(
                                    modifier = Modifier.fillMaxSize(),
                                    onInkChanged = { len -> vm.onInkChanged(len) },
                                    onExport = { bmp -> canvasBitmap = bmp },
                                    clearBus = vm.clearCanvasRequest,
                                    controller = canvas
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                        }

                        Text("풀이 시간: ${ui.elapsedSec}s / 최소 ${ui.minSec}s")
                        Text("필기 길이: ${ui.inkLength} / 최소 ${ui.minInk}")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        onClick = {
                            canvas.clear()
                            vm.clearCanvas()
                        },
                        enabled = !ui.submitting
                    ) { Text("지우기") }

                    var lastClickMs by remember { mutableLongStateOf(0L) }

                    Button(
                        enabled = ui.isSubmitEnabled && !ui.submitting,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastClickMs < 500) return@Button
                            lastClickMs = now

                            val bmp = canvas.exportTrimmedBitmap()
                                ?: canvas.exportBitmap()
                                ?: canvasBitmap
                            if (bmp == null) return@Button

                            vm.submit(bmp, subject) { onShowFeedback() }
                        }
                    ) { Text(if (ui.submitting) "채점 중..." else "AI 채점하기") }
                }

                ui.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            // ------ 드래그 가능한 설정 FAB (오버레이) ------
            FloatingActionButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .offset { IntOffset(fabX, fabY) }
                    .pointerInput(Unit) {
                        detectDragGestures(onDrag = { change, drag ->
                            change.consume()
                            fabX = clampX(fabX + drag.x.roundToInt())
                            fabY = clampY(fabY + drag.y.roundToInt())
                        })
                    }
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "설정")
            }
        }
    }
}

/* -------------------- 선택지: 수식 + 클릭 오버레이 -------------------- */

@Composable
private fun ChoiceMathChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)) {
            ProblemBody(body = text, modifier = Modifier.wrapContentWidth(), fontSizeSp = 16)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick)
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

/* -------------------- 로딩 스켈레톤 -------------------- */

@Composable
private fun ProblemLoadingSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(28.dp)
                .clip(shape)
                .background(base)
        )
        Box(
            Modifier
                .width(140.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(base)
        )
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(shape)
                    .background(base)
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(shape)
                .background(base)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    }
}

/* -------------------- 난이도 배치바 (1~5) -------------------- */
@Composable
private fun DifficultyBadgeBar(
    level: Int,
    modifier: Modifier = Modifier
) {
    val clamped = level.coerceIn(1, 5)
    val tag = when (clamped) {
        in 1..2 -> "초급"
        in 4..5 -> "상급"
        else -> "중급"
    }

    Column(
        modifier = modifier.widthIn(min = 140.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "출제 난이도 $clamped/5 · $tag",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        DifficultySegments(
            total = 5,
            filled = clamped,
            modifier = Modifier
                .width(160.dp)
                .height(10.dp)
        )
    }
}

@Composable
private fun DifficultySegments(
    total: Int,
    filled: Int,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val color = if (i < filled) activeColor else inactive
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
