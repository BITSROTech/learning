// app/src/main/java/com/example/ailearningapp/ui/theme/Theme.kt
package com.example.ailearningapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ---- App 색상 정의 (블루 계열) ----
private val BluePrimary      = Color(0xFF1E88E5) // 버튼 색(Primary)
private val BluePrimaryCtr   = Color.White
private val BlueSecondary    = Color(0xFF1565C0)
private val BlueSecondaryCtr = Color.White
private val BlueTertiary     = Color(0xFF03A9F4)
private val BlueTertiaryCtr  = Color.White

// 다크 모드에서도 파란 버튼 유지 (배경은 다크)
private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = BluePrimaryCtr,
    secondary = BlueSecondary,
    onSecondary = BlueSecondaryCtr,
    tertiary = BlueTertiary,
    onTertiary = BlueTertiaryCtr,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
)

// 라이트 테마: 배경/서피스 = 흰색, 버튼 = 파란색
private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BluePrimaryCtr,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),

    secondary = BlueSecondary,
    onSecondary = BlueSecondaryCtr,

    tertiary = BlueTertiary,
    onTertiary = BlueTertiaryCtr,

    background = Color.White,
    onBackground = Color(0xFF111111),

    surface = Color.White,
    onSurface = Color(0xFF111111),

    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF424242)
)

@Composable
fun AilearningappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 동적 색상 끄기(시스템 팔레트가 보라색으로 바꾸는 것 방지)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
