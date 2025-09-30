// app/src/main/java/com/example/ailearningapp/data/repository/LeaderboardRepository.kt
package com.example.ailearningapp.data.repository

import com.example.ailearningapp.data.model.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow

/**
 * 리더보드 데이터를 관리하는 Repository
 * Firebase Firestore에서 실제 데이터를 가져옵니다.
 */
class LeaderboardRepository {
    
    private val firebaseUserRepo = FirebaseUserRepository()
    
    /**
     * 전체 리더보드 데이터 가져오기
     */
    suspend fun getLeaderboardData(
        currentUserId: String? = null,
        currentUserSchool: String? = null,
        currentUserGrade: Int? = null
    ): LeaderboardData {
        // Firebase에서 실제 데이터 가져오기
        val uid = currentUserId ?: FirebaseAuth.getInstance().currentUser?.uid
        return firebaseUserRepo.getLeaderboardData(uid)
    }
    
    /**
     * 학교별 랭킹 가져오기
     */
    suspend fun getSchoolRankings(): List<SchoolStats> {
        val leaderboardData = firebaseUserRepo.getLeaderboardData(null)
        return leaderboardData.schoolRankings
    }
    
    /**
     * 학년별 랭킹 가져오기
     */
    suspend fun getGradeRankings(): List<GradeStats> {
        val leaderboardData = firebaseUserRepo.getLeaderboardData(null)
        return leaderboardData.gradeRankings
    }
    
    /**
     * 실시간 리더보드 업데이트 Flow
     */
    fun getLeaderboardFlow(): Flow<LeaderboardData> {
        return firebaseUserRepo.getLeaderboardFlow()
    }
}