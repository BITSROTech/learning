// app/src/main/java/com/example/ailearningapp/data/remote/ApiClient.kt
package com.example.ailearningapp.data.remote

import com.example.ailearningapp.AppCtx
import com.example.ailearningapp.BuildConfig
import com.example.ailearningapp.R
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.GzipSink
import okio.buffer
import retrofit2.Response as RetrofitResponse
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

/* ---------- API ---------- */
interface GatewayApi {
    @POST("generate")
    suspend fun generate(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): RetrofitResponse<GenerateResponse>

    @Multipart
    @POST("grade")
    suspend fun grade(
        @Part processImage: MultipartBody.Part?,
        @Part("payload") payload: RequestBody // JSON string in multipart form
    ): RetrofitResponse<GradeResponse>
}

/* ---------- Client ---------- */
private fun String.ensureTrailingSlash() =
    if (endsWith("/")) this else "$this/"

object ApiClient {

    // === 튜닝 스위치 ===
    private const val ENABLE_GZIP_REQUEST = true   // 서버가 Content-Encoding:gzip 지원되므로 /generate에만 적용
    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val READ_TIMEOUT_SEC = 60L
    private const val WRITE_TIMEOUT_SEC = 60L
    private const val CALL_TIMEOUT_SEC = 90L

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val req: Request = original.newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "AiLearningApp/${BuildConfig.VERSION_NAME} (okhttp)")
            // Accept-Encoding: gzip은 OkHttp가 자동 처리 (응답). br은 아래 옵션 네트워크 인터셉터.
            .build()
        chain.proceed(req)
    }

    /** 디버그일 때만 BODY 로그 */
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * 요청 바디 GZIP 압축
     * - 대상: /generate (JSON 바디)
     * - 제외: multipart, 작은 바디(≤1KB), 이미 인코딩된 요청, 비-POST/PUT/PATCH
     */
    private val gzipRequestInterceptor = Interceptor { chain ->
        val req = chain.request()
        val method = req.method
        if (method != "POST" && method != "PUT" && method != "PATCH") {
            return@Interceptor chain.proceed(req)
        }

        val body = req.body ?: return@Interceptor chain.proceed(req)

        // 엔드포인트 필터: /generate 만 압축
        val path = req.url.encodedPath
        val isGenerate = path.endsWith("/generate") || path.endsWith("generate")
        if (!isGenerate) return@Interceptor chain.proceed(req)

        // 이미 인코딩됨 or multipart or 작은 바디는 패스
        if (req.header("Content-Encoding") != null) return@Interceptor chain.proceed(req)
        val ct = body.contentType()
        if (ct?.type == "multipart") return@Interceptor chain.proceed(req)
        val len = runCatching { body.contentLength() }.getOrDefault(-1L)
        if (len in 0..1024) return@Interceptor chain.proceed(req)

        val gzipped = object : RequestBody() {
            override fun contentType() = ct
            override fun contentLength(): Long = -1L
            override fun writeTo(sink: okio.BufferedSink) {
                GzipSink(sink).buffer().use { gz -> body.writeTo(gz) }
            }
        }

        val newReq = req.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(req.method, gzipped)
            .build()

        chain.proceed(newReq)
    }

    /**
     * Brotli 네트워크 인터셉터를 reflection으로 옵션 적용 (의존성 없으면 skip)
     * 의존성: "com.squareup.okhttp3:okhttp-brotli:<ver>"
     */
    private fun getOptionalBrotliInterceptor(): Interceptor? = runCatching {
        val clazz = Class.forName("okhttp3.brotli.BrotliInterceptor")
        clazz.getField("INSTANCE").get(null) as? Interceptor
    }.getOrNull()

    private val ok: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(headerInterceptor)
            .apply {
                if (ENABLE_GZIP_REQUEST) addInterceptor(gzipRequestInterceptor)
                getOptionalBrotliInterceptor()?.let { addNetworkInterceptor(it) }
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(AppCtx.app.getString(R.string.gateway_base_url).ensureTrailingSlash())
            .client(ok)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val api: GatewayApi by lazy { retrofit.create(GatewayApi::class.java) }
}

/* ---------- Response helpers (선택) ---------- */

@Throws(IllegalStateException::class)
fun <T> RetrofitResponse<T>.bodyOrThrow(): T {
    if (isSuccessful) {
        val body = body()
        if (body != null) return body
        val httpCode = this.code()
        throw IllegalStateException("Empty body (HTTP $httpCode)")
    } else {
        val httpCode = this.code()
        val err = errorBody()?.errorBodyText().orEmpty()
        throw IllegalStateException("HTTP $httpCode - ${err.take(500)}")
    }
}

fun ResponseBody.errorBodyText(): String =
    runCatching { string() }.getOrElse { "<no error body>" }
