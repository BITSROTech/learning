// app/src/main/java/com/example/ailearningapp/data/local/Prefs.kt
package com.example.ailearningapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ailearningapp.navigation.GradeBand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// DataStore 파일명: settings.preferences_pb
private val Context.dataStore by preferencesDataStore("settings")

object Prefs {
    /* ---------- Keys & Const ---------- */
    private const val KEY_GRADE = "grade_band"
    private const val KEY_HISTORY = "history_json"
    private const val MAX_HISTORY = 200

    private val K_GRADE = stringPreferencesKey(KEY_GRADE)
    private val K_HISTORY = stringPreferencesKey(KEY_HISTORY)

    /* ---------- GradeBand ---------- */

    /** 저장된 학년대(없으면 ELEMENTARY_UPPER 기본) */
    fun gradeBandFlow(ctx: Context): Flow<GradeBand> =
        ctx.dataStore.data.map { pref ->
            val saved = pref[K_GRADE]
            saved?.let { runCatching { GradeBand.valueOf(it) }.getOrNull() }
                ?: GradeBand.ELEMENTARY_UPPER
        }

    suspend fun setGradeBand(ctx: Context, grade: GradeBand) {
        ctx.dataStore.edit { it[K_GRADE] = grade.name }
    }

    /* ---------- History (MVP: JSON Array in a single pref) ---------- */

    /**
     * 히스토리 스트림 (최신순).
     * @param subject 필터(subject 값이 일치하는 항목만), null이면 전체
     * @param gradeBand 필터(gradeBand 값이 일치하는 항목만), null이면 전체
     */
    fun historyFlow(
        ctx: Context,
        subject: String? = null,
        gradeBand: GradeBand? = null
    ): Flow<List<HistoryItem>> =
        ctx.dataStore.data.map { pref ->
            val raw = pref[K_HISTORY] ?: "[]"
            val list = raw.safeParseHistory()
            list
                .let { if (subject != null) it.filter { h -> h.subject == subject } else it }
                .let { if (gradeBand != null) it.filter { h -> h.gradeBand == gradeBand.name } else it }
                .sortedByDescending { it.createdAt }
        }

    /** 히스토리 1건 추가 (MAX_HISTORY 초과 시 오래된 순으로 컷) */
    suspend fun appendHistory(ctx: Context, item: HistoryItem) {
        ctx.dataStore.edit { pref ->
            val arr = JSONArray(pref[K_HISTORY] ?: "[]")
            arr.put(item.toJson())
            val kept = JSONArray()
            val start = (arr.length() - MAX_HISTORY).coerceAtLeast(0)
            for (i in start until arr.length()) kept.put(arr.get(i))
            pref[K_HISTORY] = kept.toString()
        }
    }

    /** 히스토리 전체 삭제 */
    suspend fun clearHistory(ctx: Context) {
        ctx.dataStore.edit { it[K_HISTORY] = "[]" }
    }

    /** 특정 문제 ID로 삭제 */
    suspend fun removeHistory(ctx: Context, problemId: String) {
        ctx.dataStore.edit { pref ->
            val arr = JSONArray(pref[K_HISTORY] ?: "[]")
            val kept = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("problemId") != problemId) kept.put(o)
            }
            pref[K_HISTORY] = kept.toString()
        }
    }

    /** 히스토리 JSON(문자열) 내보내기 (백업/공유용) */
    fun exportHistoryJson(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[K_HISTORY] ?: "[]" }

    /**
     * 히스토리 JSON(문자열) 가져오기.
     * - 잘못된 형식이면 무시
     * - MAX_HISTORY 적용
     * @return 반영된 항목 수
     */
    suspend fun importHistoryJson(ctx: Context, json: String): Int {
        val parsed = runCatching { JSONArray(json) }.getOrNull() ?: return 0
        val normalized = JSONArray()
        for (i in 0 until parsed.length()) {
            (parsed.optJSONObject(i)?.toHistoryItem())?.let { normalized.put(it.toJson()) }
        }
        ctx.dataStore.edit { pref ->
            // 합치고 컷(기존 + 새로 가져온 것)
            val current = JSONArray(pref[K_HISTORY] ?: "[]")
            val merged = JSONArray()
            // 기존 뒤쪽 MAX_HISTORY 만큼만 남기기 위해 먼저 합치고 컷
            for (i in 0 until current.length()) merged.put(current.get(i))
            for (i in 0 until normalized.length()) merged.put(normalized.get(i))

            val kept = JSONArray()
            val start = (merged.length() - MAX_HISTORY).coerceAtLeast(0)
            for (i in start until merged.length()) kept.put(merged.get(i))
            pref[K_HISTORY] = kept.toString()
        }
        return runCatching { JSONArray(json).length() }.getOrDefault(0)
    }
}

/* ───────── History 모델 (로컬 저장용) ───────── */

data class HistoryItem(
    val problemId: String,
    val subject: String,
    val gradeBand: String,
    val title: String,
    val score: Int,
    val summary: String,
    val elapsedSec: Int,
    val criteriaJson: String,
    val createdAt: Long
)

private fun JSONObject.toHistoryItem() = HistoryItem(
    problemId = optString("problemId"),
    subject = optString("subject"),
    gradeBand = optString("gradeBand"),
    title = optString("title"),
    score = optInt("score"),
    summary = optString("summary"),
    elapsedSec = optInt("elapsedSec"),
    criteriaJson = optString("criteriaJson"),
    createdAt = optLong("createdAt")
)

private fun HistoryItem.toJson(): JSONObject = JSONObject().apply {
    put("problemId", problemId)
    put("subject", subject)
    put("gradeBand", gradeBand)
    put("title", title)
    put("score", score)
    put("summary", summary)
    put("elapsedSec", elapsedSec)
    put("criteriaJson", criteriaJson)
    put("createdAt", createdAt)
}

/* ───────── JSON Helper ───────── */

private fun String.safeParseHistory(): List<HistoryItem> = try {
    val arr = JSONArray(this)
    buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            add(obj.toHistoryItem())
        }
    }
} catch (_: JSONException) {
    emptyList()
}
