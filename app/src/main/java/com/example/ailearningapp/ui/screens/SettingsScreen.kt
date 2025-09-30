// app/src/main/java/com/example/ailearningapp/ui/screens/SettingsScreen.kt
package com.example.ailearningapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ailearningapp.BuildConfig
import com.example.ailearningapp.data.model.AuthUser
import com.example.ailearningapp.data.model.DifficultyLevel
import com.example.ailearningapp.data.repository.AuthRepository
import com.example.ailearningapp.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenProfile: () -> Unit = {},  // 프로필 설정 화면 열기
    onOpenLeaderboard: () -> Unit = {}  // 리더보드 화면 열기
) {
    val ctx = LocalContext.current
    val repo = remember(ctx) { AuthRepository(ctx) }
    val profileRepo = remember(ctx) { UserProfileRepository(ctx) }
    val scope = rememberCoroutineScope()

    val user: AuthUser? by repo.currentUser().collectAsState(initial = null)
    var loading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 점수 정보
    var totalScore by remember { mutableStateOf(0) }
    var solvedProblems by remember { mutableStateOf(0) }
    var difficultyStats by remember { mutableStateOf<Map<DifficultyLevel, Int>>(emptyMap()) }
    
    // 프로필 정보
    var school by remember { mutableStateOf<String?>(null) }
    var grade by remember { mutableStateOf<Int?>(null) }
    
    // 데이터 로드
    LaunchedEffect(Unit) {
        profileRepo.scoreFlow().collectLatest { (score, solved, stats) ->
            totalScore = score
            solvedProblems = solved
            difficultyStats = stats
        }
    }
    
    LaunchedEffect(Unit) {
        profileRepo.profileFlow().collectLatest { (s, g) ->
            school = s
            grade = g
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    TextButton(onClick = onClose, enabled = !loading) { Text("닫기") }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 계정 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("계정", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenProfile) {
                    Text("프로필 수정")
                }
            }
            
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("플랫폼: ${user?.provider ?: "-"}")
                    Text("이름: ${user?.name ?: "-"}")
                    Text("이메일: ${user?.email ?: "-"}")
                    Text("학교: ${school ?: "미등록"}")
                    Text("학년: ${grade?.let { "${it}학년" } ?: "미등록"}")
                }
            }
            
            // 점수 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("나의 학습 현황", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenLeaderboard) {
                    Text("리더보드 보기")
                }
            }
            
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "총 점수",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "${totalScore}점",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("해결한 문제")
                        Text("${solvedProblems}문제")
                    }
                    
                    if (difficultyStats.isNotEmpty()) {
                        HorizontalDivider()
                        Text("난이도별 해결 현황", style = MaterialTheme.typography.labelMedium)
                        difficultyStats.forEach { (level, count) ->
                            if (count > 0) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${level.displayName} (+${level.points}점)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "${count}문제",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            // 보안/로그아웃
            Text("보안", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = {
                    if (loading || user == null) return@Button
                    loading = true
                    scope.launch {
                        val result = runCatching { repo.signOutAll() }
                        loading = false
                        result.onSuccess {
                            // 네비게이션 먼저 (스택 정리는 루트 가드/콜백에서 처리)
                            onLoggedOut()
                            // 스낵바는 비동기로
                            launch { snackbarHostState.showSnackbar("로그아웃되었습니다.") }
                        }.onFailure { e ->
                            launch {
                                snackbarHostState.showSnackbar(
                                    "로그아웃 실패: ${e.message ?: "알 수 없는 오류"}"
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && user != null
            ) { Text(if (loading) "로그아웃 중..." else "로그아웃") }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Spacer(Modifier.weight(1f))

            // 앱 버전
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        thickness = DividerDefaults.Thickness,
        color = DividerDefaults.color
    )
}