// app/src/main/java/com/example/ailearningapp/ui/screens/LeaderboardScreen.kt
package com.example.ailearningapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ailearningapp.data.model.*
import com.example.ailearningapp.data.repository.LeaderboardRepository
import com.example.ailearningapp.data.repository.UserProfileRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val leaderboardRepo = remember { LeaderboardRepository() }
    val profileRepo = remember { UserProfileRepository(context) }
    val scope = rememberCoroutineScope()
    
    var leaderboardData by remember { mutableStateOf<LeaderboardData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var userProfile by remember { mutableStateOf<UserProfileData?>(null) }
    
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabTitles = listOf("전체 순위", "학교별 순위", "학년별 순위")
    
    // 데이터 로드
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            userProfile = profileRepo.getUserData()
            leaderboardData = leaderboardRepo.getLeaderboardData(
                currentUserId = "current_user", // 실제 구현시 현재 사용자 ID 사용
                currentUserSchool = userProfile?.school,
                currentUserGrade = userProfile?.grade
            )
        } catch (e: Exception) {
            // 에러 처리
        } finally {
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("리더보드") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 탭
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(title) }
                    )
                }
            }
            
            // 내용
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> TopStudentsTab(
                            leaderboardData?.topStudents ?: emptyList(),
                            leaderboardData?.userRank
                        )
                        1 -> SchoolRankingTab(
                            leaderboardData?.schoolRankings ?: emptyList(),
                            userProfile?.school
                        )
                        2 -> GradeRankingTab(
                            leaderboardData?.gradeRankings ?: emptyList(),
                            userProfile?.grade
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStudentsTab(
    topStudents: List<LeaderboardEntry>,
    userRank: LeaderboardEntry?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 현재 사용자 순위
        userRank?.let { user ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    StudentRankItem(user, isCurrentUser = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "전체 순위",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        
        // Top 10 학생들
        items(topStudents) { student ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                StudentRankItem(student)
            }
        }
    }
}

@Composable
private fun StudentRankItem(
    entry: LeaderboardEntry,
    isCurrentUser: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 순위 배지
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when (entry.rank) {
                        1 -> Color(0xFFFFD700) // 금색
                        2 -> Color(0xFFC0C0C0) // 은색
                        3 -> Color(0xFFCD7F32) // 동색
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.rank.toString(),
                fontWeight = FontWeight.Bold,
                color = if (entry.rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 사용자 정보
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrentUser) "나 (${entry.userName})" else entry.userName,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (entry.rank <= 3) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFD700)
                    )
                }
            }
            Text(
                text = "${entry.school ?: "학교 미등록"} | ${entry.grade ?: "?"}학년",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 점수 정보
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${entry.totalScore}점",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${entry.solvedProblems}문제",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SchoolRankingTab(
    schoolRankings: List<SchoolStats>,
    userSchool: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(schoolRankings) { school ->
            val isUserSchool = school.school == userSchool
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (isUserSchool) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else CardDefaults.cardColors()
            ) {
                SchoolRankItem(school, isUserSchool)
            }
        }
    }
}

@Composable
private fun SchoolRankItem(
    school: SchoolStats,
    isUserSchool: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 순위
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isUserSchool) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = school.rank.toString(),
                fontWeight = FontWeight.Bold,
                color = if (isUserSchool) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 학교 정보
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isUserSchool) "${school.school} (우리 학교)" else school.school,
                fontWeight = if (isUserSchool) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = "학생 ${school.totalStudents}명",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 점수 정보
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "평균 ${String.format("%.1f", school.averageScore)}점",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "총 ${school.totalScore}점",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GradeRankingTab(
    gradeRankings: List<GradeStats>,
    userGrade: Int?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(gradeRankings) { grade ->
            val isUserGrade = grade.grade == userGrade
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (isUserGrade) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else CardDefaults.cardColors()
            ) {
                GradeRankItem(grade, isUserGrade)
            }
        }
    }
}

@Composable
private fun GradeRankItem(
    grade: GradeStats,
    isUserGrade: Boolean
) {
    val gradeName = when (grade.grade) {
        in 1..6 -> "초등 ${grade.grade}학년"
        in 7..9 -> "중학 ${grade.grade - 6}학년"
        in 10..12 -> "고등 ${grade.grade - 9}학년"
        else -> "${grade.grade}학년"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 순위
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isUserGrade) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = grade.rank.toString(),
                fontWeight = FontWeight.Bold,
                color = if (isUserGrade) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 학년 정보
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isUserGrade) "$gradeName (내 학년)" else gradeName,
                fontWeight = if (isUserGrade) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = "학생 ${grade.totalStudents}명",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 점수 정보
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "평균 ${String.format("%.1f", grade.averageScore)}점",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "총 ${grade.totalScore}점",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}