// app/src/main/java/com/example/ailearningapp/data/repository/FirebaseUserRepository.kt
package com.example.ailearningapp.data.repository

import com.example.ailearningapp.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase Firestore를 사용한 사용자 데이터 관리
 */
class FirebaseUserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val schoolStatsCollection = db.collection("schoolStats")
    private val gradeStatsCollection = db.collection("gradeStats")
    private val solveRecordsCollection = db.collection("solveRecords")

    /**
     * 사용자 프로필 생성 또는 업데이트
     */
    suspend fun createOrUpdateUser(
        uid: String,
        email: String,
        name: String,
        provider: String,
        photoUrl: String? = null
    ): Result<FirestoreUserProfile> {
        return try {
            val existingUser = getUserProfile(uid)
            
            val userData = if (existingUser != null) {
                // 기존 사용자 - 기본 정보만 업데이트
                mapOf(
                    "email" to email,
                    "name" to name,
                    "provider" to provider,
                    "photoUrl" to photoUrl,
                    "lastActiveAt" to Timestamp.now(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                // 신규 사용자 - 전체 프로필 생성
                mapOf(
                    "uid" to uid,
                    "email" to email,
                    "name" to name,
                    "provider" to provider,
                    "photoUrl" to photoUrl,
                    "school" to "",
                    "grade" to 1,
                    "totalScore" to 0,
                    "solvedProblems" to 0,
                    "easySolved" to 0,
                    "mediumSolved" to 0,
                    "hardSolved" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to Timestamp.now()
                )
            }
            
            usersCollection.document(uid).set(userData, SetOptions.merge()).await()
            val updatedProfile = getUserProfile(uid) ?: throw Exception("Failed to retrieve updated profile")
            Result.success(updatedProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자 프로필 조회
     */
    suspend fun getUserProfile(uid: String): FirestoreUserProfile? {
        return try {
            val doc = usersCollection.document(uid).get().await()
            doc.toObject<FirestoreUserProfile>()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 사용자 프로필 업데이트 (학교, 학년)
     */
    suspend fun updateUserProfile(
        uid: String,
        school: String,
        grade: Int
    ): Result<Unit> {
        return try {
            val oldProfile = getUserProfile(uid)
            
            usersCollection.document(uid).update(
                mapOf(
                    "school" to school,
                    "grade" to grade,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            
            // 학교/학년 통계 업데이트
            if (oldProfile != null) {
                if (oldProfile.school != school || oldProfile.grade != grade) {
                    updateSchoolAndGradeStats(
                        oldSchool = oldProfile.school,
                        newSchool = school,
                        oldGrade = oldProfile.grade,
                        newGrade = grade,
                        score = oldProfile.totalScore,
                        problems = oldProfile.solvedProblems
                    )
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 점수 추가 (문제 정답 시)
     */
    suspend fun addScore(
        uid: String,
        difficultyLevel: DifficultyLevel,
        subject: String,
        problemText: String,
        userAnswer: String,
        correctAnswer: String?,
        elapsedSeconds: Int,
        isCorrect: Boolean
    ): Result<Unit> {
        return try {
            // 프로필이 없으면 기본 프로필 생성
            var profile = getUserProfile(uid)
            if (profile == null) {
                // 기본 프로필 생성
                val defaultProfile = mapOf(
                    "uid" to uid,
                    "email" to "",
                    "name" to "User",
                    "provider" to "Unknown",
                    "school" to "",
                    "grade" to 1,
                    "totalScore" to 0,
                    "solvedProblems" to 0,
                    "easySolved" to 0,
                    "mediumSolved" to 0,
                    "hardSolved" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                usersCollection.document(uid).set(defaultProfile).await()
                profile = getUserProfile(uid) ?: throw Exception("Failed to create user profile")
            }
            
            // 트랜잭션으로 점수 업데이트
            db.runTransaction { transaction ->
                val userRef = usersCollection.document(uid)
                val userDoc = transaction.get(userRef)
                
                if (isCorrect) {
                    val currentScore = userDoc.getLong("totalScore") ?: 0
                    val currentProblems = userDoc.getLong("solvedProblems") ?: 0
                    
                    val fieldToUpdate = when (difficultyLevel) {
                        DifficultyLevel.EASY -> "easySolved"
                        DifficultyLevel.MEDIUM -> "mediumSolved"
                        DifficultyLevel.HARD -> "hardSolved"
                    }
                    val currentDifficultyCount = userDoc.getLong(fieldToUpdate) ?: 0
                    
                    transaction.update(userRef, mapOf(
                        "totalScore" to currentScore + difficultyLevel.points,
                        "solvedProblems" to currentProblems + 1,
                        fieldToUpdate to currentDifficultyCount + 1,
                        "lastActiveAt" to Timestamp.now(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }.await()
            
            // 풀이 기록 저장
            val record = FirestoreSolveRecord(
                userId = uid,
                userName = profile.name,
                school = profile.school,
                grade = profile.grade,
                subject = subject,
                difficulty = when (difficultyLevel) {
                    DifficultyLevel.EASY -> 2
                    DifficultyLevel.MEDIUM -> 5
                    DifficultyLevel.HARD -> 8
                },
                difficultyLevel = difficultyLevel.name,
                points = if (isCorrect) difficultyLevel.points else 0,
                isCorrect = isCorrect,
                problemText = problemText,
                userAnswer = userAnswer,
                correctAnswer = correctAnswer,
                elapsedSeconds = elapsedSeconds
            )
            
            solveRecordsCollection.add(record).await()
            
            // 학교/학년 통계 업데이트
            if (isCorrect && profile.school.isNotEmpty()) {
                updateStatsForScore(
                    school = profile.school,
                    grade = profile.grade,
                    points = difficultyLevel.points
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 실시간 리더보드 데이터 가져오기
     */
    fun getLeaderboardFlow(): Flow<LeaderboardData> = callbackFlow {
        // 전체 사용자 상위 10명
        val topUsersListener = usersCollection
            .orderBy("totalScore", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                
                val users = snapshot?.documents?.mapIndexed { index, doc ->
                    val user = doc.toObject<FirestoreUserProfile>() ?: return@mapIndexed null
                    LeaderboardEntry(
                        rank = index + 1,
                        userId = user.uid,
                        userName = user.name,
                        school = user.school.ifEmpty { null },
                        grade = if (user.grade in 1..12) user.grade else null,
                        totalScore = user.totalScore,
                        solvedProblems = user.solvedProblems
                    )
                }?.filterNotNull() ?: emptyList()
                
                trySend(LeaderboardData(topStudents = users))
            }
        
        awaitClose { topUsersListener.remove() }
    }

    /**
     * 전체 리더보드 데이터 가져오기 (한 번)
     */
    suspend fun getLeaderboardData(currentUserId: String?): LeaderboardData {
        return try {
            // 상위 10명 학생
            val topStudents = usersCollection
                .orderBy("totalScore", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()
                .documents
                .mapIndexed { index, doc ->
                    val user = doc.toObject<FirestoreUserProfile>() ?: return@mapIndexed null
                    LeaderboardEntry(
                        rank = index + 1,
                        userId = user.uid,
                        userName = user.name,
                        school = user.school.ifEmpty { null },
                        grade = if (user.grade in 1..12) user.grade else null,
                        totalScore = user.totalScore,
                        solvedProblems = user.solvedProblems
                    )
                }
                .filterNotNull()
            
            // 학교별 통계
            val schoolStats = schoolStatsCollection
                .orderBy("totalScore", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapIndexed { index, doc ->
                    val stats = doc.toObject<FirestoreSchoolStats>() ?: return@mapIndexed null
                    SchoolStats(
                        school = stats.schoolName,
                        totalStudents = stats.totalStudents,
                        totalScore = stats.totalScore.toInt(),
                        averageScore = stats.averageScore,
                        rank = index + 1
                    )
                }
                .filterNotNull()
            
            // 학년별 통계
            val gradeStats = gradeStatsCollection
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val stats = doc.toObject<FirestoreGradeStats>() ?: return@mapNotNull null
                    GradeStats(
                        grade = stats.grade,
                        totalStudents = stats.totalStudents,
                        totalScore = stats.totalScore.toInt(),
                        averageScore = stats.averageScore,
                        rank = 0  // 정렬 후 재할당
                    )
                }
                .sortedByDescending { it.averageScore }
                .mapIndexed { index, stats ->
                    stats.copy(rank = index + 1)
                }
            
            // 현재 사용자 순위
            val userRank = currentUserId?.let { uid ->
                val userProfile = getUserProfile(uid)
                userProfile?.let { profile ->
                    val rank = getUserRank(uid)
                    LeaderboardEntry(
                        rank = rank,
                        userId = uid,
                        userName = profile.name,
                        school = profile.school.ifEmpty { null },
                        grade = if (profile.grade in 1..12) profile.grade else null,
                        totalScore = profile.totalScore,
                        solvedProblems = profile.solvedProblems
                    )
                }
            }
            
            LeaderboardData(
                topStudents = topStudents,
                schoolRankings = schoolStats,
                gradeRankings = gradeStats,
                userRank = userRank
            )
        } catch (e: Exception) {
            LeaderboardData()
        }
    }

    /**
     * 사용자의 전체 순위 계산
     */
    private suspend fun getUserRank(uid: String): Int {
        return try {
            val userProfile = getUserProfile(uid) ?: return 0
            val higherScoreCount = usersCollection
                .whereGreaterThan("totalScore", userProfile.totalScore)
                .get()
                .await()
                .size()
            higherScoreCount + 1
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 학교/학년 통계 업데이트 (점수 추가 시)
     */
    private suspend fun updateStatsForScore(school: String, grade: Int, points: Int) {
        if (school.isEmpty()) return
        
        try {
            // 학교 통계 업데이트
            val schoolExists = withContext(Dispatchers.IO) {
                schoolStatsCollection.document(school).get().await().exists()
            }
            
            if (!schoolExists) {
                // 학교 통계가 없으면 먼저 생성
                createSchoolStats(school)
            }
            
            // 통계 업데이트
            db.runTransaction { transaction ->
                val schoolRef = schoolStatsCollection.document(school)
                val schoolDoc = transaction.get(schoolRef)
                
                if (schoolDoc.exists()) {
                    val currentScore = schoolDoc.getLong("totalScore") ?: 0
                    val currentProblems = schoolDoc.getLong("totalProblems") ?: 0
                    transaction.update(schoolRef, mapOf(
                        "totalScore" to currentScore + points,
                        "totalProblems" to currentProblems + 1,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    ))
                }
            }.await()
            
            // 학년 통계 업데이트
            val gradeId = "grade_$grade"
            val gradeExists = withContext(Dispatchers.IO) {
                gradeStatsCollection.document(gradeId).get().await().exists()
            }
            
            if (!gradeExists) {
                // 학년 통계가 없으면 먼저 생성
                createGradeStats(grade)
            }
            
            // 통계 업데이트
            db.runTransaction { transaction ->
                val gradeRef = gradeStatsCollection.document(gradeId)
                val gradeDoc = transaction.get(gradeRef)
                
                if (gradeDoc.exists()) {
                    val currentScore = gradeDoc.getLong("totalScore") ?: 0
                    val currentProblems = gradeDoc.getLong("totalProblems") ?: 0
                    transaction.update(gradeRef, mapOf(
                        "totalScore" to currentScore + points,
                        "totalProblems" to currentProblems + 1,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    ))
                }
            }.await()
        } catch (e: Exception) {
            // 통계 업데이트 실패는 무시 (핵심 기능 아님)
        }
    }

    /**
     * 학교 통계 생성/재계산
     */
    private suspend fun createSchoolStats(schoolName: String) {
        try {
            val users = usersCollection
                .whereEqualTo("school", schoolName)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject<FirestoreUserProfile>() }
            
            val stats = FirestoreSchoolStats(
                schoolName = schoolName,
                totalStudents = users.size,
                totalScore = users.sumOf { it.totalScore.toLong() },
                totalProblems = users.sumOf { it.solvedProblems.toLong() }
            )
            
            schoolStatsCollection.document(schoolName).set(stats).await()
        } catch (e: Exception) {
            // 실패 무시
        }
    }

    /**
     * 학년 통계 생성/재계산
     */
    private suspend fun createGradeStats(grade: Int) {
        try {
            val users = usersCollection
                .whereEqualTo("grade", grade)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject<FirestoreUserProfile>() }
            
            val stats = FirestoreGradeStats(
                grade = grade,
                totalStudents = users.size,
                totalScore = users.sumOf { it.totalScore.toLong() },
                totalProblems = users.sumOf { it.solvedProblems.toLong() }
            )
            
            val gradeId = "grade_$grade"
            gradeStatsCollection.document(gradeId).set(stats).await()
        } catch (e: Exception) {
            // 실패 무시
        }
    }

    /**
     * 학교/학년 변경 시 통계 업데이트
     */
    private suspend fun updateSchoolAndGradeStats(
        oldSchool: String,
        newSchool: String,
        oldGrade: Int,
        newGrade: Int,
        score: Int,
        problems: Int
    ) {
        // 구현 생략 (복잡한 로직이므로 필요시 추가)
        // 기존 학교/학년에서 제거, 새 학교/학년에 추가
    }
}