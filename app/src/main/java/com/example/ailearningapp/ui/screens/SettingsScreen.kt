// app/src/main/java/com/example/ailearningapp/ui/screens/SettingsScreen.kt
package com.example.ailearningapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ailearningapp.BuildConfig
import com.example.ailearningapp.data.model.AuthUser
import com.example.ailearningapp.data.repository.AuthRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember(ctx) { AuthRepository(ctx) }
    val scope = rememberCoroutineScope()

    val user: AuthUser? by repo.currentUser().collectAsState(initial = null)
    var loading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
            Text("계정", style = MaterialTheme.typography.titleMedium)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("플랫폼: ${user?.provider ?: "-"}")
                    Text("이름: ${user?.name ?: "-"}")
                    Text("이메일: ${user?.email ?: "-"}")
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
