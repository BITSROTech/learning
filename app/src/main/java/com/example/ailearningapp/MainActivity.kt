// app/src/main/java/com/example/ailearningapp/MainActivity.kt
package com.example.ailearningapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ailearningapp.navigation.Routes
import com.example.ailearningapp.navigation.Routes.optGradeQuery
import com.example.ailearningapp.navigation.Routes.optSubjectQuery
import com.example.ailearningapp.navigation.Routes.requireSubjectArg
import com.example.ailearningapp.navigation.Subject
import com.example.ailearningapp.ui.screens.FeedbackScreen
import com.example.ailearningapp.ui.screens.GradeSelectScreen
import com.example.ailearningapp.ui.screens.HistoryScreen
import com.example.ailearningapp.ui.screens.LoginScreen
import com.example.ailearningapp.ui.screens.ProblemSolveScreen
import com.example.ailearningapp.ui.screens.SettingsScreen
import com.example.ailearningapp.ui.screens.SubjectScreen
import com.example.ailearningapp.ui.screens.LeaderboardScreen
import com.example.ailearningapp.ui.screens.ProfileSetupScreen
import com.example.ailearningapp.ui.theme.AilearningappTheme
import com.example.ailearningapp.viewmodel.SolveViewModel
import com.example.ailearningapp.viewmodel.UserViewModel
import com.example.ailearningapp.data.repository.UserProfileRepository
import android.content.Context

private const val SOLVE_FLOW = "solve_flow" // 중첩 그래프(문제 풀기 ↔ 피드백) route

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // ✅ 앱 전역 테마 적용(배경 흰색, 버튼 파랑은 Theme.kt에서 설정됨)
            AilearningappTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current

    // 🔒 루트에서 인증 상태 감시
    val userVm: UserViewModel = viewModel()
    val ui by userVm.uiState.collectAsState()
    
    // 프로필 상태 감시
    val profileRepo = remember { UserProfileRepository(context) }
    val profileData by profileRepo.profileFlow().collectAsState(initial = null to null)

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(ui.user, currentRoute) {
        // 미인증 상태면 → 로그인으로 (스택 비우기)
        if (ui.user == null && currentRoute != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) {
                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
        // 인증 상태면 로그인 화면에 머물지 않도록
        else if (ui.user != null && currentRoute == Routes.LOGIN) {
            // 프로필이 없으면 프로필 설정으로
            val (school, grade) = profileData
            if (school == null || grade == null) {
                nav.navigate(Routes.PROFILE_SETUP) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                // 프로필이 있으면 홈(학년/수준)으로
                nav.navigate(Routes.GRADE) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {

            NavHost(
                navController = nav,
                startDestination = Routes.LOGIN,
                modifier = Modifier.fillMaxSize()
            ) {
                // ───────── Auth ─────────
                composable(Routes.LOGIN) {
                    // 내비게이션은 루트 가드가 담당
                    LoginScreen(onAuthed = { /* handled by root auth guard */ })
                }

                // ───────── 메인 플로우 ─────────
                composable(Routes.GRADE) {
                    GradeSelectScreen(onNext = { nav.navigate(Routes.SUBJECT) })
                }

                composable(Routes.SUBJECT) {
                    SubjectScreen(onPick = { subjectStr ->
                        // "math"/"english" → Enum(MATH/ENGLISH)
                        val s = runCatching { Subject.valueOf(subjectStr.uppercase()) }
                            .getOrDefault(Subject.MATH)
                        // solve/{subject} 로 이동 (solve_flow 하위 대상)
                        nav.navigate(Routes.solve(s)) {
                            launchSingleTop = true // 동일 경로 중복 방지
                        }
                    })
                }

                // ───────── 중첩 그래프: 문제 풀기 ↔ 피드백 (SolveViewModel 공유) ─────────
                navigation(
                    route = SOLVE_FLOW,
                    startDestination = Routes.SOLVE
                ) {
                    composable(
                        route = Routes.SOLVE,
                        arguments = listOf(
                            navArgument(Routes.ARG_SUBJECT) { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        // 부모(그래프) BackStackEntry를 통해 VM 공유
                        val parentEntry = remember(backStackEntry) {
                            nav.getBackStackEntry(SOLVE_FLOW)
                        }
                        val vm: SolveViewModel = viewModel(viewModelStoreOwner = parentEntry)

                        // 라우트 인자: 대문자 Enum → 서버용 소문자 문자열
                        val subjectLower = backStackEntry.requireSubjectArg().name.lowercase()

                        ProblemSolveScreen(
                            subject = subjectLower,
                            onShowFeedback = { nav.navigate(Routes.FEEDBACK) },
                            onBack = { nav.popBackStack() },
                            vm = vm,
                            // 🔵 드래그 가능한 FAB 클릭 시: 설정 화면으로 이동
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.FEEDBACK) { backStackEntry ->
                        val parentEntry = remember(backStackEntry) {
                            nav.getBackStackEntry(SOLVE_FLOW)
                        }
                        val vm: SolveViewModel = viewModel(viewModelStoreOwner = parentEntry)

                        // ✅ FeedbackScreen은 vm.currentSubject를 사용하므로 subject 파라미터 불필요
                        FeedbackScreen(
                            onNext = {
                                // FEEDBACK에서 한 단계만 pop → SOLVE로 복귀
                                nav.popBackStack()
                            },
                            vm = vm
                        )
                    }
                }

                // ───────── 기록 / 설정 ─────────
                composable(
                    route = Routes.HISTORY_PATTERN,
                    arguments = listOf(
                        navArgument(Routes.Q_SUBJECT) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                        navArgument(Routes.Q_GRADE) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val initSubject = backStackEntry.optSubjectQuery()
                    val initGrade = backStackEntry.optGradeQuery()
                    HistoryScreen(
                        initialSubject = initSubject,
                        initialGrade = initGrade,
                        onBack = { nav.popBackStack() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onClose = { nav.popBackStack() },
                        onLoggedOut = {
                            // 버튼에서도 즉시 로그인으로 (스택 비우기)
                            nav.navigate(Routes.LOGIN) {
                                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenProfile = {
                            nav.navigate(Routes.PROFILE_SETUP)
                        },
                        onOpenLeaderboard = {
                            nav.navigate(Routes.LEADERBOARD)
                        }
                    )
                }
                
                // 리더보드 화면
                composable(Routes.LEADERBOARD) {
                    LeaderboardScreen(
                        onBack = { nav.popBackStack() }
                    )
                }
                
                // 프로필 설정 화면
                composable(Routes.PROFILE_SETUP) {
                    // 최초 설정인지 확인
                    val (school, grade) = profileData
                    val isInitial = school == null || grade == null
                    
                    ProfileSetupScreen(
                        onComplete = { 
                            if (isInitial) {
                                // 최초 설정 후 홈으로
                                nav.navigate(Routes.GRADE) {
                                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                // 수정 후 뒤로가기
                                nav.popBackStack() 
                            }
                        },
                        isInitialSetup = isInitial
                    )
                }
            }

            // ✅ 전역 설정 FAB는 제거했습니다 (중복 아이콘 방지).
        }
    }
}
