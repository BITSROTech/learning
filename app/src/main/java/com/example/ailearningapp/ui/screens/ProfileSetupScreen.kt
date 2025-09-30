// app/src/main/java/com/example/ailearningapp/ui/screens/ProfileSetupScreen.kt
package com.example.ailearningapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ailearningapp.data.repository.UserProfileRepository
import com.example.ailearningapp.viewmodel.UserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onComplete: () -> Unit,
    isInitialSetup: Boolean = true  // 초기 설정인지, 수정인지 구분
) {
    val context = LocalContext.current
    val profileRepo = remember { UserProfileRepository(context) }
    val userVm: UserViewModel = viewModel()
    val scope = rememberCoroutineScope()
    
    var school by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 기존 프로필 데이터 로드 (수정 모드일 때)
    LaunchedEffect(Unit) {
        if (!isInitialSetup) {
            val userData = profileRepo.getUserData()
            school = userData.school ?: ""
            grade = userData.grade?.toString() ?: ""
        }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(if (isInitialSetup) "프로필 설정" else "프로필 수정")
                },
                navigationIcon = if (!isInitialSetup) {
                    {
                        TextButton(onClick = onComplete) {
                            Text("취소")
                        }
                    }
                } else null
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = if (isInitialSetup) {
                    "학습을 시작하기 전에\n학교와 학년을 입력해주세요"
                } else {
                    "프로필 정보를 수정합니다"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            // 학교 입력
            OutlinedTextField(
                value = school,
                onValueChange = { 
                    school = it
                    errorMessage = null
                },
                label = { Text("학교명") },
                placeholder = { Text("예: 서울초등학교") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && school.isBlank()
            )
            
            // 학년 입력
            OutlinedTextField(
                value = grade,
                onValueChange = { input ->
                    // 숫자만 입력 가능하도록
                    if (input.all { it.isDigit() }) {
                        val gradeInt = input.toIntOrNull()
                        if (gradeInt == null || gradeInt in 1..12) {
                            grade = input
                            errorMessage = null
                        }
                    }
                },
                label = { Text("학년") },
                placeholder = { Text("1-12 사이의 숫자") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && (grade.isBlank() || grade.toIntOrNull() !in 1..12),
                supportingText = {
                    Text("초등학교 1-6, 중학교 7-9, 고등학교 10-12")
                }
            )
            
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 저장 버튼
            Button(
                onClick = {
                    val gradeInt = grade.toIntOrNull()
                    
                    when {
                        school.isBlank() -> {
                            errorMessage = "학교명을 입력해주세요"
                        }
                        gradeInt == null || gradeInt !in 1..12 -> {
                            errorMessage = "올바른 학년을 입력해주세요 (1-12)"
                        }
                        else -> {
                            isLoading = true
                            scope.launch {
                                try {
                                    profileRepo.saveProfile(school, gradeInt)
                                    // UserViewModel에도 업데이트 (필요한 경우)
                                    isLoading = false
                                    onComplete()
                                } catch (e: Exception) {
                                    isLoading = false
                                    errorMessage = "저장 중 오류가 발생했습니다: ${e.message}"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isInitialSetup) "시작하기" else "저장")
                }
            }
        }
    }
}