// app/src/main/java/com/example/ailearningapp/ui/screens/LoginScreen.kt
package com.example.ailearningapp.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ailearningapp.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onAuthed: () -> Unit) {
    val vm: UserViewModel = viewModel()
    val ui by vm.uiState.collectAsState()

    val ctx = LocalContext.current
    val activity = ctx as? Activity

    // Google 로그인 결과 수신
    val googleLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { res ->
        val data: Intent = res.data ?: return@rememberLauncherForActivityResult
        vm.handleGoogleResult(
            data = data,
            onSuccess = onAuthed,
            onError = { /* 에러 메시지는 ui.error로 표시됨 */ }
        )
    }

    // 이미 로그인되어 있으면 한 번만 자동 진입
    var navigated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.user) {
        if (!navigated && ui.user != null) {
            navigated = true
            onAuthed()
        }
    }

    // UI 색/상수
    val kakaoYellow = Color(0xFFFEE500)
    val nearBlack = Color(0xFF191919)

    Scaffold { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "AI 학습 앱",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "계정 하나로 간편하게 시작해 보세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoogleSignInButton(
                            text = "Google로 시작하기",
                            onClick = {
                                if (ui.loading) return@GoogleSignInButton
                                if (activity == null) return@GoogleSignInButton
                                googleLauncher.launch(vm.googleSignInIntent())
                            }
                        )
                        OrDivider()
                        KakaoSignInButton(
                            text = "카카오로 시작하기",
                            onClick = {
                                if (ui.loading) return@KakaoSignInButton
                                if (activity == null) return@KakaoSignInButton
                                vm.kakaoLogin(
                                    activity = activity,
                                    onSuccess = onAuthed,
                                    onError = { /* 에러 메시지는 ui.error로 표시됨 */ }
                                )
                            },
                            containerColor = kakaoYellow,
                            contentColor = nearBlack
                        )

                        AnimatedVisibility(visible = ui.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .align(Alignment.CenterHorizontally)
                            )
                        }

                        ui.error?.let { msg ->
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                Text(
                    "계속하면 서비스 이용약관 및 개인정보처리방침에 동의하게 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/* ───────── Styled Buttons ───────── */

@Composable
private fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
) {
    val nearBlack = Color(0xFF191919)
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = nearBlack
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonogramCircle(label = "G", bg = Color.White, fg = nearBlack, withBorder = true)
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(24.dp))
        }
    }
}

@Composable
private fun KakaoSignInButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonogramCircle(label = "K", bg = Color(0xFF381E1F).copy(alpha = 0.08f), fg = contentColor)
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(24.dp))
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
        Text(
            "또는",
            modifier = Modifier.padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
    }
}

@Composable
private fun MonogramCircle(
    label: String,
    bg: Color,
    fg: Color,
    withBorder: Boolean = false
) {
    Surface(
        shape = CircleShape,
        color = bg,
        border = if (withBorder) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
