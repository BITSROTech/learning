// app/src/main/java/com/example/ailearningapp/data/repository/UserProfileRepository.kt
package com.example.ailearningapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.ailearningapp.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import java.io.IOException
import com.google.firebase.auth.FirebaseAuth

private val Context.userProfileDataStore by preferencesDataStore("user_profile")

/**
 * 사용자 프로필 및 점수 관리 Repository
 */
class UserProfileRepository(private val context: Context) {

    // Firebase 연동을 위한 FirebaseUserRepository 추가
    private val firebaseUserRepo = FirebaseUserRepository()

    companion object {
        private val KEY_SCHOOL = stringPreferencesKey("school")
        private val KEY_GRADE = intPreferencesKey("grade")
        private val KEY_TOTAL_SCORE = intPreferencesKey("total_score")
        private val KEY_SOLVED_PROBLEMS = intPreferencesKey("solved_problems")
        private val KEY_EASY_SOLVED = intPreferencesKey("easy_solved")
        private val KEY_MEDIUM_SOLVED = intPreferencesKey("medium_solved")
        private val KEY_HARD_SOLVED = intPreferencesKey("hard_solved")
    }

    private val dataStore = context.userProfileDataStore

    /**
     * 학교 정보 저장
     */
    suspend fun saveSchool(school: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHOOL] = school
        }
    }

    /**
     * 학년 정보 저장
     */
    suspend fun saveGrade(grade: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_GRADE] = grade.coerceIn(1, 12)
        }
    }

    /**
     * 학교와 학년 동시 저장
     */
    suspend fun saveProfile(school: String, grade: Int) {
        // 로컬 저장
        dataStore.edit { preferences ->
            preferences[KEY_SCHOOL] = school
            preferences[KEY_GRADE] = grade.coerceIn(1, 12)
        }

        // Firebase에도 저장
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val currentUser = com.example.ailearningapp.data.local.AuthStore.currentUserOnce(context)

        // Firebase Auth UID가 없으면 Kakao 사용자일 가능성
        val uid = firebaseUser?.uid ?: currentUser?.let { user ->
            if (user.isKakao) "kakao:${user.uid}" else null
        }

        if (uid != null) {
            firebaseUserRepo.updateUserProfile(uid, school, grade)
        }

    }

    /**
     * 문제 정답시 점수 추가 (로컬만, Firebase는 SolveViewModel에서 처리)
     */
    suspend fun addScore(difficultyLevel: DifficultyLevel) {
        dataStore.edit { preferences ->
            val currentScore = preferences[KEY_TOTAL_SCORE] ?: 0
            val solvedCount = preferences[KEY_SOLVED_PROBLEMS] ?: 0

            preferences[KEY_TOTAL_SCORE] = currentScore + difficultyLevel.points
            preferences[KEY_SOLVED_PROBLEMS] = solvedCount + 1

            // 난이도별 해결 수 카운트
            when (difficultyLevel) {
                DifficultyLevel.EASY -> {
                    val easySolved = preferences[KEY_EASY_SOLVED] ?: 0
                    preferences[KEY_EASY_SOLVED] = easySolved + 1
                }
                DifficultyLevel.MEDIUM -> {
                    val mediumSolved = preferences[KEY_MEDIUM_SOLVED] ?: 0
                    preferences[KEY_MEDIUM_SOLVED] = mediumSolved + 1
                }
                DifficultyLevel.HARD -> {
                    val hardSolved = preferences[KEY_HARD_SOLVED] ?: 0
                    preferences[KEY_HARD_SOLVED] = hardSolved + 1
                }
            }
        }

        // 주의: Firebase 점수 업데이트는 SolveViewModel에서 문제 정보와 함께 처리
    }

    /**
     * 프로필 정보 Flow
     */
    fun profileFlow(): Flow<Pair<String?, Int?>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                Pair(
                    preferences[KEY_SCHOOL],
                    preferences[KEY_GRADE]
                )
            }
    }

    /**
     * 점수 정보 Flow
     */
    fun scoreFlow(): Flow<Triple<Int, Int, Map<DifficultyLevel, Int>>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val totalScore = preferences[KEY_TOTAL_SCORE] ?: 0
                val solvedProblems = preferences[KEY_SOLVED_PROBLEMS] ?: 0
                val difficultyStats = mapOf(
                    DifficultyLevel.EASY to (preferences[KEY_EASY_SOLVED] ?: 0),
                    DifficultyLevel.MEDIUM to (preferences[KEY_MEDIUM_SOLVED] ?: 0),
                    DifficultyLevel.HARD to (preferences[KEY_HARD_SOLVED] ?: 0)
                )
                Triple(totalScore, solvedProblems, difficultyStats)
            }
    }

    /**
     * 전체 사용자 데이터 가져오기
     */
    suspend fun getUserData(): UserProfileData {
        val preferences = dataStore.data.first()
        return UserProfileData(
            school = preferences[KEY_SCHOOL],
            grade = preferences[KEY_GRADE],
            totalScore = preferences[KEY_TOTAL_SCORE] ?: 0,
            solvedProblems = preferences[KEY_SOLVED_PROBLEMS] ?: 0,
            easySolved = preferences[KEY_EASY_SOLVED] ?: 0,
            mediumSolved = preferences[KEY_MEDIUM_SOLVED] ?: 0,
            hardSolved = preferences[KEY_HARD_SOLVED] ?: 0
        )
    }

    /**
     * 모든 프로필 데이터 초기화
     */
    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

/**
 * 사용자 프로필 데이터
 */
data class UserProfileData(
    val school: String? = null,
    val grade: Int? = null,
    val totalScore: Int = 0,
    val solvedProblems: Int = 0,
    val easySolved: Int = 0,
    val mediumSolved: Int = 0,
    val hardSolved: Int = 0
)