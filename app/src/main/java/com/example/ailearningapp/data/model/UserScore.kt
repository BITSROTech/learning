// app/src/main/java/com/example/ailearningapp/data/model/UserScore.kt
package com.example.ailearningapp.data.model

import androidx.compose.runtime.Immutable

/**
 * 사용자 점수 관련 데이터 모델
 */
@Immutable
data class UserScore(
    val userId: String,
    val userName: String,
    val school: String,
    val grade: Int,
    val totalScore: Int,
    val solvedProblems: Int,
    val rank: Int? = null  // 순위 (리더보드에서 계산)
)

/**
 * 문제 난이도별 점수 시스템
 */
enum class DifficultyLevel(val displayName: String, val points: Int) {
    EASY("초급", 2),      // +2점
    MEDIUM("중급", 3),    // +3점 
    HARD("고급", 5);      // +5점
    
    companion object {
        /**
         * 1-10 난이도를 3단계로 분류
         * 1-3: 초급, 4-6: 중급, 7-10: 고급
         */
        fun fromDifficulty(difficulty: Int?): DifficultyLevel {
            return when (difficulty) {
                in 1..3 -> EASY
                in 4..6 -> MEDIUM
                in 7..10 -> HARD
                else -> EASY  // 기본값
            }
        }
    }
}

/**
 * 리더보드 항목
 */
@Immutable
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val userName: String,
    val school: String?,
    val grade: Int?,
    val totalScore: Int,
    val solvedProblems: Int
)

/**
 * 학교별 통계
 */
@Immutable
data class SchoolStats(
    val school: String,
    val totalStudents: Int,
    val totalScore: Int,
    val averageScore: Double,
    val rank: Int
)

/**
 * 학년별 통계
 */
@Immutable
data class GradeStats(
    val grade: Int,
    val totalStudents: Int, 
    val totalScore: Int,
    val averageScore: Double,
    val rank: Int
)

/**
 * 리더보드 데이터
 */
@Immutable
data class LeaderboardData(
    val topStudents: List<LeaderboardEntry> = emptyList(),
    val schoolRankings: List<SchoolStats> = emptyList(),
    val gradeRankings: List<GradeStats> = emptyList(),
    val userRank: LeaderboardEntry? = null  // 현재 사용자의 순위
)