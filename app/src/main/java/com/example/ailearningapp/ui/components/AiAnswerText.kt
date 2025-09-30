@file:Suppress(
    "ANNOTATIONS_ON_BLOCK_LEVEL_EXPRESSION_ON_THE_SAME_LINE",
    "WRAPPED_LHS_IN_ASSIGNMENT_WARNING",
    "DEPRECATION"
)

package com.example.ailearningapp.ui.components

import android.annotation.SuppressLint
import android.text.Html
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/* -------------------- 라벨 기반 가독화(문단/이중개행 강제) -------------------- */
/**
 * - “풀이 과정(10점 만점) … 점수요약” 및 개별 숫자-only 지표 줄 제거
 * - 헤더는 문단 시작 처리
 * - 지표 라벨(정확도/접근법/완성도/창의성)은 불릿으로 변환하되,
 *   ✅ “개선 제안” 구간 내부는 라벨/불릿을 제거하고 문장 나열로만 표시
 */
private fun prettifyFeedback(raw: String): String {
    if (raw.isBlank()) return raw
    var s = raw

    // 공통 표준화
    s = s.replace('：', ':')
        .replace("\r\n", "\n")
        .replace("\r", "\n")

    // 라벨 뒤 조사의 어색한 형태 교정
    s = s.replace(Regex("""(정확도|접근법|완성도|창의성)\s*:\s*(은|는|이|가)\b"""), "$1$2 ")
        .replace(Regex("""정확도\s*:\s*를"""), "정확도를")
        .replace(Regex("""접근법\s*:\s*을"""), "접근법을")
        .replace(Regex("""완성도\s*:\s*를"""), "완성도를")
        .replace(Regex("""창의성\s*:\s*을"""), "창의성을")

    // 선행 하이픈 라벨 정리: "-창의성: ..." -> "창의성: ..."
    s = s.replace(Regex("""(?m)^\s*-\s*(정확도|접근법|완성도|창의성)\s*:\s*"""), "$1: ")

    // 0) “풀이 과정(10점 만점) … 점수요약” 줄 제거
    val procRegex = Regex(
        """풀이\s*과정[^\n]*?:\s*정확도\s*\d{1,2}[^\n]*?접근법\s*\d{1,2}[^\n]*?완성도\s*\d{1,2}[^\n]*?창의성\s*\d{1,2}""",
        RegexOption.IGNORE_CASE
    )
    s = procRegex.replace(s, "")

    // 0-2) 개별 숫자-only 지표 줄 제거: "정확도: 3", "완성도: 6." 등
    val metricScoreOnlyLine = Regex("""(?m)^\s*-?\s*(정확도|접근법|완성도|창의성)\s*:\s*\d{1,2}\s*(?:점|/10)?\s*[.\-;]?\s*$""")
    s = s.replace(metricScoreOnlyLine, "")

    // 1-a) "개선 제안:"은 특수 시작 토큰으로
    s = s.replace(Regex("""(?m)^\s*개선\s*제안\s*:\s*"""), "\n\n@@H:개선 제안@@\n\n@@SUG_START@@")

    // 1-b) 나머지 헤더는 일반 토큰으로
    val otherHeaders = listOf("정답 판정:", "판정 이유:", "풀이 과정", "잘한 점:", "풀이 시간:")
    otherHeaders.forEach { label ->
        val pat = Regex("""(?m)^\s*${Regex.escape(label)}""")
        s = s.replace(pat) { "\n\n@@H:${label}@@" }
    }

    // 2) 지표 라벨 → 토큰(전역). *제안 구간은 나중에 정리*
    val metricNames = listOf("정확도", "접근법", "완성도", "창의성")
    metricNames.forEach { name ->
        val pat = Regex("""(?<!@@LM:)(?m)(^|[ \t])$name\s*:?\s?""")
        s = s.replace(pat) { _ -> "\n\n@@LM:${name}@@" }
    }

    // 2-2) "개선 제안" 구간만 문장 나열로 정리(라벨/불릿 제거 + 줄바꿈 정돈)
    val sugRe = Regex("""@@SUG_START@@([\s\S]*?)(?=@@H:|$)""")
    s = sugRe.replace(s) { m ->
        var block = m.groupValues[1]
        block = block.replace(Regex("""@@LM:(정확도|접근법|완성도|창의성)@@"""), "")
        block = block.replace(Regex("""(?m)^\s*[-•]?\s*(정확도|접근법|완성도|창의성)\s*:\s*"""), "")
        block = block.replace(Regex("""\s*;\s*-\s*"""), "\n")
        block = block.replace(Regex("""\s*;\s*"""), "\n")
        block = block.replace(Regex("""(?m)^\s*-\s*"""), "")
        block = block.replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        "\n" + block.lines().joinToString("\n") { it.trim() }.trimEnd() + "\n\n"
    }

    // 3) 토큰 → 마크업 (헤더는 굵게)
    s = s.replace(Regex("""@@H:([^@]+)@@""")) { m -> "**${m.groupValues[1].trim()}** " }
    // 3-2) 남은 지표 토큰은 불릿으로 (제안 구간 내부는 이미 제거됨)
    s = s.replace(Regex("""@@LM:(정확도|접근법|완성도|창의성)@@""")) { m ->
        "-${m.groupValues[1]}: "
    }

    // 문장 끝의 고립 대시 제거
    s = s.replace(Regex("""(?m)([.!?…])\s*[-–—]\s*(?=\n|$)"""), "$1")
    s = s.replace(Regex("""(?m)^\s*[-–—]\s*$"""), "")

    // 과다 개행 정리
    s = s.replace(Regex("\n{3,}"), "\n\n").trim()
    return s
}

