// app/src/main/java/com/example/ailearningapp/data/repository/LeaderboardRepository.kt
package com.example.ailearningapp.data.repository

import com.example.ailearningapp.data.model.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 리더보드 데이터를 관리하는 Repository
 * 실제 구현에서는 서버 API를 호출하겠지만, 현재는 더미 데이터를 생성합니다.
 */
class LeaderboardRepository {
    
    /**
     * 전체 리더보드 데이터 가져오기
     */
    suspend fun getLeaderboardData(
        currentUserId: String?,
        currentUserSchool: String?,
        currentUserGrade: Int?
    ): LeaderboardData {
        // API 호출 시뮬레이션
        delay(500)
        
        // 더미 데이터 생성
        val topStudents = generateTopStudents()
        val schoolRankings = generateSchoolRankings(currentUserSchool)
        val gradeRankings = generateGradeRankings()
        
        // 현재 사용자 순위 (더미)
        val userRank = currentUserId?.let { userId ->
            LeaderboardEntry(
                rank = Random.nextInt(10, 100),
                userId = userId,
                userName = "나",
                school = currentUserSchool,
                grade = currentUserGrade,
                totalScore = Random.nextInt(50, 500),
                solvedProblems = Random.nextInt(10, 100)
            )
        }
        
        return LeaderboardData(
            topStudents = topStudents,
            schoolRankings = schoolRankings,
            gradeRankings = gradeRankings,
            userRank = userRank
        )
    }
    
    /**
     * 학교별 랭킹 가져오기
     */
    suspend fun getSchoolRankings(): List<SchoolStats> {
        delay(300)
        return generateSchoolRankings(null)
    }
    
    /**
     * 학년별 랭킹 가져오기
     */
    suspend fun getGradeRankings(): List<GradeStats> {
        delay(300)
        return generateGradeRankings()
    }
    
    // 더미 데이터 생성 함수들
    
    private fun generateTopStudents(): List<LeaderboardEntry> {
        val schools = listOf("서울초등학교", "부산중학교", "대전고등학교", "광주초등학교", 
                           "인천중학교", "대구고등학교", "울산초등학교", "세종중학교")
        val names = listOf("김민준", "이서윤", "박지호", "최서연", "정예준", 
                         "강지우", "조민서", "윤서현", "장하윤", "임시우")
        
        return names.mapIndexed { index, name ->
            LeaderboardEntry(
                rank = index + 1,
                userId = "user_${index + 1}",
                userName = name,
                school = schools.random(),
                grade = Random.nextInt(1, 13),
                totalScore = 1000 - (index * 50) + Random.nextInt(-20, 21),
                solvedProblems = 200 - (index * 10) + Random.nextInt(-5, 6)
            )
        }
    }
    
    private fun generateSchoolRankings(currentUserSchool: String?): List<SchoolStats> {
        val schools = mutableListOf(
            "서울초등학교" to Triple(125, 15000, 120.0),
            "부산중학교" to Triple(98, 11500, 117.3),
            "대전고등학교" to Triple(87, 10200, 117.2),
            "광주초등학교" to Triple(110, 12800, 116.4),
            "인천중학교" to Triple(92, 10500, 114.1),
            "대구고등학교" to Triple(75, 8400, 112.0),
            "울산초등학교" to Triple(68, 7500, 110.3),
            "세종중학교" to Triple(55, 6000, 109.1)
        )
        
        // 현재 사용자 학교가 목록에 없으면 추가
        if (currentUserSchool != null && schools.none { it.first == currentUserSchool }) {
            schools.add(currentUserSchool to Triple(
                Random.nextInt(30, 100),
                Random.nextInt(3000, 10000),
                Random.nextDouble(100.0, 120.0)
            ))
        }
        
        return schools
            .sortedByDescending { it.second.third }  // 평균 점수로 정렬
            .mapIndexed { index, (school, data) ->
                SchoolStats(
                    school = school,
                    totalStudents = data.first,
                    totalScore = data.second,
                    averageScore = data.third,
                    rank = index + 1
                )
            }
    }
    
    private fun generateGradeRankings(): List<GradeStats> {
        val gradeNames = mapOf(
            1 to "초등 1학년", 2 to "초등 2학년", 3 to "초등 3학년",
            4 to "초등 4학년", 5 to "초등 5학년", 6 to "초등 6학년",
            7 to "중학 1학년", 8 to "중학 2학년", 9 to "중학 3학년",
            10 to "고등 1학년", 11 to "고등 2학년", 12 to "고등 3학년"
        )
        
        return (1..12).map { grade ->
            val students = Random.nextInt(50, 150)
            val totalScore = students * Random.nextInt(80, 120)
            GradeStats(
                grade = grade,
                totalStudents = students,
                totalScore = totalScore,
                averageScore = totalScore.toDouble() / students,
                rank = 0  // 나중에 정렬 후 재할당
            )
        }.sortedByDescending { it.averageScore }
         .mapIndexed { index, stats ->
            stats.copy(rank = index + 1)
        }
    }
}