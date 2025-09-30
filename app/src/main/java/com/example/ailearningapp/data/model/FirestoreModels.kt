// app/src/main/java/com/example/ailearningapp/data/model/FirestoreModels.kt
package com.example.ailearningapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore에 저장될 사용자 프로필 데이터
 */
data class FirestoreUserProfile(
    @DocumentId
    val id: String = "",  // 문서 ID (일반적으로 uid 사용)
    
    @PropertyName("uid")
    val uid: String = "",
    
    @PropertyName("email")
    val email: String = "",
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("school")
    val school: String = "",
    
    @PropertyName("grade")
    val grade: Int = 1,
    
    @PropertyName("totalScore")
    val totalScore: Int = 0,
    
    @PropertyName("solvedProblems")
    val solvedProblems: Int = 0,
    
    @PropertyName("easySolved")
    val easySolved: Int = 0,
    
    @PropertyName("mediumSolved")
    val mediumSolved: Int = 0,
    
    @PropertyName("hardSolved")
    val hardSolved: Int = 0,
    
    @PropertyName("provider")
    val provider: String = "",  // "Google" or "Kakao"
    
    @PropertyName("photoUrl")
    val photoUrl: String? = null,
    
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Timestamp? = null,
    
    @ServerTimestamp
    @PropertyName("updatedAt")
    val updatedAt: Timestamp? = null,
    
    @PropertyName("lastActiveAt")
    val lastActiveAt: Timestamp? = null
) {
    // Firestore는 인수가 없는 생성자를 요구함
    constructor() : this("")
}

/**
 * 문제 풀이 기록 (히스토리 용도)
 */
data class FirestoreSolveRecord(
    @DocumentId
    val id: String = "",
    
    @PropertyName("userId")
    val userId: String = "",
    
    @PropertyName("userName")
    val userName: String = "",
    
    @PropertyName("school")
    val school: String = "",
    
    @PropertyName("grade")
    val grade: Int = 1,
    
    @PropertyName("subject")
    val subject: String = "",  // "math" or "english"
    
    @PropertyName("difficulty")
    val difficulty: Int = 1,
    
    @PropertyName("difficultyLevel")
    val difficultyLevel: String = "",  // "EASY", "MEDIUM", "HARD"
    
    @PropertyName("points")
    val points: Int = 0,
    
    @PropertyName("isCorrect")
    val isCorrect: Boolean = false,
    
    @PropertyName("problemText")
    val problemText: String = "",
    
    @PropertyName("userAnswer")
    val userAnswer: String = "",
    
    @PropertyName("correctAnswer")
    val correctAnswer: String? = null,
    
    @PropertyName("elapsedSeconds")
    val elapsedSeconds: Int = 0,
    
    @ServerTimestamp
    @PropertyName("solvedAt")
    val solvedAt: Timestamp? = null
) {
    constructor() : this("")
}

/**
 * 학교별 통계 캐시 (실시간 계산 부담 줄이기)
 */
data class FirestoreSchoolStats(
    @DocumentId
    val id: String = "",  // 학교명을 ID로 사용
    
    @PropertyName("schoolName")
    val schoolName: String = "",
    
    @PropertyName("totalStudents")
    val totalStudents: Int = 0,
    
    @PropertyName("totalScore")
    val totalScore: Long = 0,  // Int 오버플로우 방지
    
    @PropertyName("totalProblems")
    val totalProblems: Long = 0,
    
    @ServerTimestamp
    @PropertyName("lastUpdated")
    val lastUpdated: Timestamp? = null
) {
    constructor() : this("")
    
    val averageScore: Double
        get() = if (totalStudents > 0) totalScore.toDouble() / totalStudents else 0.0
}

/**
 * 학년별 통계 캐시
 */
data class FirestoreGradeStats(
    @DocumentId
    val id: String = "",  // "grade_1" ~ "grade_12"
    
    @PropertyName("grade")
    val grade: Int = 1,
    
    @PropertyName("totalStudents")
    val totalStudents: Int = 0,
    
    @PropertyName("totalScore")
    val totalScore: Long = 0,
    
    @PropertyName("totalProblems")
    val totalProblems: Long = 0,
    
    @ServerTimestamp
    @PropertyName("lastUpdated")
    val lastUpdated: Timestamp? = null
) {
    constructor() : this("")
    
    val averageScore: Double
        get() = if (totalStudents > 0) totalScore.toDouble() / totalStudents else 0.0
}