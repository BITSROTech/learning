package com.example.ailearningapp.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 인앱 OpenAI 호출기
 * - 문제 생성: 한 번에(JSON) 생성 (스트리밍 OFF)
 * - 채점: 텍스트 판정 + (옵션) 비전 노트 → 4지표 보정 → 강점/개선 → 요약
 * - Cloud Run 파이프라인을 Kotlin/OkHttp로 최대한 재현
 *
 * 의존성: OkHttp, org.json
 */
class OpenAIInApp(
    apiKey: String? = null,
    private val textModel: String = "gpt-4.1-mini",
    private val visionPrimary: String = "gpt-4.1"      // 이미지 입력 지원 모델
) {

    private val apiKeyEff: String = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private fun authHeader(builder: Request.Builder) =
        builder.addHeader("Authorization", "Bearer $apiKeyEff")
            .addHeader("Content-Type", "application/json")

    // ------------------ 유틸 ------------------

    private fun levelKo(level: String): String = when (level) {
        "elementary_low"  -> "초등학교 저학년(1~3학년)"
        "elementary_high" -> "초등학교 고학년(4~6학년)"
        "middle_school"   -> "중학생"
        "high_school"     -> "고등학생"
        else              -> "초등학교 저학년(1~3학년)"
    }

    private fun subjectKo(subject: String) = if (subject == "math") "수학" else "영어"

    private fun subjectHint(subject: String, level: String) =
        if (subject == "math") when (level) {
            "elementary_low"  -> "덧셈·뺄셈·기초 곱셈, 도형 인식, 시계 보기 등 기초 수학 개념"
            "elementary_high" -> "곱셈·나눗셈, 분수·소수, 도형의 넓이/둘레, 그래프 읽기"
            "middle_school"   -> "정수/유리수, 일차방정식, 함수 기초, 기하 증명, 확률/통계"
            "high_school"     -> "이차함수, 삼각함수, 지수/로그, 미적분 기초, 수열/급수"
            else              -> "기초 수학 개념"
        } else when (level) {
            "elementary_low"  -> "알파벳, 기초 단어, 간단한 문장, 기본 인사말"
            "elementary_high" -> "기본 문법, 어휘 확장, 짧은 글 읽기, 간단한 대화"
            "middle_school"   -> "문법 심화, 독해력 향상, 작문 기초, 듣기/말하기"
            "high_school"     -> "고급 문법, 논술/작문, 문학 분석, 토론/발표"
            else              -> "기초 영어 학습"
        }

    private fun findFirstJson(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) runCatching {
            JSONObject(text.substring(start, end + 1))
        }.getOrNull() else null
    }

    private fun normalizeNumberedToList(src: String?): List<String> {
        if (src.isNullOrBlank()) return emptyList()
        return src.lines()
            .map { it.trim().removePrefix("-").removePrefix("•") }
            .map { it.replace(Regex("^\\d+[.)]\\s*"), "") }
            .filter { it.isNotBlank() }
            .take(3)
    }

    private fun clamp01(x: Double): Double =
        when {
            x.isNaN() || x.isInfinite() -> 0.0
            x < 0.0 -> 0.0
            x > 1.0 -> 1.0
            else -> x
        }

    // ------------------ (A) 문제 생성 (한 번에) ------------------

    /**
     * 서버(Python) /generate 와 동일 형식의 JSON 문제를 한 번에 생성
     */
    suspend fun generateProblemOneShot(
        subject: String,
        level: String,
        targetDifficulty: Int = 3,
        previousTitle: String? = null,
        previousBody: String? = null,
        previousAnswer: String? = null
    ): ProblemMeta = withContext(Dispatchers.IO) {
        val seed = Random.nextInt().toUInt().toString()
        val sys = "너는 초중고 학생을 위한 문제 출제기다. 매번 주제/수치/문장을 다양화하라. 반드시 JSON만 출력한다."

        val prevBlock = buildString {
            if (!previousTitle.isNullOrBlank() || !previousBody.isNullOrBlank() || !previousAnswer.isNullOrBlank()) {
                appendLine()
                appendLine("이전 문제 정보:")
                appendLine("- 제목: ${previousTitle ?: "(제목 없음)"}")
                appendLine("- 본문(요약): ${previousBody?.replace("\n"," ")?.take(600) ?: "(본문 없음)"}")
                appendLine("- 정답: ${previousAnswer ?: "(정답 미상)"}")
                appendLine("- 이전 난이도: (미상)")
            }
        }

        val user = """
${levelKo(level)} 대상의 ${subjectKo(subject)} 문제를 출제한다.
출제 시드: $seed
난이도 체계: 1~5로 1이 매우쉬움, 2 쉬움, 3 보통, 4 어려움, 5 매우어려움 으로 가정하라, 목표 난이도(target) = ${targetDifficulty.coerceIn(1,5)}

출제 범위(힌트):
- ${subjectHint(subject, level)}
- 위 범위 안에서 한 문제당 핵심 개념을 랜덤으로 뽑아 출제하라.

규칙:
- **JSON만** 출력한다.
- "difficulty"는 반드시 **target과 동일한 값**으로 설정한다.
- 선택지가 있다면 한 줄에 하나씩 명확히 기입한다(수식은 TeX 또는 pseudo: frac(1,2) 허용).

출력(JSON만):
{
  "title": "문제 제목(간결)",
  "body": "문제 본문(줄바꿈/수식/diagram 가능)",
  "choices": null 또는 ["보기A","보기B", "..."],
  "answer": "정답 텍스트",
  "explain": "해설(짧고 명료)",
  "difficulty": 1~5 정수
}
$prevBlock
        """.trimIndent()

        val payload = JSONObject().apply {
            put("model", textModel)
            put("temperature", 0.9)
            put("response_format", JSONObject().put("type","json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role","system").put("content", sys))
                put(JSONObject().put("role","user").put("content", user))
            })
        }.toString().toRequestBody(jsonMedia)

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .also(::authHeader)
            .post(payload)
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("generate failed ${res.code}: ${res.message}")
            val txt = res.body.string()
            val content = JSONObject(txt)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val data = JSONObject(content)
            val title = data.optString("title").ifBlank { "[${subjectKo(subject)}] 문제" }
            val body = data.optString("body")
            val answer = data.optString("answer")
            val explain = data.optString("explain")
            val difficulty = targetDifficulty.coerceIn(1,5)
            val choices = if (data.has("choices") && !data.isNull("choices")) {
                val arr = data.getJSONArray("choices")
                (0 until arr.length()).map { i -> arr.getString(i) }
            } else emptyList()

            ProblemMeta(
                title = title,
                body = body,
                answer = answer,
                explain = explain,
                difficulty = difficulty,
                choices = choices
            )
        }
    }

    // ------------------ (B) 채점 (텍스트 + 비전) ------------------

    suspend fun grade(
        subject: String,
        answerText: String,
        expectedAnswer: String?,
        problemText: String?,
        elapsedSec: Int,
        processImageBase64: String? // "data:image/png;base64,..." 형식 또는 null
    ): GradeResult = withContext(Dispatchers.IO) {
        // 1) 텍스트 판정(JSON)
        val baseRule = if (subject == "math")
            "수학: 수식 동치/간단화 결과가 정답과 같은지(분수, 소수, 단위, 부호 포함)"
        else
            "영어: 문법/의미/철자/어순이 정답과 동등하거나 허용 변형인지"

        val sys = "너는 엄격한 채점관이다. 반드시 JSON만 출력한다."
        val user = buildString {
            appendLine("""학생답: "$answerText"""")
            if (!expectedAnswer.isNullOrBlank()) appendLine("""정답(정확 기준): "$expectedAnswer"""")
            if (!problemText.isNullOrBlank()) appendLine("문제 지문: ${problemText.take(800)}")
            appendLine("판정기준: $baseRule")
            appendLine("""출력형식: {"correct":true|false, "explain":"간단한 이유"}""")
            appendLine("- 애매하면 false로 판정하고 이유를 남겨라.")
        }

        val gradeReq = JSONObject().apply {
            put("model", textModel)
            put("temperature", 0.9)
            put("response_format", JSONObject().put("type","json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role","system").put("content", sys))
                put(JSONObject().put("role","user").put("content", user))
            })
        }.toString().toRequestBody(jsonMedia)

        val textEvalJson = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .also(::authHeader)
                .post(gradeReq)
                .build()
        ).execute().use { r ->
            if (!r.isSuccessful) JSONObject("""{"correct":false,"explain":"채점 호출 실패"}""")
            else {
                val body = r.body.string()
                val content = JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                findFirstJson(content) ?: JSONObject("""{"correct":false}""")
            }
        }

        val textIsCorrect = textEvalJson.optBoolean("correct", false)
        val explain = textEvalJson.optString("explain")

        // 2) (옵션) 비전/노트 → 4지표 스코어
        var lines = 0
        var chars = 0
        var illeg = 0.0
        var notes: List<String> = emptyList()

        if (!processImageBase64.isNullOrBlank()) {
            // 2-A) 이미지 → 노트/메트릭(JSON)
            val visionUser = """
You are an OCR+reasoning assistant for students' handwritten solutions.
From the image, return JSON ONLY:
{
  "notes": ["3-8 concise bullets describing key steps/formulas/claims as written"],
  "raw_text": "best-effort linearized text/OCR (optional, short)",
  "metrics": {"line_count": int, "char_count_est": int, "illegible_ratio": 0.0-1.0}
}
No extra commentary.
            """.trimIndent()

            val payload = JSONObject().apply {
                put("model", visionPrimary)
                put("temperature", 0.9)
                put("response_format", JSONObject().put("type","json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role","user").put("content",
                        JSONArray().apply {
                            put(JSONObject().put("type","text").put("text", visionUser))
                            put(JSONObject().put("type","image_url").put("image_url",
                                JSONObject().put("url", processImageBase64)
                            ))
                        }
                    ))
                })
            }.toString().toRequestBody(jsonMedia)

            val vJson = client.newCall(
                Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .also(::authHeader)
                    .post(payload)
                    .build()
            ).execute().use { res ->
                if (!res.isSuccessful) null
                else {
                    val body = res.body.string()
                    val content = JSONObject(body)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    findFirstJson(content)
                }
            }

            if (vJson != null) {
                val metrics = vJson.optJSONObject("metrics") ?: JSONObject()
                lines = metrics.optInt("line_count", 0)
                chars = metrics.optInt("char_count_est", 0)
                illeg = clamp01(metrics.optDouble("illegible_ratio", 0.0))
                val arr = vJson.optJSONArray("notes")
                if (arr != null) {
                    val tmp = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        tmp += arr.optString(i).orEmpty().take(200)
                    }
                    notes = tmp.take(8)
                }
            }

            // 2-B) 노트/원문 요약 → 4지표 초안(JSON)
            val analysisPrompt = buildString {
                appendLine("역할: 학생의 손글씨 풀이 '과정' 평가자.")
                appendLine("입력:")
                appendLine("- text_is_correct=$textIsCorrect")
                appendLine("- problem_text=${problemText ?: ""}")
                appendLine("- expected_answer=${expectedAnswer ?: ""}")
                appendLine("- notes=${notes}")
                appendLine("- raw_text=(생략)")
                appendLine()
                appendLine("지시:")
                appendLine("- 아래 4개 항목을 0~10 정수로 채점하고 각 항목에 간단 피드백을 준다.")
                appendLine("- 항목: accuracy, approach, completeness, creativity")
                appendLine("- 4개 점수를 전부 같은 값으로 내지 말 것(최소 2개 이상 서로 달라야 함).")
                appendLine("- JSON만 반환.")
            }

            val analysisReq = JSONObject().apply {
                put("model", textModel)
                put("temperature", 0.9)
                put("response_format", JSONObject().put("type","json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role","user").put("content", analysisPrompt))
                })
            }.toString().toRequestBody(jsonMedia)

            val analysisJson = client.newCall(
                Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .also(::authHeader)
                    .post(analysisReq)
                    .build()
            ).execute().use { res ->
                if (!res.isSuccessful) null
                else {
                    val body = res.body.string()
                    val content = JSONObject(body)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    findFirstJson(content)
                }
            }

            // 2-C) 4지표 파싱 → 메트릭 기반 보정/분산
            val parsed = parseAnalysisV2(analysisJson)
            val diversified = diversifyFinalScoresV3(parsed.scores, lines, chars, illeg, textIsCorrect)
            val finalScores =
                if (setOf(diversified["정확도"], diversified["접근법"], diversified["완성도"], diversified["창의성"]).size < 2) {
                    forceDiversityOnTies(diversified, (lines + chars + (illeg * 10).toInt()))
                } else diversified

            // 3) 강점/개선(숫자 줄머리 2~3개) 생성
            val strengths = generateNumbered(
                system = "너는 학생 풀이를 근거로 핵심만 뽑아내는 분석가다.",
                user = """
너는 학생 풀이 분석가다. 아래 정보를 바탕으로 '잘한 점' 2~3가지를 한국어로 작성하라.

형식:
- 반드시 다음 형식으로 출력:
  1. 문장
  2. 문장
  (필요하면 3. 문장)
제약:
- 점수/지표명(정확도, 접근법, 완성도, 창의성) 언급 금지
- 풀이에 실제로 나타난 행동/전략/표현을 근거로 구체적으로 작성

정보:
- 문제: ${problemText ?: ""}
- 기준 정답: ${expectedAnswer ?: ""}
- 텍스트 판정: correct=$textIsCorrect, 이유="$explain"
- 이미지/노트 요약: $notes
                """.trimIndent()
            )
            val improvements = generateNumbered(
                system = "너는 실행가능한 조언만 주는 엄격한 코치다.",
                user = """
너는 학생 답안 코치다. 아래 정보를 바탕으로 학생의 풀이 과정을 개선하기 위한
'구체적인 행동 제안' 2~3가지를 한국어로 작성하라.

형식:
- 반드시 다음 형식으로 출력:
  1. 문장
  2. 문장
  (필요하면 3. 문장)
제약:
- 점수/지표명(정확도, 접근법, 완성도, 창의성) 언급 금지
- 상투어 금지. 실제 풀이에 바로 적용 가능한 행동만 제시

정보:
- 문제: ${problemText ?: ""}
- 기준 정답: ${expectedAnswer ?: ""}
- 텍스트 판정: correct=$textIsCorrect, 이유="$explain"
- 과정 요약/노트: $notes
                """.trimIndent()
            )

            val textPart = if (textIsCorrect) 100 else 40
            val processAvg = (finalScores["정확도"]!! + finalScores["접근법"]!! + finalScores["완성도"]!! + finalScores["창의성"]!!) / 4.0
            val score = (textPart * 0.6 + (processAvg / 10.0) * 40).roundToInt()

            return@withContext GradeResult(
                score = score,
                correct = textIsCorrect,
                explain = explain,
                aiConfidence = if (textIsCorrect) 90 else 80,
                timeSpent = elapsedSec,
                criteria = finalScores,
                strengths = strengths,
                improvements = improvements,
                summary = buildSummary(textIsCorrect, explain, strengths, improvements, elapsedSec)
            )
        }

        // ■ 비전 이미지가 없을 때: 기본 분산 로직만
        val base = if (textIsCorrect) 5 else 3
        val varied = forceDiversityOnTies(
            mapOf("정확도" to base, "접근법" to base, "완성도" to base, "창의성" to base),
            elapsedSec.coerceAtLeast(1)
        )
        val textPart = if (textIsCorrect) 100 else 40
        val processAvg = (varied["정확도"]!! + varied["접근법"]!! + varied["완성도"]!! + varied["창의성"]!!) / 4.0
        val score = (textPart * 0.6 + (processAvg / 10.0) * 40).roundToInt()

        val strengths = generateNumbered(
            system = "너는 학생 풀이를 근거로 핵심만 뽑아내는 분석가다.",
            user = """
학생 풀이 상황을 바탕으로 '잘한 점' 2~3가지를 한국어로 작성하라.
형식은 1., 2., (3. 선택) 번호 목록으로만 출력.
문제: ${problemText ?: ""}
기준 정답: ${expectedAnswer ?: ""}
텍스트 판정: correct=$textIsCorrect, 이유="$explain"
            """.trimIndent()
        )
        val improvements = generateNumbered(
            system = "너는 실행가능한 조언만 주는 엄격한 코치다.",
            user = """
학생 풀이 개선을 위한 '구체적인 행동 제안' 2~3가지를 한국어로 작성하라.
형식은 1., 2., (3. 선택) 번호 목록으로만 출력.
문제: ${problemText ?: ""}
기준 정답: ${expectedAnswer ?: ""}
텍스트 판정: correct=$textIsCorrect, 이유="$explain"
            """.trimIndent()
        )

        GradeResult(
            score = score,
            correct = textIsCorrect,
            explain = explain,
            aiConfidence = if (textIsCorrect) 90 else 80,
            timeSpent = elapsedSec,
            criteria = varied,
            strengths = strengths,
            improvements = improvements,
            summary = buildSummary(textIsCorrect, explain, strengths, improvements, elapsedSec)
        )
    }

    // ------------------ (C) 보조 루틴들 ------------------

    private fun buildSummary(
        correct: Boolean,
        explain: String?,
        strengths: List<String>,
        improvements: List<String>,
        elapsedSec: Int
    ): String {
        val parts = mutableListOf<String>()
        parts += "정답 판정: " + if (correct) "정답입니다." else "오답입니다."
        if (!explain.isNullOrBlank()) parts += "판정 이유: $explain"
        if (strengths.isNotEmpty()) {
            parts += "잘한 점:\n" + strengths.mapIndexed { i, s -> "${i+1}. $s" }.joinToString("\n")
        }
        if (improvements.isNotEmpty()) {
            parts += "개선 제안:\n" + improvements.mapIndexed { i, s -> "${i+1}. $s" }.joinToString("\n")
        }
        if (elapsedSec > 0) parts += "풀이 시간: 약 ${elapsedSec}초."
        return parts.joinToString("\n\n")
    }

    private data class ParsedAnalysis(
        val scores: Map<String, Int>,
        val comment: String
    )

    private fun parseAnalysisV2(obj: JSONObject?): ParsedAnalysis {
        if (obj == null) return ParsedAnalysis(
            scores = mapOf("정확도" to 5, "접근법" to 5, "완성도" to 5, "창의성" to 5),
            comment = ""
        )

        fun scoreAny(key: String, fallback: Int): Int {
            val v = obj.opt(key)
            val num = when (v) {
                is Number -> v.toDouble()
                is JSONObject -> listOf("score","value","val").firstNotNullOfOrNull { k ->
                    (v.opt(k) as? Number)?.toDouble()
                } ?: fallback.toDouble()
                is String -> v.toDoubleOrNull() ?: fallback.toDouble()
                else -> fallback.toDouble()
            }
            return num.roundToInt().coerceIn(0, 10)
        }

        fun fbAny(key: String): String {
            val v = obj.opt(key)
            return when (v) {
                is JSONObject -> listOf("feedback","comment","reason","text")
                    .firstNotNullOfOrNull { k -> v.optString(k, "") }
                    ?.trim().orEmpty()
                is String -> v.trim()
                is JSONArray -> (0 until v.length()).joinToString("; ") { v.optString(it) }
                else -> ""
            }
        }

        val a = scoreAny("accuracy", 5)
        val b = scoreAny("approach", 5)
        val c = scoreAny("completeness", 5)
        val d = scoreAny("creativity", 5)

        val parts = mutableListOf<String>()
        listOf("정확도" to "accuracy", "접근법" to "approach", "완성도" to "completeness", "창의성" to "creativity").forEach { (ko, en) ->
            val t = fbAny(en)
            if (t.isNotBlank()) parts += "$ko: $t"
        }
        val summaryStr = obj.optString("summary").takeIf { it.isNotBlank() }
        if (summaryStr != null) parts += summaryStr

        val sugg = obj.opt("suggestions")
        if (sugg is JSONArray && sugg.length() > 0) {
            parts += "제안: " + (0 until sugg.length()).joinToString("; ") { sugg.optString(it) }
        } else if (sugg is String && sugg.isNotBlank()) {
            parts += "제안: $sugg"
        }

        val strengths = obj.opt("strengths")
        if (strengths is JSONArray && strengths.length() > 0) {
            parts += "강점: " + (0 until strengths.length()).joinToString("; ") {
                strengths.optString(
                    it
                )
            }
        } else if (strengths is String && strengths.isNotBlank()) {
            parts += "강점: $strengths"
        }

        return ParsedAnalysis(
            scores = mapOf("정확도" to a, "접근법" to b, "완성도" to c, "창의성" to d),
            comment = parts.joinToString(" ").take(1000)
        )
    }

    /** Double/Int 혼용 구간을 안전하게 분리한 4지표 분산 보정 */
    private fun diversifyFinalScoresV3(
        base: Map<String, Int>?,
        lines: Int,
        chars: Int,
        illeg: Double,
        textIsCorrect: Boolean
    ): Map<String, Int> {
        var a = (base?.get("정확도") ?: if (textIsCorrect) 5 else 3).coerceIn(0, 10)
        var b = (base?.get("접근법") ?: 5).coerceIn(0, 10)
        var c = (base?.get("완성도") ?: 5).coerceIn(0, 10)
        var d = (base?.get("창의성") ?: 5).coerceIn(0, 10)

        fun clipD(x: Double) = min(10, max(0, x.roundToInt()))
        fun clipI(x: Int)    = x.coerceIn(0, 10)

        if (!textIsCorrect) a = min(a, 4)

        val mb = clipD(2 + 0.0045 * chars + 0.45 * min(lines, 10))
        val mc = clipD(2 + 0.0065 * chars + 0.60 * min(lines, 12))
        val md = clipD(2 + 0.0035 * chars + 0.35 * min(lines, 12) - (if (illeg >= 0.5) 2.0 else 0.0))

        val metricW = if (lines >= 3 && chars >= 30) 0.35 else 0.15

        b = clipD((1 - metricW) * b + metricW * mb)
        c = clipD((1 - metricW) * c + metricW * mc)
        d = clipD((1 - metricW) * d + metricW * md)

        if (illeg >= 0.6) {
            b = min(b, 2); c = min(c, 2); d = min(d, 3)
        }

        if (setOf(b, c, d).size < 3) {
            val seed = (chars + 7 * lines + (illeg * 10).toInt() + if (textIsCorrect) 1 else 0) % 3
            val patterns = arrayOf(intArrayOf(-1, 0, +1), intArrayOf(0, +1, -1), intArrayOf(+1, -1, 0))
            val p = patterns[seed]
            b = clipI(b + p[0])
            if (setOf(b, c, d).size < 3) c = clipI(c + p[1])
            if (setOf(b, c, d).size < 3) d = clipI(d + p[2])
        }

        return mapOf("정확도" to a, "접근법" to b, "완성도" to c, "창의성" to d)
    }

    private fun forceDiversityOnTies(scores: Map<String, Int>, seed: Int): Map<String, Int> {
        val a = scores["정확도"] ?: 5
        val b = scores["접근법"] ?: 5
        val c = scores["완성도"] ?: 5
        val d = scores["창의성"] ?: 5
        if (setOf(a, b, c, d).size >= 2) return scores

        fun clipI(x: Int) = x.coerceIn(0, 10)
        val patterns = arrayOf(
            intArrayOf(0, +1, 0, -1),
            intArrayOf(0, 0, +1, -1),
            intArrayOf(0, +1, -1, 0),
            intArrayOf(+1, -1, 0, 0),
        )
        val p = patterns[kotlin.math.abs(seed) % patterns.size]
        return mapOf(
            "정확도" to clipI(a + p[0]),
            "접근법" to clipI(b + p[1]),
            "완성도" to clipI(c + p[2]),
            "창의성" to clipI(d + p[3]),
        )
    }

    private fun generateNumbered(system: String, user: String): List<String> {
        val payload = JSONObject().apply {
            put("model", textModel)
            put("temperature", 0.9)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role","system").put("content", system))
                put(JSONObject().put("role","user").put("content", user))
            })
        }.toString().toRequestBody(jsonMedia)

        return try {
            client.newCall(
                Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .also(::authHeader)
                    .post(payload)
                    .build()
            ).execute().use { res ->
                if (!res.isSuccessful) return emptyList()
                val txt = res.body.string()
                val content = JSONObject(txt)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                normalizeNumberedToList(content)
            }
        } catch (t: Throwable) {
            Log.w("OpenAIInApp", "generateNumbered fail: ${t.message}")
            emptyList()
        }
    }
}

/* ---------- DTO ---------- */
data class ProblemMeta(
    val title: String,
    val body: String,
    val answer: String,
    val explain: String,
    val difficulty: Int,
    val choices: List<String>
)

data class GradeResult(
    val score: Int,
    val correct: Boolean,
    val explain: String,
    val aiConfidence: Int,
    val timeSpent: Int,
    val criteria: Map<String, Int>,
    val strengths: List<String>,
    val improvements: List<String>,
    val summary: String
)
