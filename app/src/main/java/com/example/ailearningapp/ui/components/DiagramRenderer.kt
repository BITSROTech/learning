// app/src/main/java/com/example/ailearningapp/ui/components/DiagramRenderer.kt
package com.example.ailearningapp.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.*

/**
 * SVG 콘텐츠를 WebView로 렌더링
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SvgDiagramBox(
    svgContent: String,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) "#1a1a1a" else "#ffffff"
    val strokeColor = if (isDarkTheme) "#e5e5e5" else "#374151"
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.apply {
                        javaScriptEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    setBackgroundColor(AndroidColor.parseColor(backgroundColor))
                }
            },
            update = { webView ->
                val processedSvg = processSvgContent(svgContent, strokeColor)
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            body {
                                margin: 0;
                                padding: 10px;
                                background-color: $backgroundColor;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                min-height: 100vh;
                                box-sizing: border-box;
                            }
                            svg {
                                max-width: 100%;
                                height: auto;
                                display: block;
                            }
                            /* 기본 스타일 */
                            line, path, circle, rect, ellipse, polygon, polyline {
                                stroke: $strokeColor;
                                stroke-width: 2;
                                fill: none;
                            }
                            text {
                                fill: $strokeColor;
                                font-family: 'Noto Sans KR', sans-serif;
                                font-size: 14px;
                            }
                            /* 채워진 도형 */
                            .filled {
                                fill: $strokeColor;
                                fill-opacity: 0.2;
                            }
                        </style>
                    </head>
                    <body>
                        $processedSvg
                    </body>
                    </html>
                """.trimIndent()
                
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        )
    }
}

/**
 * SVG 콘텐츠 처리 및 스타일 적용
 */
private fun processSvgContent(svg: String, strokeColor: String): String {
    var processed = svg
    
    // viewBox가 없으면 추가
    if (!processed.contains("viewBox", ignoreCase = true)) {
        processed = processed.replace(
            Regex("<svg([^>]*)>", RegexOption.IGNORE_CASE),
            "<svg$1 viewBox=\"0 0 400 400\">"
        )
    }
    
    // 기본 스타일 적용
    if (!processed.contains("stroke=", ignoreCase = true)) {
        processed = processed.replace(
            Regex("<(line|path|circle|rect|ellipse|polygon|polyline)([^>]*)>", RegexOption.IGNORE_CASE)
        ) { matchResult ->
            val tag = matchResult.groupValues[1]
            val attrs = matchResult.groupValues[2]
            if (!attrs.contains("stroke")) {
                "<$tag$attrs stroke=\"$strokeColor\">"
            } else {
                matchResult.value
            }
        }
    }
    
    return processed
}

/**
 * 향상된 Canvas 기반 도형 렌더러
 * 더 많은 도형 타입과 텍스트 지원
 */
@Composable
fun EnhancedDiagramBox(
    script: String,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    color: Color = Color(0xFF374151)
) {
    val commands = remember(script) { parseEnhancedDiagram(script) }
    
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        val width = size.width
        val height = size.height
        val scale = minOf(width, height) / 100f
        
        // 배경 격자 (옵션)
        drawGrid(scale, color.copy(alpha = 0.05f))
        
        // 도형 렌더링
        commands.forEach { cmd ->
            when (cmd) {
                is DiagramCommand.Line -> {
                    drawLine(
                        color = color,
                        start = Offset(cmd.x1 * scale, cmd.y1 * scale),
                        end = Offset(cmd.x2 * scale, cmd.y2 * scale),
                        strokeWidth = strokeWidth
                    )
                }
                is DiagramCommand.Rectangle -> {
                    drawRect(
                        color = color,
                        topLeft = Offset(cmd.x * scale, cmd.y * scale),
                        size = Size(cmd.width * scale, cmd.height * scale),
                        style = if (cmd.filled) Fill else Stroke(width = strokeWidth)
                    )
                }
                is DiagramCommand.Circle -> {
                    drawCircle(
                        color = color,
                        radius = cmd.radius * scale,
                        center = Offset(cmd.cx * scale, cmd.cy * scale),
                        style = if (cmd.filled) Fill else Stroke(width = strokeWidth)
                    )
                }
                is DiagramCommand.Triangle -> {
                    val path = Path().apply {
                        moveTo(cmd.x1 * scale, cmd.y1 * scale)
                        lineTo(cmd.x2 * scale, cmd.y2 * scale)
                        lineTo(cmd.x3 * scale, cmd.y3 * scale)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = if (cmd.filled) Fill else Stroke(width = strokeWidth)
                    )
                }
                is DiagramCommand.Polygon -> {
                    if (cmd.points.size >= 3) {
                        val path = Path().apply {
                            moveTo(cmd.points[0].first * scale, cmd.points[0].second * scale)
                            cmd.points.drop(1).forEach { (x, y) ->
                                lineTo(x * scale, y * scale)
                            }
                            close()
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = if (cmd.filled) Fill else Stroke(width = strokeWidth)
                        )
                    }
                }
                is DiagramCommand.Arc -> {
                    drawArc(
                        color = color,
                        startAngle = cmd.startAngle,
                        sweepAngle = cmd.sweepAngle,
                        useCenter = false,
                        topLeft = Offset((cmd.cx - cmd.radius) * scale, (cmd.cy - cmd.radius) * scale),
                        size = Size(cmd.radius * 2 * scale, cmd.radius * 2 * scale),
                        style = Stroke(width = strokeWidth)
                    )
                }
                is DiagramCommand.Text -> {
                    // 텍스트는 Canvas에서 직접 그리기 어려우므로 skip
                    // 실제 구현시 drawText API 사용 가능
                }
            }
        }
    }
}

/**
 * 격자 그리기
 */
private fun DrawScope.drawGrid(scale: Float, color: Color) {
    val step = 10f * scale
    val width = size.width
    val height = size.height
    
    // 수직선
    var x = 0f
    while (x <= width) {
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
        x += step
    }
    
    // 수평선
    var y = 0f
    while (y <= height) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
        y += step
    }
}

/**
 * 도형 명령어 정의
 */
sealed interface DiagramCommand {
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : DiagramCommand
    data class Rectangle(val x: Float, val y: Float, val width: Float, val height: Float, val filled: Boolean = false) : DiagramCommand
    data class Circle(val cx: Float, val cy: Float, val radius: Float, val filled: Boolean = false) : DiagramCommand
    data class Triangle(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float, val filled: Boolean = false) : DiagramCommand
    data class Polygon(val points: List<Pair<Float, Float>>, val filled: Boolean = false) : DiagramCommand
    data class Arc(val cx: Float, val cy: Float, val radius: Float, val startAngle: Float, val sweepAngle: Float) : DiagramCommand
    data class Text(val x: Float, val y: Float, val text: String, val size: Float = 12f) : DiagramCommand
}

/**
 * 향상된 도형 스크립트 파서
 * 지원 명령어:
 * - LINE x1 y1 x2 y2
 * - RECT x y width height [FILLED]
 * - CIRCLE cx cy radius [FILLED]
 * - TRIANGLE x1 y1 x2 y2 x3 y3 [FILLED]
 * - POLYGON x1,y1 x2,y2 x3,y3 ... [FILLED]
 * - ARC cx cy radius startAngle sweepAngle
 * - TEXT x y "텍스트 내용"
 */
private fun parseEnhancedDiagram(script: String): List<DiagramCommand> {
    val commands = mutableListOf<DiagramCommand>()
    
    script.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        
        val parts = trimmed.split(Regex("\\s+"))
        val cmd = parts.firstOrNull()?.uppercase() ?: return@forEach
        
        try {
            when (cmd) {
                "LINE" -> {
                    if (parts.size >= 5) {
                        commands.add(DiagramCommand.Line(
                            parts[1].toFloat(), parts[2].toFloat(),
                            parts[3].toFloat(), parts[4].toFloat()
                        ))
                    }
                }
                "RECT", "RECTANGLE" -> {
                    if (parts.size >= 5) {
                        val filled = parts.getOrNull(5)?.uppercase() == "FILLED"
                        commands.add(DiagramCommand.Rectangle(
                            parts[1].toFloat(), parts[2].toFloat(),
                            parts[3].toFloat(), parts[4].toFloat(),
                            filled
                        ))
                    }
                }
                "CIRCLE" -> {
                    if (parts.size >= 4) {
                        val filled = parts.getOrNull(4)?.uppercase() == "FILLED"
                        commands.add(DiagramCommand.Circle(
                            parts[1].toFloat(), parts[2].toFloat(),
                            parts[3].toFloat(), filled
                        ))
                    }
                }
                "TRIANGLE" -> {
                    if (parts.size >= 7) {
                        val filled = parts.getOrNull(7)?.uppercase() == "FILLED"
                        commands.add(DiagramCommand.Triangle(
                            parts[1].toFloat(), parts[2].toFloat(),
                            parts[3].toFloat(), parts[4].toFloat(),
                            parts[5].toFloat(), parts[6].toFloat(),
                            filled
                        ))
                    }
                }
                "POLYGON" -> {
                    if (parts.size >= 2) {
                        val points = mutableListOf<Pair<Float, Float>>()
                        var i = 1
                        while (i < parts.size && parts[i] != "FILLED") {
                            val coords = parts[i].split(",")
                            if (coords.size == 2) {
                                points.add(coords[0].toFloat() to coords[1].toFloat())
                            }
                            i++
                        }
                        val filled = parts.lastOrNull()?.uppercase() == "FILLED"
                        if (points.size >= 3) {
                            commands.add(DiagramCommand.Polygon(points, filled))
                        }
                    }
                }
                "ARC" -> {
                    if (parts.size >= 6) {
                        commands.add(DiagramCommand.Arc(
                            parts[1].toFloat(), parts[2].toFloat(),
                            parts[3].toFloat(),
                            parts[4].toFloat(), parts[5].toFloat()
                        ))
                    }
                }
                "TEXT" -> {
                    if (parts.size >= 4) {
                        val textStart = trimmed.indexOf('"')
                        val textEnd = trimmed.lastIndexOf('"')
                        if (textStart != -1 && textEnd != -1 && textStart < textEnd) {
                            val text = trimmed.substring(textStart + 1, textEnd)
                            commands.add(DiagramCommand.Text(
                                parts[1].toFloat(), parts[2].toFloat(), text
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 파싱 오류 무시
        }
    }
    
    return commands
}