// app/src/main/java/com/example/ailearningapp/data/model/Submission.kt
package com.example.ailearningapp.data.model

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import androidx.compose.runtime.Immutable
import java.io.ByteArrayOutputStream

@Immutable
data class Submission(
    /** 채점·히스토리 연계를 위한 문제 ID */
    val problemId: String,
    /** 텍스트 정답(최종 답안 입력란의 값) */
    val answerText: String,
    /** 풀이 과정 필기 이미지(없을 수 있음) */
    val processImage: Bitmap?,
    /** 풀이 시간(초) */
    val elapsedSec: Int
) {
    /** 공백 제거한 정답 */
    val safeAnswer: String get() = answerText.trim()

    /** 필기 이미지 존재 여부 */
    val hasProcessImage: Boolean get() = processImage != null

    /**
     * 서버 채점 요청에 바로 넣어 쓸 수 있는 JSON 페이로드 생성
     * (FastAPI 게이트웨이의 /grade에서 기대하는 필드와 동일)
     */
    fun toPayloadJson(subject: String): String = buildString {
        append('{')
        append("\"problem_id\":\"").append(problemId.jsonEscaped()).append("\",")
        append("\"subject\":\"").append(subject.jsonEscaped()).append("\",")
        append("\"answer_text\":\"").append(safeAnswer.jsonEscaped()).append("\",")
        append("\"elapsed_sec\":").append(elapsedSec)
        append('}')
    }

    /**
     * 필기 이미지를 PNG(ByteArray)로 변환. 이미지가 없으면 null
     * @param quality PNG는 무손실이라 보통 100, 형식 통일을 위해 인자만 보존
     */
    fun processImagePng(quality: Int = 100): ByteArray? {
        val bmp = processImage ?: return null
        return ByteArrayOutputStream().use { bos ->
            bmp.compress(CompressFormat.PNG, quality, bos)
            bos.toByteArray()
        }
    }
}

/* --------- small utils --------- */

private fun String.jsonEscaped(): String =
    this.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
