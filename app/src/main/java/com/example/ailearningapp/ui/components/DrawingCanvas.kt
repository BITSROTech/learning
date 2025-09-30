// app/src/main/java/com/example/ailearningapp/ui/components/DrawingCanvas.kt
package com.example.ailearningapp.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ailearningapp.ai.HandwritingView
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

/* ---------- CanvasController ---------- */
class CanvasController internal constructor() {
    internal var view: HandwritingView? = null
    fun undo() = view?.undo()
    fun redo() = view?.redo()
    fun clear() = view?.clearAll()
    fun exportBitmap(bgColor: Int = android.graphics.Color.WHITE): Bitmap? =
        view?.exportBitmap(bgColor)
    fun exportTrimmedBitmap(
        bgColor: Int = android.graphics.Color.WHITE,
        marginPx: Int = 16,
        tolerance: Int = 8
    ): Bitmap? = view?.exportTrimmedBitmap(bgColor, marginPx, tolerance)
}
@Composable
fun rememberCanvasController(): CanvasController = remember { CanvasController() }

/* ---------- DrawingCanvas ---------- */
@OptIn(FlowPreview::class)
@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    onInkChanged: (Int) -> Unit,
    onExport: (Bitmap) -> Unit,
    clearBus: MutableSharedFlow<Unit>? = null,
    controller: CanvasController? = null   // ✅ 추가
) {
    val onInkChangedState by rememberUpdatedState(onInkChanged)
    val onExportState by rememberUpdatedState(onExport)

    var viewRef by remember { mutableStateOf<HandwritingView?>(null) }
    val inkBus = remember { MutableSharedFlow<Int>(extraBufferCapacity = 16) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            HandwritingView(ctx).also { hv ->
                viewRef = hv
                controller?.view = hv        // ✅ attach
                hv.onInkChanged = { len ->
                    onInkChangedState(len)
                    inkBus.tryEmit(len)
                }
            }
        },
        update = { hv ->
            controller?.view = hv            // ✅ re-attach 최신 참조
            hv.onInkChanged = { len ->
                onInkChangedState(len)
                inkBus.tryEmit(len)
            }
        }
    )

    LaunchedEffect(inkBus, viewRef) {
        val hv = viewRef ?: return@LaunchedEffect
        onExportState(hv.exportBitmap())     // 초기 1회
        inkBus.debounce(120).collectLatest {
            viewRef?.exportBitmap()?.let(onExportState)
        }
    }

    LaunchedEffect(clearBus, viewRef) {
        val hv = viewRef ?: return@LaunchedEffect
        if (clearBus == null) return@LaunchedEffect
        clearBus.collectLatest {
            hv.clearAll()
            onInkChangedState(0)
            hv.exportBitmap().let(onExportState)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller?.view = null          // ✅ detach
            viewRef = null
        }
    }
}