/* -------------------- Sanitizer -------------------- */
private fun sanitize(rawInput: String): String {
    var s = rawInput.trim()
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    s = s.replace(Regex("""\\r\\n"""), "\n")
        .replace(Regex("""\\n"""), "\n")
        .replace(Regex("""\\t"""), "    ")
    s = s.replace(Regex("""[\u200B-\u200D\uFEFF\u2060]"""), "")
    if ((s.startsWith('"') && s.endsWith('"')) ||
        (s.startsWith('“') && s.endsWith('”')) ||
        (s.startsWith('‘') && s.endsWith('’'))
    ) {
        s = s.substring(1, s.length - 1)
    }
    s = s.replace(Regex("```[a-zA-Z0-9_+\\-]*\\s*([\\s\\S]*?)```")) {
        "<pre><code>${Html.escapeHtml(it.groupValues[1].trim())}</code></pre>"
    }
    s = s.replace("```", "")
    s = s.replace(Regex("\n{3,}"), "\n\n")
    return s
}

/* -------------------- Inline 마크업 -------------------- */
private fun inlineToHtml(text: String): String {
    var e = Html.escapeHtml(text)
    e = e.replace(Regex("""\*\*([^*\n]+)\*\*"""), "<strong>$1</strong>")
    e = e.replace(Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)"""), "<em>$1</em>")
    e = e.replace(Regex("""`([^`\n]+)`"""), "<code>$1</code>")
    return e
}

/* -------------------- Markdown-ish -> HTML (+ $$ 수식 블록 보호) -------------------- */
private fun markdownishToHtmlBlocks(s: String): String {
    val lines = s.split('\n')
    val out = StringBuilder()
    var inUl = false
    var inOl = false
    var inBlockquote = false
    var inMath = false
    val para = mutableListOf<String>()
    val mathBuf = mutableListOf<String>()

    fun flushPara() {
        if (para.isNotEmpty()) {
            val joined = para.joinToString("<br/>") { inlineToHtml(it) }
            out.append("<p>").append(joined).append("</p>")
            para.clear()
        }
    }
    fun closeLists() {
        if (inUl) { out.append("</ul>"); inUl = false }
        if (inOl) { out.append("</ol>"); inOl = false }
    }
    fun closeBlockquote() {
        if (inBlockquote) { out.append("</blockquote>"); inBlockquote = false }
    }
    fun flushMath() {
        if (mathBuf.isNotEmpty()) {
            // MathJax가 텍스트 노드로 $$ ... $$ 를 받아야 하므로 <br/> 넣지 않음
            val body = mathBuf.joinToString("\n")
            out.append("<p>$$\n").append(body).append("\n$$</p>")
            mathBuf.clear()
        }
    }

    val ulRegex = Regex("""^\s*[-*]\s+(.+)$""")
    val olRegex = Regex("""^\s*\d+[.)]\s+(.+)$""")
    val hRegex  = Regex("""^\s*(#{1,3})\s+(.+)$""")
    val bqRegex = Regex("""^\s*>\s?(.*)$""")
    val hrRegex = Regex("""^\s*[-*_]{3,}\s*$""")

    for (line in lines) {
        val trimmed = line.trim()

        // 수식 블록 토글: 한 줄이 $$ 인 경우
        if (trimmed == "$$") {
            if (inMath) {
                // 닫기
                flushMath()
                inMath = false
            } else {
                // 열기
                flushPara(); closeLists(); closeBlockquote()
                inMath = true
            }
            continue
        }
        if (inMath) {
            mathBuf.add(line) // 원본 그대로 유지
            continue
        }

        when {
            trimmed.isEmpty() -> { flushPara(); closeLists(); closeBlockquote() }
            hrRegex.matches(trimmed) -> { flushPara(); closeLists(); closeBlockquote(); out.append("<hr/>") }
            hRegex.matches(line) -> {
                flushPara(); closeLists(); closeBlockquote()
                val m = hRegex.find(line)!!
                val level = m.groupValues[1].length.coerceIn(1, 3)
                val text = inlineToHtml(m.groupValues[2].trim())
                out.append("<h$level>").append(text).append("</h$level>")
            }
            bqRegex.matches(line) -> {
                flushPara(); closeLists()
                val m = bqRegex.find(line)!!
                if (!inBlockquote) { out.append("<blockquote>"); inBlockquote = true }
                out.append("<p>").append(inlineToHtml(m.groupValues[1])).append("</p>")
            }
            ulRegex.matches(line) -> {
                flushPara(); closeBlockquote()
                if (inOl) { out.append("</ol>"); inOl = false }
                if (!inUl) { out.append("<ul>"); inUl = true }
                val item = ulRegex.find(line)!!.groupValues[1]
                out.append("<li>").append(inlineToHtml(item)).append("</li>")
            }
            olRegex.matches(line) -> {
                flushPara(); closeBlockquote()
                if (inUl) { out.append("</ul>"); inUl = false }
                if (!inOl) { out.append("<ol>"); inOl = true }
                val item = olRegex.find(line)!!.groupValues[1]
                out.append("<li>").append(inlineToHtml(item)).append("</li>")
            }
            line.startsWith("<pre><code>") -> { flushPara(); closeLists(); closeBlockquote(); out.append(line) }
            else -> { para.add(line) }
        }
    }
    if (inMath) flushMath()
    flushPara(); closeLists(); closeBlockquote()
    return out.toString()
}

/* -------------------- HTML + 스타일 (+ MathJax 주입) -------------------- */
private fun buildAnswerHtml(innerHtml: String, deviceWidthCssPx: Int, fontSizeSp: Int): String {
    val css = """
        <style>
          html, body { max-width: 100%; overflow-x: hidden; }
          body {
            margin: 0; padding: 0.9rem;
            font-family: -apple-system, Roboto, "Noto Sans", Arial, sans-serif;
            font-size: ${fontSizeSp}px; line-height: 1.7;
            color: #000000;
            background: #FFFFFF;
            font-weight: 500;
            -webkit-text-size-adjust: 100%;
            word-wrap: break-word; overflow-wrap: break-word;
            text-rendering: optimizeLegibility;
          }
          #wrap, #wrap * { color: #000000 !important; }

          #wrap { max-width: ${deviceWidthCssPx}px; width: 100%; }
          p { margin: 0 0 1.0rem 0; }
          ul, ol { margin: 0 0 1.0rem 1.2rem; padding: 0; }
          li { margin: 0.5rem 0; }
          h1, h2, h3 {
            margin: 0.25rem 0 0.7rem 0; line-height: 1.35; font-weight: 700;
          }
          h1 { font-size: ${fontSizeSp + 8}px; }
          h2 { font-size: ${fontSizeSp + 4}px; }
          h3 { font-size: ${fontSizeSp + 2}px; }
          hr {
            border: 0; height: 1px; background: #E5E7EB;
            margin: 1.0rem 0;
          }
          blockquote {
            margin: 0 0 1.0rem 0; padding: 0.7rem 0.9rem;
            background: #F9FAFB; border-left: 4px solid #D1D5DB;
            border-radius: 10px;
          }
          blockquote p { margin: 0.15rem 0; }
          code {
            font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
            background: #F3F4F6; padding: 0.14rem 0.4rem; border-radius: 6px;
            font-weight: 600;
          }
          pre {
            background: #F3F4F6; padding: 0.85rem; border-radius: 12px;
            overflow-x: auto; margin: 0 0 1.0rem 0;
          }
          pre code { background: transparent; padding: 0; }
          a { color: #2563EB; text-decoration: none; }
          a:hover { text-decoration: underline; }
          ::selection { background: rgba(37,99,235,0.18); }
        </style>
    """.trimIndent()

    val viewport = """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>"""
    val colorScheme = """<meta name="color-scheme" content="light">"""

    // ✅ MathJax 설정/주입 (TeX → CHTML). typeset은 로드 후 수동 호출.
    val mathJax = """
        <script>
          window.MathJax = {
            tex: {
              inlineMath: [['$', '$'], ['\\(', '\\)']],
              displayMath: [['$$','$$'], ['\\[','\\]']],
              processEscapes: true,
              packages: {'[+]': ['noerrors','noundefined']}
            },
            options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'] },
            startup: { typeset: false }
          };
        </script>
        <script id="MathJax-script" async
          src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
    """.trimIndent()

    val autosizeJs = """
        (function() {
          function report() {
            var h = Math.max(
              document.body.scrollHeight,
              document.documentElement.scrollHeight,
              document.body.getBoundingClientRect().height,
              document.documentElement.getBoundingClientRect().height
            );
            if (window.AndroidBridge2 && AndroidBridge2.onSize) {
              AndroidBridge2.onSize(Math.ceil(h));
            }
          }
          try {
            if ('ResizeObserver' in window) {
              var ro = new ResizeObserver(function() { report(); });
              ro.observe(document.body);
            } else {
              setInterval(report, 300);
            }
            if (document.fonts && document.fonts.ready) { document.fonts.ready.then(report); }
            Array.prototype.forEach.call(document.images || [], function(img) {
              if (!img.complete) { img.addEventListener('load', report); img.addEventListener('error', report); }
            });
          } catch (e) {}
          function typesetAndReport(){
            if (window.MathJax && MathJax.typesetPromise) {
              MathJax.typesetPromise().then(report).catch(report);
            } else { report(); }
          }
          setTimeout(typesetAndReport, 30);
          setTimeout(typesetAndReport, 250);
          setTimeout(typesetAndReport, 1000);
        })();
    """.trimIndent()

    return """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8">
            $viewport
            $colorScheme
            $css
            $mathJax
          </head>
          <body>
            <div id="wrap">$innerHtml</div>
            <script>$autosizeJs</script>
          </body>
        </html>
    """.trimIndent()
}

/* -------------------- 높이 브리지 -------------------- */
@Keep
private class JsBridge2(private val onHeightCssPx: (Int) -> Unit) {
    @JavascriptInterface
    fun onSize(cssPx: Int) = onHeightCssPx(cssPx)
}

/* -------------------- Composable -------------------- */
@SuppressLint("SetJavaScriptEnabled", "ConfigurationScreenWidthHeight")
@Composable
fun AiAnswerText(
    text: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 16
) {
    val conf = LocalConfiguration.current
    val density = LocalDensity.current
    val deviceWidthPx = with(density) { conf.screenWidthDp.dp.toPx() }.roundToInt()

    val prettified by remember(text) { mutableStateOf(prettifyFeedback(text)) }
    val sanitized by remember(prettified) { mutableStateOf(sanitize(prettified)) }
    val htmlBody by remember(sanitized) { mutableStateOf(markdownishToHtmlBlocks(sanitized)) }
    val fullHtml by remember(htmlBody, deviceWidthPx, fontSizeSp) {
        mutableStateOf(buildAnswerHtml(htmlBody, deviceWidthPx, fontSizeSp))
    }

    var contentHeightCssPx by remember { mutableIntStateOf(0) }
    val minHeight = 24

    AndroidView(
        modifier = modifier.height(maxOf(contentHeightCssPx, minHeight).dp),
        factory = { ctx ->
            WebView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setBackgroundColor(0x00000000)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = true
                @Suppress("DEPRECATION") settings.textZoom = 100
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    settings.forceDark = WebSettings.FORCE_DARK_OFF
                }

                addJavascriptInterface(JsBridge2 { h -> contentHeightCssPx = h }, "AndroidBridge2")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            // MathJax typeset 후 높이 보고
                            """
                            (function(){
                              try{
                                if (window.MathJax && MathJax.typesetPromise) {
                                  MathJax.typesetPromise().then(function(){
                                    if(window.AndroidBridge2){AndroidBridge2.onSize(document.body.scrollHeight|0);}
                                  }).catch(function(){
                                    if(window.AndroidBridge2){AndroidBridge2.onSize(document.body.scrollHeight|0);}
                                  });
                                } else {
                                  if(window.AndroidBridge2){AndroidBridge2.onSize(document.body.scrollHeight|0);}
                                }
                              } catch(e){}
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }
                loadDataWithBaseURL("about:blank", fullHtml, "text/html", "utf-8", null)
            }
        },
        update = { web ->
            contentHeightCssPx = 0
            web.loadDataWithBaseURL("about:blank", fullHtml, "text/html", "utf-8", null)
        }
    )
}
