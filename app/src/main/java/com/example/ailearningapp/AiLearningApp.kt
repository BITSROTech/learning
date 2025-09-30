// app/src/main/java/com/example/ailearningapp/AiLearningApp.kt
package com.example.ailearningapp

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class AiLearningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCtx.init(this)

        // Kakao SDK 초기화
        val kakaoKey = getString(R.string.kakao_app_key)
        KakaoSdk.init(this, kakaoKey)
    }
}

/** 애플리케이션 컨텍스트 싱글톤 */
object AppCtx {
    lateinit var app: Application; private set
    fun init(app: Application) { this.app = app }
}
