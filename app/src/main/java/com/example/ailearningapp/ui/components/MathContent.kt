// app/src/main/java/com/example/ailearningapp/ui/components/MathContentEnhanced.kt
package com.example.ailearningapp.ui.components

import android.annotation.SuppressLint
import android.text.Html
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import android.graphics.Color as AndroidColor
import java.util.regex.Pattern
import kotlin.math.*

/** 도형 타입 열거형 */
private enum class DiagramType {
    BASIC,    // 기존의 단순 도형 (LINE, RECT, CIRCLE)
    ENHANCED, // 향상된 도형 (TRIANGLE, POLYGON, ARC 등)
    SVG       // SVG 형식
}

/** 
 * 문제 본문에서 diagram 코드블록을 추출한다.
 * SVG 블록도 함께 감지하여 처리한다.
 * 반환: (도형스크립트 or null, 도형타입 or null, 도형블록 제거된 텍스트)
 */
private fun extractDiagramBlock(raw: String): Triple<String?, DiagramType?, String> {
    // SVG 블록 체크
    val svgRegex = Regex("```svg\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    val svgMatch = svgRegex.find(raw)
    if (svgMatch != null) {
        val svgContent = svgMatch.groupValues[1].trim()
        val stripped = raw.replaceRange(svgMatch.range, "").trim()
        return Triple(svgContent, DiagramType.SVG, stripped)
    }
    
    // 기존 diagram 블록 체크
    val diagramRegex = Regex("```diagram\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    val diagramMatch = diagramRegex.find(raw)
    if (diagramMatch != null) {
        val script = diagramMatch.groupValues[1].trim()
        val stripped = raw.replaceRange(diagramMatch.range, "").trim()
        // 스크립트 내용을 분석하여 적절한 타입 결정
        val type = determineDiagramType(script)
        return Triple(script, type, stripped)
    }
    
    return Triple(null, null, raw)
}

/** 
 * 도형 스크립트를 분석하여 적절한 렌더러 타입을 결정한다.
 */
private fun determineDiagramType(script: String): DiagramType {
    val lines = script.lines()
    for (line in lines) {
        val trimmed = line.trim().uppercase()
        if (trimmed.startsWith("TRIANGLE") || 
            trimmed.startsWith("POLYGON") || 
            trimmed.startsWith("ARC") ||
            trimmed.startsWith("TEXT") ||
            trimmed.startsWith("ELLIPSE") ||
            trimmed.startsWith("PATH") ||
            trimmed.contains("FILLED")) {
            return DiagramType.ENHANCED
        }
    }
    return DiagramType.BASIC
}

/** 입력 문자열에 섞여온 리터럴 개행 표기(\n, \\n 등)를 실제 줄바꿈으로 교정 */
private fun normalizeEscapedBreaks(s: String): String {
    var t = s
    // 실제 개행 통일
    t = t.replace("\r\n", "\n").replace("\r", "\n")
    // 리터럴 이스케이프 개행("\n", "\\n", "\r\n") -> 실제 개행
    t = t.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n")
    // 과도한 빈 줄 축약(3줄 이상 -> 2줄)
    t = Regex("\n{3,}").replace(t, "\n\n")
    return t
}

/** 흔한 의사수식 오타 자동 교정 (OCR/타이핑 오류) */
private fun fixPseudoMathTypos(raw: String): String {
    var s = raw
    // flac( → frac( , frc( → frac(
    s = Regex("""\bflac\s*\(""", RegexOption.IGNORE_CASE).replace(s) { "frac(" }
    s = Regex("""\bfrc\s*\(""",  RegexOption.IGNORE_CASE).replace(s) { "frac(" }
    // sqr t( -> sqrt(
    s = Regex("""\bsq\s*r\s*t\s*\(""", RegexOption.IGNORE_CASE).replace(s) { "sqrt(" }
    return s
}

/** 의사수식 → TeX (frac(a,b), sqrt(x), pi 등) — 블록 수식용 */
private fun pseudoMathToTeX(s: String): String {
    var t = s
    // frac(a,b) -> \frac{a}{b}
    val frac = Pattern.compile("""frac\(\s*([^,()]+)\s*,\s*([^)]+)\)""", Pattern.CASE_INSENSITIVE)
    t = frac.matcher(t).replaceAll("\\\\frac{$1}{$2}")
    // sqrt(x) -> \sqrt{x}
    val sqrt = Pattern.compile("""sqrt\(\s*([^)]+)\)""", Pattern.CASE_INSENSITIVE)
    t = sqrt.matcher(t).replaceAll("\\\\sqrt{$1}")
    // pi -> \pi
    t = Regex("""\bpi\b""").replace(t) { "\\pi" }
    // 공백으로 둘러싼 * 만 ⋅ 로
    t = Regex("""\s\*\s""").replace(t, " \\\\cdot ")
    // -> 를 \to
    t = t.replace("->", "\\\\to")
    return t
}

/** 텍스트 라인 안에 있는 의사수식을 인라인 수식으로 치환: '... frac(1,2) ...' → '...\(\frac{1}{2}\)...' */
private fun injectInlineTeX(escaped: String): String {
    var s = escaped
    val frac = Pattern.compile("""frac\(\s*([^,()]+)\s*,\s*([^)]+)\)""", Pattern.CASE_INSENSITIVE)
    s = frac.matcher(s).replaceAll("\\\\(\\\\frac{$1}{$2}\\\\)")
    val sqrt = Pattern.compile("""sqrt\(\s*([^)]+)\)""", Pattern.CASE_INSENSITIVE)
    s = sqrt.matcher(s).replaceAll("\\\\(\\\\sqrt{$1}\\\\)")
    s = Regex("""\bpi\b""").replace(s) { "\\\\(\\\\pi\\\\)" }
    s = Regex("""\s\*\s""").replace(s, " \\\\(\\\\cdot\\\\) ")
    s = s.replace("->", "\\\\(\\\\to\\\\)")
    return s
}

/** 이 라인이 '순수 수학'처럼 보이는지(문장 없이 기호/식 위주인지) 휴리스틱 판정 */
private fun isLikelyPureMath(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return false
    val hasHangul = Regex("[가-힣]").containsMatchIn(trimmed)
    val hasLongAlphaWord = Regex("[A-Za-z]{2,}").containsMatchIn(trimmed) // 영어 단어(설명문) 추정
    val hasMathOps = Regex("""[=+\-*/^]""").containsMatchIn(trimmed) ||
            trimmed.contains("frac(") || trimmed.contains("\\frac") ||
            trimmed.contains("sqrt(") || trimmed.contains("\\sqrt")
    return !hasHangul && !hasLongAlphaWord && hasMathOps
}

/** 한 줄 단위 렌더링 → 순수 수학이면 $$...$$, 아니면 텍스트 + 인라인 수식 */
private fun renderLineWithMath(line: String): String {
    val pure = isLikelyPureMath(line)
    return if (pure) {
        val tex = pseudoMathToTeX(line.trim())
        "$$ $tex $$"
    } else {
        val escaped = Html.escapeHtml(line)
        injectInlineTeX(escaped)
    }
}

/** 본문 전체를 HTML(수식 포함)로 구성 */
private fun toHtmlWithTeX(raw: String): String {
    val lines = raw.split('\n')
    val buf = StringBuilder()
    lines.forEachIndexed { idx, line ->
        buf.append(renderLineWithMath(line))
        if (idx < lines.lastIndex) buf.append("<br/>")
    }
    return buf.toString()
}

/** MathJax HTML 생성 (가로 스크롤 제거 + 자동 줄바꿈 + 기기 폭 적용 + 선택지 자동번호화) */
private fun buildMathJaxHtml(bodyHtml: String, fontSizeSp: Int, deviceWidthCssPx: Int): String {
    val mathJaxJs = "https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"
    val config = """
        <script>
          window.MathJax = {
            tex: {
              inlineMath: [['\\(','\\)'], ['$', '$']],
              displayMath: [['$$','$$'], ['\\[','\\]']],
              processEscapes: true,
              packages: {'[+]': ['ams']}
            },
            chtml: {
              linebreaks: { automatic: true, width: 'container' },
              scale: 1
            },
            options: { skipHtmlTags: ['script','noscript','style','textarea','pre'] }
          };
        </script>
    """.trimIndent()

    val css = """
        <style>
          :root{
            --bg:transparent;
            --surface:#FFFFFF;
            --text:#141826;
            --muted:#6A7385;
            --border:#E8EBF3;
            --accent:#6E59A5;
            --accent-ink:#FFFFFF;
            --shadow:0 10px 28px rgba(20,24,38,0.06);
            --radius:16px;
          }
          html, body { max-width: 100%; overflow-x: hidden; }
          body {
            margin: 0; padding: 1.0rem;
            font-family: -apple-system, Roboto, "Noto Sans", "Pretendard", "Inter", Arial, sans-serif;
            font-size: ${fontSizeSp}px; line-height: 1.75;
            color: var(--text);
            background: var(--bg);
            -webkit-text-size-adjust: 100%;
            letter-spacing: .1px;
          }
          #wrap { max-width: ${deviceWidthCssPx}px; width: 100%; margin: 0 auto; }
          #content {
            background: var(--surface);
            border: 1px solid var(--border);
            box-shadow: var(--shadow);
            border-radius: var(--radius);
            padding: 0.9rem 1.0rem;
            white-space: pre-wrap;
            overflow-wrap: anywhere;
            word-break: break-word;
          }
          .mjx-container { max-width: 100% !important; overflow-x: hidden !important; }
          .mjx-container[display="true"] { white-space: normal !important; }

          /* 선택지 스타일 */
          .choices { background: transparent; border: 0; box-shadow: none; padding: 0; margin: .2rem 0 0 0; }
          .choices > ol { margin: .2rem 0 0 0; padding-left: 1.6rem; counter-reset: num; list-style: none; }
          .choices > ol > li {
            counter-increment: num; position: relative; margin: .38rem 0; padding-left: .6rem;
          }
          .choices > ol > li::before{
            content: counter(num);
            position: absolute; left: -1.4rem; top: .12rem;
            width: 1.05rem; height: 1.05rem; display: inline-flex; align-items: center; justify-content: center;
            font-size: .8rem; font-weight: 800; color: var(--accent-ink); background: var(--accent);
            border-radius: 999px; box-shadow: 0 2px 6px rgba(110,89,165,0.25);
          }
        </style>
    """.trimIndent()

    val viewport = """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>"""

    // 선택지 정규화 스크립트(브레이크 라인 → <ol> 변환), 수식 인라인 강제
    val normalizeChoicesJs = """
        <script>
        (function(){
          function normalizeChoices(){
            var content = document.getElementById('content');
            if(!content) return;

            var html = content.innerHTML;
            var parts = html.split(/<br\s*\/?>/i); // 기존 라인 분리
            var out = [];
            var i = 0;

            var headerRe = /^\s*(보기|선택지)\s*:?\s*$/i;
            // 불릿(-, • 등) 또는 '1)', '(1)', 'A.', '(B)' 같은 마커
            var markerRe = /^\s*(?:[-–—•*]|[①-⑳]|(?:\(?\d{1,2}[)\.]|\(?[A-Za-z][)\.]))\s*/;

            function cleanLine(s){
              // 양끝 불릿/대시 제거
              s = s.replace(markerRe,'').replace(/\s*[-–—•*]+\s*$/,'').trim();
              // $$..$$ 단독은 인라인으로 강제 (li 내부에서 레이아웃 안정)
              s = s.replace(/^\s*\$\$([\s\S]*?)\$\$\s*$/,'\\($1\\)');
              return s;
            }

            while(i < parts.length){
              var seg = (parts[i] || '').trim();
              if (seg === '') { out.push(parts[i++]); continue; }

              var start = i;
              var sawHeader = headerRe.test(seg);
              if (sawHeader) { i++; }

              var j = i, bucket = [];
              while(j < parts.length){
                var line = (parts[j] || '').trim();
                if (line === '') break;
                var hasMarker = markerRe.test(line);
                if (!sawHeader && !hasMarker) break; // 헤더가 없으면 마커가 있는 라인만 선택지로 간주
                bucket.push(cleanLine(line));
                j++;
                if (bucket.length >= 6) break;
              }

              if (bucket.length >= 2){
                // 선택지 블록으로 치환
                var list = '<div class="choices"><ol>' + bucket.map(function(c){ return '<li>' + c + '</li>'; }).join('') + '</ol></div>';
                out.push(list);
                i = j;
              } else {
                // 선택지로 보기 어려우면 원래 라인 유지
                out.push(parts[start]);
                i = start + 1;
              }
            }

            content.innerHTML = out.join('<br/>');
          }

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', normalizeChoices);
          } else {
            normalizeChoices();
          }
        })();
        </script>
    """.trimIndent()

    return """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8">
            $viewport
            $config
            $css
            $normalizeChoicesJs
            <script src="$mathJaxJs"></script>
          </head>
          <body>
            <div id="wrap"><div id="content">$bodyHtml</div></div>
          </body>
        </html>
    """.trimIndent()
}

/** JS → Android 브리지: MathJax typeset 이후 문서 높이를 CSS px로 전달 */
private class JsBridge(private val onHeightCssPx: (Int) -> Unit) {
    @Keep
    @JavascriptInterface
    @Suppress("unused")
    fun onSize(cssPx: Int) {
        onHeightCssPx(cssPx)
    }
}

/** 수학 본문 렌더러 (MathJax) — 콘텐츠 높이 자동 반영 + 기기 폭 적용 */
@SuppressLint("SetJavaScriptEnabled", "ConfigurationScreenWidthHeight")
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 18,
) {
    // 1) \n 같은 리터럴 개행 교정 → 2) 의사수식 오타(flac) 교정 → 3) TeX 변환
    val fixedInput by remember(text) { mutableStateOf(fixPseudoMathTypos(normalizeEscapedBreaks(text))) }

    // 기기 폭(px) 계산
    val conf = LocalConfiguration.current
    val density = LocalDensity.current
    val deviceWidthPx = with(density) { conf.screenWidthDp.dp.toPx() }.roundToInt()

    val prepared by remember(fixedInput) { mutableStateOf(toHtmlWithTeX(fixedInput)) }
    val html by remember(prepared, fontSizeSp, deviceWidthPx) {
        mutableStateOf(buildMathJaxHtml(prepared, fontSizeSp, deviceWidthPx))
    }

    // JS에서 주는 값은 CSS px 기준
    var contentHeightCssPx by remember { mutableIntStateOf(0) }
    val minHeight = 24 // dp와 동일하게 취급

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(maxOf(contentHeightCssPx, minHeight).dp),
        factory = { ctx ->
            WebView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER

                // WebView 배경도 투명(바깥 직사각형 제거)
                setBackgroundColor(0x00000000)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = true
                @Suppress("DEPRECATION")
                settings.textZoom = 100

                addJavascriptInterface(JsBridge { h -> contentHeightCssPx = h }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            """
                            (function() {
                              function report() {
                                var h = Math.max(
                                  document.body.scrollHeight,
                                  document.documentElement.scrollHeight,
                                  document.body.getBoundingClientRect().height,
                                  document.documentElement.getBoundingClientRect().height
                                );
                                if (window.AndroidBridge && AndroidBridge.onSize) {
                                  AndroidBridge.onSize(Math.ceil(h));
                                }
                              }
                              if (window.MathJax && MathJax.typesetPromise) {
                                MathJax.typesetPromise().then(report).catch(report);
                              } else {
                                setTimeout(report, 100);
                              }
                              setTimeout(report, 400);
                              setTimeout(report, 1200);
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }

                loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "utf-8", null)
            }
        },
        update = { web ->
            contentHeightCssPx = 0 // 재측정 유도
            web.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "utf-8", null)
        }
    )
}

/* -------------------- 도형 렌더러 -------------------- */

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
 * 더 많은 도형 타입 지원: TRIANGLE, POLYGON, ARC, ELLIPSE, PATH
 */
@Composable
fun EnhancedDiagramBox(
    script: String,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    color: Color = Color(0xFF374151)
) {
    val commands = remember(script) { parseEnhancedDiagram(script) }
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val scale = minOf(width, height) / 100f
        
        // 배경 격자 (선택사항)
        if (false) { // 격자를 원하면 true로 변경
            drawGrid(scale, color.copy(alpha = 0.05f))
        }
        
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
                        color = if (cmd.filled) color.copy(alpha = 0.2f) else color,
                        topLeft = Offset(cmd.x * scale, cmd.y * scale),
                        size = Size(cmd.width * scale, cmd.height * scale),
                        style = if (cmd.filled) Fill else Stroke(width = strokeWidth)
                    )
                }
                is DiagramCommand.Circle -> {
                    drawCircle(
                        color = if (cmd.filled) color.copy(alpha = 0.2f) else color,
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
                        color = if (cmd.filled) color.copy(alpha = 0.2f) else color,
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
                            color = if (cmd.filled) color.copy(alpha = 0.2f) else color,
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
                is DiagramCommand.Ellipse -> {
                    // 타원 그리기
                    drawOval(
                        color = if (cmd.filled) color.copy(alpha = 0.2f) else color,
                        topLeft = Offset((cmd.cx - cmd.rx) * scale, (cmd.cy - cmd.ry) * scale),
                        size = Size(cmd.rx * 2 * scale, cmd.ry * 2 * scale),
                        style = if (cmd.filled) Fill else Stroke(width = strokeWidth)
                    )
                }
                is DiagramCommand.Path -> {
                    // 경로 그리기
                    val path = Path().apply {
                        cmd.commands.forEach { pathCmd ->
                            when (pathCmd.first.uppercase()) {
                                "M" -> moveTo(pathCmd.second * scale, pathCmd.third * scale)
                                "L" -> lineTo(pathCmd.second * scale, pathCmd.third * scale)
                                "C" -> cubicTo(
                                    pathCmd.second * scale, pathCmd.third * scale,
                                    pathCmd.fourth * scale, pathCmd.fifth * scale,
                                    pathCmd.sixth * scale, pathCmd.seventh * scale
                                )
                                "Z" -> close()
                            }
                        }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = strokeWidth)
                    )
                }
                is DiagramCommand.Text -> {
                    // 텍스트는 Canvas에서 직접 그리기 어려우므로 skip
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
    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val filled: Boolean = false) : DiagramCommand
    data class Path(val commands: List<PathCommand>) : DiagramCommand
    data class Text(val x: Float, val y: Float, val text: String, val size: Float = 12f) : DiagramCommand
}

// PATH 명령을 위한 데이터 클래스
data class PathCommand(
    val first: String,
    val second: Float = 0f,
    val third: Float = 0f,
    val fourth: Float = 0f,
    val fifth: Float = 0f,
    val sixth: Float = 0f,
    val seventh: Float = 0f
)

/**
 * 향상된 도형 스크립트 파서
 * 지원 명령어:
 * - LINE x1 y1 x2 y2
 * - RECT x y width height [FILLED]
 * - CIRCLE cx cy radius [FILLED]
 * - TRIANGLE x1 y1 x2 y2 x3 y3 [FILLED]
 * - POLYGON x1,y1 x2,y2 x3,y3 ... [FILLED]
 * - ARC cx cy radius startAngle sweepAngle
 * - ELLIPSE cx cy rx ry [FILLED]
 * - PATH M x y L x y C x1 y1 x2 y2 x3 y3 Z
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
                "ELLIPSE" -> {
                    if (parts.size >= 5) {
                        val filled = parts.getOrNull(5)?.uppercase() == "FILLED"
                        commands.add(DiagramCommand.Ellipse(
                            parts[1].toFloat(), parts[2].toFloat(),
                            parts[3].toFloat(), parts[4].toFloat(),
                            filled
                        ))
                    }
                }
                "PATH" -> {
                    // PATH M 10 10 L 50 50 C 20 20 30 30 40 40 Z
                    val pathCommands = mutableListOf<PathCommand>()
                    var i = 1
                    while (i < parts.size) {
                        when (parts[i].uppercase()) {
                            "M", "L" -> {
                                if (i + 2 < parts.size) {
                                    pathCommands.add(PathCommand(
                                        parts[i].uppercase(),
                                        parts[i + 1].toFloat(),
                                        parts[i + 2].toFloat()
                                    ))
                                    i += 3
                                } else break
                            }
                            "C" -> {
                                if (i + 6 < parts.size) {
                                    pathCommands.add(PathCommand(
                                        "C",
                                        parts[i + 1].toFloat(),
                                        parts[i + 2].toFloat(),
                                        parts[i + 3].toFloat(),
                                        parts[i + 4].toFloat(),
                                        parts[i + 5].toFloat(),
                                        parts[i + 6].toFloat()
                                    ))
                                    i += 7
                                } else break
                            }
                            "Z" -> {
                                pathCommands.add(PathCommand("Z"))
                                i++
                            }
                            else -> i++
                        }
                    }
                    if (pathCommands.isNotEmpty()) {
                        commands.add(DiagramCommand.Path(pathCommands))
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

/**
 * 기본 도형 렌더러 (하위 호환성 유지)
 * 간단한 도형 DSL:
 * - LINE x1 y1 x2 y2
 * - RECT x y w h
 * - CIRCLE cx cy r
 * 좌표는 0..100 기준으로 컨테이너에 스케일링.
 */
@Composable
fun BasicDiagramBox(
    script: String,
    modifier: Modifier = Modifier,
    stroke: Dp = 2.dp,
    color: Color = Color(0xFF374151),
    grid: Boolean = false
) {
    val cmds = remember(script) { parseBasicDiagram(script) }
    Canvas(modifier = modifier) {
        val s = size
        val scale = min(s.width, s.height) / 100f

        if (grid) {
            val step = 10f * scale
            for (x in 0..(s.width / step).toInt()) {
                drawLine(color = color.copy(alpha = 0.08f), start = Offset(x * step, 0f), end = Offset(x * step, s.height), strokeWidth = 1f)
            }
            for (y in 0..(s.height / step).toInt()) {
                drawLine(color = color.copy(alpha = 0.08f), start = Offset(0f, y * step), end = Offset(s.width, y * step), strokeWidth = 1f)
            }
        }

        val sw = stroke.toPx()
        cmds.forEach { c ->
            when (c) {
                is BasicCmd.Line -> drawLine(color = color, start = Offset(c.x1 * scale, c.y1 * scale), end = Offset(c.x2 * scale, c.y2 * scale), strokeWidth = sw)
                is BasicCmd.Rect -> drawRect(color = Color.Transparent, topLeft = Offset(c.x * scale, c.y * scale), size = Size(c.w * scale, c.h * scale), style = Stroke(width = sw))
                is BasicCmd.Circle -> drawCircle(color = Color.Transparent, radius = c.r * scale, center = Offset(c.cx * scale, c.cy * scale), style = Stroke(width = sw))
            }
        }
    }
}

private sealed interface BasicCmd {
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : BasicCmd
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float) : BasicCmd
    data class Circle(val cx: Float, val cy: Float, val r: Float) : BasicCmd
}

private fun parseBasicDiagram(script: String): List<BasicCmd> {
    val cmds = mutableListOf<BasicCmd>()
    script.lineSequence().forEach { raw ->
        val line = raw.trim().replace(Regex("\\s+"), " ")
        if (line.isBlank() || line.startsWith("#")) return@forEach
        val parts = line.split(" ")

        fun getF(i: Int): Float? = parts.getOrNull(i)?.toFloatOrNull()

        when (parts.firstOrNull()?.uppercase()) {
            "LINE" -> {
                val x1 = getF(1); val y1 = getF(2); val x2 = getF(3); val y2 = getF(4)
                if (x1 != null && y1 != null && x2 != null && y2 != null) {
                    cmds.add(BasicCmd.Line(x1, y1, x2, y2))
                }
            }
            "RECT" -> {
                val x = getF(1); val y = getF(2); val w = getF(3); val h = getF(4)
                if (x != null && y != null && w != null && h != null) {
                    cmds.add(BasicCmd.Rect(x, y, w, h))
                }
            }
            "CIRCLE" -> {
                val cx = getF(1); val cy = getF(2); val r = getF(3)
                if (cx != null && cy != null && r != null) {
                    cmds.add(BasicCmd.Circle(cx, cy, r))
                }
            }
        }
    }
    return cmds
}

/**
 * 도형 렌더러 통합 함수
 * 도형 타입에 따라 적절한 렌더러를 선택한다.
 */
@Composable
fun DiagramBox(
    script: String,
    type: DiagramType,
    modifier: Modifier = Modifier,
    stroke: Dp = 2.dp,
    color: Color = Color(0xFF374151)
) {
    when (type) {
        DiagramType.BASIC -> {
            BasicDiagramBox(
                script = script,
                modifier = modifier,
                stroke = stroke,
                color = color
            )
        }
        DiagramType.ENHANCED -> {
            EnhancedDiagramBox(
                script = script,
                modifier = modifier,
                strokeWidth = stroke.value,
                color = color
            )
        }
        DiagramType.SVG -> {
            SvgDiagramBox(
                svgContent = script,
                modifier = modifier
            )
        }
    }
}

/** 문제 본문을 수식/도형 포함해 예쁘게 렌더링 */
@Composable
fun ProblemBody(
    body: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 18
) {
    val (diagram, diagramType, stripped0) = remember(body) { extractDiagramBlock(body) }
    // 🔸 "\n\n" 교정 + 의사수식 오타 교정 후 렌더
    val stripped = remember(stripped0) { fixPseudoMathTypos(normalizeEscapedBreaks(stripped0)) }
    Column(modifier = modifier) {
        if (!diagram.isNullOrBlank() && diagramType != null) {
            DiagramBox(
                script = diagram,
                type = diagramType,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .heightIn(min = 160.dp, max = 360.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
        MathText(
            text = stripped,
            modifier = Modifier.fillMaxWidth(),
            fontSizeSp = fontSizeSp
        )
    }
}