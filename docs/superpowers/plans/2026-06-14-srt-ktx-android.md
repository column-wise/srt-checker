# SRT/KTX 취소표 알리미 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SRT·KTX 취소표를 Foreground Service로 폴링해 발견 즉시 자동 예약하는 안드로이드 앱 구축

**Architecture:** MVVM + Repository 패턴. TicketWatcherService(Foreground)가 코루틴으로 N초마다 SRT/KTX API 폴링. 취소표 발견 시 즉시 예약 API 호출 후 결과를 푸시 알림으로 전달.

**Tech Stack:** Kotlin, Hilt, Room, Retrofit+OkHttp, Jetpack Security, Coroutines+Flow, Material3, MockWebServer(테스트)

---

## 파일 구조

```
app/src/main/java/com/trainchecker/
  TrainCheckerApp.kt
  MainActivity.kt
  data/
    model/
      WatchJob.kt              # Room Entity + 관련 enum
      TrainInfo.kt             # SRT/KTX 공통 열차 정보 sealed class
    db/
      AppDatabase.kt
      WatchJobDao.kt
    api/
      srt/
        SrtApiService.kt       # Retrofit 인터페이스
        SrtModels.kt           # 요청/응답 data class
        NetFunnelHelper.kt     # 대기열 토큰
        SrtRepository.kt       # login/searchTrains/reserve
      ktx/
        KtxApiService.kt
        KtxModels.kt
        KtxAesHelper.kt        # 비밀번호 AES 암호화
        KtxRepository.kt       # login/searchTrains/reserve
    prefs/
      CredentialStore.kt       # EncryptedSharedPreferences
  service/
    TicketWatcherService.kt    # Foreground Service
    NotificationHelper.kt
  ui/
    home/
      HomeFragment.kt
      HomeViewModel.kt
      SrtFormFragment.kt
      KtxFormFragment.kt
    watchlist/
      WatchListFragment.kt
      WatchListViewModel.kt
      WatchJobAdapter.kt
    settings/
      SettingsFragment.kt
      SettingsViewModel.kt
  di/
    AppModule.kt
    DatabaseModule.kt
    NetworkModule.kt
app/src/test/java/com/trainchecker/
  api/srt/NetFunnelHelperTest.kt
  api/srt/SrtRepositoryTest.kt
  api/ktx/KtxAesHelperTest.kt
  api/ktx/KtxRepositoryTest.kt
app/src/androidTest/java/com/trainchecker/
  db/WatchJobDaoTest.kt
```

---

## Task 1: 프로젝트 설정

**Files:**
- Modify: `build.gradle.kts` (project)
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/trainchecker/TrainCheckerApp.kt`

- [ ] **Step 1: project build.gradle.kts 수정**

```kotlin
// build.gradle.kts (project)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

`libs.versions.toml`:
```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.0"
hilt = "2.51.1"
ksp = "2.0.0-1.0.24"
room = "2.6.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
navigation = "2.7.7"
security = "1.1.0-alpha06"
material = "1.12.0"
coroutines = "1.8.1"
viewpager2 = "1.1.0"
mockwebserver = "4.12.0"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
navigation-fragment = { group = "androidx.navigation", name = "navigation-fragment-ktx", version.ref = "navigation" }
navigation-ui = { group = "androidx.navigation", name = "navigation-ui-ktx", version.ref = "navigation" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
viewpager2 = { group = "androidx.viewpager2", name = "viewpager2", version.ref = "viewpager2" }
```

- [ ] **Step 2: app build.gradle.kts 작성**

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.trainchecker"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.trainchecker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.security.crypto)
    implementation(libs.material)
    implementation(libs.coroutines.android)
    implementation(libs.viewpager2)

    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(libs.room.testing)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
}
```

- [ ] **Step 3: AndroidManifest.xml 작성**

```xml
<!-- app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".TrainCheckerApp"
        android:allowBackup="true"
        android:label="취소표 알리미"
        android:theme="@style/Theme.TrainChecker">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".service.TicketWatcherService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 4: TrainCheckerApp.kt 작성**

```kotlin
// app/src/main/java/com/trainchecker/TrainCheckerApp.kt
package com.trainchecker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrainCheckerApp : Application()
```

- [ ] **Step 5: 빌드 확인**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: project scaffolding with Hilt, Room, Retrofit"
```

---

## Task 2: Room DB — WatchJob 엔티티

**Files:**
- Create: `app/src/main/java/com/trainchecker/data/model/WatchJob.kt`
- Create: `app/src/main/java/com/trainchecker/data/db/WatchJobDao.kt`
- Create: `app/src/main/java/com/trainchecker/data/db/AppDatabase.kt`
- Create: `app/src/main/java/com/trainchecker/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/trainchecker/db/WatchJobDaoTest.kt`

- [ ] **Step 1: WatchJob 모델 작성**

```kotlin
// data/model/WatchJob.kt
package com.trainchecker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrainType { SRT, KTX }
enum class SeatType { GENERAL, SPECIAL, ANY }
enum class WatchStatus { WATCHING, SUCCESS, FAILED, CANCELLED }

@Entity(tableName = "watch_jobs")
data class WatchJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainType: TrainType,
    val depStation: String,
    val arrStation: String,
    val date: String,         // YYYYMMDD
    val timeFrom: String,     // HHMM
    val timeTo: String,       // HHMM (빈 문자열 = 제한 없음)
    val seatType: SeatType,
    val status: WatchStatus,
    val reservationNumber: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: WatchJobDao 작성**

```kotlin
// data/db/WatchJobDao.kt
package com.trainchecker.data.db

import androidx.room.*
import com.trainchecker.data.model.WatchJob
import com.trainchecker.data.model.WatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchJobDao {
    @Query("SELECT * FROM watch_jobs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WatchJob>>

    @Query("SELECT * FROM watch_jobs WHERE status = 'WATCHING'")
    suspend fun getActive(): List<WatchJob>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: WatchJob): Long

    @Update
    suspend fun update(job: WatchJob)

    @Query("UPDATE watch_jobs SET status = :status, reservationNumber = :resNo, updatedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: Long, status: WatchStatus, resNo: String? = null, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM watch_jobs WHERE id = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **Step 3: AppDatabase 작성**

```kotlin
// data/db/AppDatabase.kt
package com.trainchecker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trainchecker.data.model.WatchJob

class Converters {
    @TypeConverter fun fromTrainType(v: TrainType): String = v.name
    @TypeConverter fun toTrainType(v: String): TrainType = TrainType.valueOf(v)
    @TypeConverter fun fromSeatType(v: SeatType): String = v.name
    @TypeConverter fun toSeatType(v: String): SeatType = SeatType.valueOf(v)
    @TypeConverter fun fromWatchStatus(v: WatchStatus): String = v.name
    @TypeConverter fun toWatchStatus(v: String): WatchStatus = WatchStatus.valueOf(v)
}

@Database(entities = [WatchJob::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchJobDao(): WatchJobDao
}
```

- [ ] **Step 4: DatabaseModule 작성**

```kotlin
// di/DatabaseModule.kt
package com.trainchecker.di

import android.content.Context
import androidx.room.Room
import com.trainchecker.data.db.AppDatabase
import com.trainchecker.data.db.WatchJobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "trainchecker.db").build()

    @Provides
    fun provideWatchJobDao(db: AppDatabase): WatchJobDao = db.watchJobDao()
}
```

- [ ] **Step 5: DAO 인스트루먼테이션 테스트 작성**

```kotlin
// androidTest/db/WatchJobDaoTest.kt
package com.trainchecker.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trainchecker.data.db.AppDatabase
import com.trainchecker.data.db.WatchJobDao
import com.trainchecker.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WatchJobDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: WatchJobDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        dao = db.watchJobDao()
    }

    @After fun teardown() = db.close()

    @Test fun insertAndGetAll() = runTest {
        val job = WatchJob(
            trainType = TrainType.SRT,
            depStation = "수서", arrStation = "부산",
            date = "20241225", timeFrom = "0800", timeTo = "",
            seatType = SeatType.GENERAL, status = WatchStatus.WATCHING
        )
        dao.insert(job)
        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("수서", all[0].depStation)
    }

    @Test fun updateStatus() = runTest {
        val id = dao.insert(WatchJob(
            trainType = TrainType.KTX,
            depStation = "서울", arrStation = "부산",
            date = "20241225", timeFrom = "0800", timeTo = "",
            seatType = SeatType.ANY, status = WatchStatus.WATCHING
        ))
        dao.updateStatus(id, WatchStatus.SUCCESS, "ABC123")
        val active = dao.getActive()
        assertEquals(0, active.size)
    }
}
```

- [ ] **Step 6: 테스트 실행**

```bash
./gradlew connectedDebugAndroidTest --tests "com.trainchecker.db.WatchJobDaoTest"
```
Expected: 2 tests PASSED

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: Room DB with WatchJob entity and DAO"
```

---

## Task 3: CredentialStore

**Files:**
- Create: `app/src/main/java/com/trainchecker/data/prefs/CredentialStore.kt`
- Modify: `app/src/main/java/com/trainchecker/di/AppModule.kt`

- [ ] **Step 1: CredentialStore 작성**

```kotlin
// data/prefs/CredentialStore.kt
package com.trainchecker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        ctx,
        "train_credentials",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var srtId: String
        get() = prefs.getString("srt_id", "") ?: ""
        set(v) = prefs.edit().putString("srt_id", v).apply()

    var srtPw: String
        get() = prefs.getString("srt_pw", "") ?: ""
        set(v) = prefs.edit().putString("srt_pw", v).apply()

    var ktxId: String
        get() = prefs.getString("ktx_id", "") ?: ""
        set(v) = prefs.edit().putString("ktx_id", v).apply()

    var ktxPw: String
        get() = prefs.getString("ktx_pw", "") ?: ""
        set(v) = prefs.edit().putString("ktx_pw", v).apply()

    var pollIntervalSeconds: Int
        get() = prefs.getInt("poll_interval", 15)
        set(v) = prefs.edit().putInt("poll_interval", v).apply()
}
```

- [ ] **Step 2: AppModule 작성 (네트워크 DI 포함)**

```kotlin
// di/AppModule.kt
package com.trainchecker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .cookieJar(PersistentCookieJar())
        .build()
}
```

- [ ] **Step 3: PersistentCookieJar 작성**

```kotlin
// di/PersistentCookieJar.kt
package com.trainchecker.di

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host] ?: emptyList()
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: CredentialStore with EncryptedSharedPreferences"
```

---

## Task 4: SRT — NetFunnelHelper

**Files:**
- Create: `app/src/main/java/com/trainchecker/data/api/srt/NetFunnelHelper.kt`
- Test: `app/src/test/java/com/trainchecker/api/srt/NetFunnelHelperTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
// test/api/srt/NetFunnelHelperTest.kt
package com.trainchecker.api.srt

import com.trainchecker.data.api.srt.NetFunnelHelper
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetFunnelHelperTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: NetFunnelHelper

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        helper = NetFunnelHelper(OkHttpClient(), baseUrl = server.url("/").toString())
    }

    @After fun teardown() = server.shutdown()

    @Test fun `getToken returns key on PASS status`() = runTest {
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=abc123&nwait=0&ip=${server.hostName}'"
        ))
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=abc123&nwait=0&ip=${server.hostName}'"
        ))
        val token = helper.getToken()
        assertEquals("abc123", token)
    }

    @Test fun `getToken uses cache within TTL`() = runTest {
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=cached&nwait=0&ip=${server.hostName}'"
        ))
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=cached&nwait=0&ip=${server.hostName}'"
        ))
        helper.getToken()
        val second = helper.getToken()
        assertEquals("cached", second)
        assertEquals(2, server.requestCount) // complete 요청까지 2번
    }
}
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
./gradlew testDebugUnitTest --tests "com.trainchecker.api.srt.NetFunnelHelperTest"
```
Expected: FAILED (NetFunnelHelper not found)

- [ ] **Step 3: NetFunnelHelper 구현**

```kotlin
// data/api/srt/NetFunnelHelper.kt
package com.trainchecker.data.api.srt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetFunnelHelper @Inject constructor(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://nf.letskorail.com",
) {
    private var cachedKey: String? = null
    private var lastFetchTime: Long = 0
    private val cacheTtlMs = 48_000L

    suspend fun getToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedKey?.let { if (now - lastFetchTime < cacheTtlMs) return@withContext it }

        val (status, key, nwait, ip) = makeRequest("5101")
        var curStatus = status
        var curKey = key
        var curIp = ip
        var curNwait = nwait

        while (curStatus == "201") {
            delay(1000)
            val r = makeRequest("5002", curIp, curKey)
            curStatus = r[0]; curKey = r[1]; curNwait = r[2]; curIp = r[3]
        }

        makeRequest("5004", curIp, curKey)

        cachedKey = curKey
        lastFetchTime = System.currentTimeMillis()
        curKey
    }

    fun clear() { cachedKey = null; lastFetchTime = 0 }

    private fun makeRequest(opcode: String, ip: String? = null, key: String? = null): List<String> {
        val host = ip ?: "nf.letskorail.com"
        val url = "$baseUrl/ts.wseq".let { if (ip != null) "https://$host/ts.wseq" else it }
        val params = buildString {
            append("opcode=$opcode&nfid=0&prefix=NetFunnel.gRtype%3D$opcode%3B&js=true")
            append("&${System.currentTimeMillis()}=")
            when (opcode) {
                "5101" -> append("&sid=service_1&aid=act_10")
                "5002" -> append("&sid=service_1&aid=act_10&key=${key}&ttl=1")
                "5004" -> append("&key=${key}")
            }
        }
        val req = Request.Builder().url("$url?$params").get().build()
        val body = client.newCall(req).execute().body!!.string()
        return parse(body)
    }

    private fun parse(response: String): List<String> {
        val match = Regex("NetFunnel\\.gControl\\.result='([^']+)'").find(response)
            ?: return listOf("200", "", "0", "nf.letskorail.com")
        val parts = match.groupValues[1].split(":", limit = 3)
        val status = parts[0]
        val paramMap = if (parts.size > 2) parts[2].split("&")
            .filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=") }
        else emptyMap()
        return listOf(
            status,
            paramMap["key"] ?: "",
            paramMap["nwait"] ?: "0",
            paramMap["ip"] ?: "nf.letskorail.com",
        )
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew testDebugUnitTest --tests "com.trainchecker.api.srt.NetFunnelHelperTest"
```
Expected: 2 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: NetFunnelHelper with caching"
```

---

## Task 5: SRT Repository — login + searchTrains

**Files:**
- Create: `app/src/main/java/com/trainchecker/data/api/srt/SrtModels.kt`
- Create: `app/src/main/java/com/trainchecker/data/api/srt/SrtApiService.kt`
- Create: `app/src/main/java/com/trainchecker/data/api/srt/SrtRepository.kt`
- Create: `app/src/main/java/com/trainchecker/di/NetworkModule.kt`
- Test: `app/src/test/java/com/trainchecker/api/srt/SrtRepositoryTest.kt`

- [ ] **Step 1: SrtModels 작성**

```kotlin
// data/api/srt/SrtModels.kt
package com.trainchecker.data.api.srt

import com.google.gson.annotations.SerializedName

data class SrtLoginResponse(
    @SerializedName("MSG") val msg: String? = null,
    val userMap: SrtUserMap? = null,
)
data class SrtUserMap(
    @SerializedName("MB_CRD_NO") val membershipNo: String,
    @SerializedName("CUST_NM") val name: String,
    @SerializedName("MBL_PHONE") val phone: String,
)

data class SrtSearchResponse(
    val resultMap: List<SrtResultMap>? = null,
    val outDataSets: SrtOutDataSets? = null,
    @SerializedName("ErrorCode") val errorCode: String? = null,
    @SerializedName("ErrorMsg") val errorMsg: String? = null,
)
data class SrtResultMap(val strResult: String, val msgTxt: String = "")
data class SrtOutDataSets(val dsOutput1: List<SrtTrainData>? = null)
data class SrtTrainData(
    val stlbTrnClsfCd: String,
    val trnNo: String,
    val dptDt: String,
    val dptTm: String,
    val dptRsStnCd: String,
    val arvDt: String,
    val arvTm: String,
    val arvRsStnCd: String,
    val gnrmRsvPsbStr: String,   // 일반실 상태 ("예약가능" 포함 시 가능)
    val sprmRsvPsbStr: String,   // 특실 상태
    val rsvWaitPsbCd: String,    // "-1"=없음, "9"=가능, "0"=매진
    val rsvWaitPsbCdNm: String,
    val dptStnRunOrdr: String,
    val dptStnConsOrdr: String,
    val arvStnRunOrdr: String,
    val arvStnConsOrdr: String,
)

data class SrtReserveResponse(
    val resultMap: List<SrtResultMap>? = null,
    val reservListMap: List<Map<String, String>>? = null,
)

// 역 코드 매핑
val SRT_STATION_CODE = mapOf(
    "수서" to "0551", "동탄" to "0552", "평택지제" to "0553",
    "경주" to "0508", "공주" to "0514", "광주송정" to "0036",
    "대전" to "0010", "동대구" to "0015", "부산" to "0020",
    "서대구" to "0506", "오송" to "0297", "울산(통도사)" to "0509",
    "익산" to "0030", "전주" to "0045", "천안아산" to "0502",
    "포항" to "0515", "목포" to "0041", "순천" to "0051",
    "여수EXPO" to "0053", "진주" to "0063", "창원" to "0057",
)
val SRT_STATION_NAME = SRT_STATION_CODE.entries.associate { (k, v) -> v to k }
```

- [ ] **Step 2: SrtApiService Retrofit 인터페이스 작성**

```kotlin
// data/api/srt/SrtApiService.kt
package com.trainchecker.data.api.srt

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface SrtApiService {
    @FormUrlEncoded
    @POST("/apb/selectListApb01080_n.do")
    suspend fun login(
        @Field("auto") auto: String = "Y",
        @Field("check") check: String = "Y",
        @Field("page") page: String = "menu",
        @Field("deviceKey") deviceKey: String = "-",
        @Field("customerYn") customerYn: String = "",
        @Field("login_referer") loginReferer: String = "https://app.srail.or.kr/main/main.do",
        @Field("srchDvCd") loginType: String,   // "1"=회원번호, "2"=이메일, "3"=전화번호
        @Field("srchDvNm") id: String,
        @Field("hmpgPwdCphd") pw: String,
    ): SrtLoginResponse

    @FormUrlEncoded
    @POST("/ara/selectListAra10007_n.do")
    suspend fun searchTrains(
        @Field("chtnDvCd") chtnDvCd: String = "1",
        @Field("dptDt") date: String,           // YYYYMMDD
        @Field("dptTm") time: String,           // HHMMSS
        @Field("dptDt1") date1: String,
        @Field("dptTm1") time1: String,
        @Field("dptRsStnCd") depCode: String,
        @Field("arvRsStnCd") arrCode: String,
        @Field("stlbTrnClsfCd") trainClass: String = "05",
        @Field("trnGpCd") trnGpCd: Int = 109,
        @Field("trnNo") trnNo: String = "",
        @Field("psgNum") psgNum: String = "1",
        @Field("seatAttCd") seatAttCd: String = "015",
        @Field("arriveTime") arriveTime: String = "N",
        @Field("dlayTnumAplFlg") dlayFlag: String = "Y",
        @Field("netfunnelKey") netfunnelKey: String,
    ): SrtSearchResponse

    @FormUrlEncoded
    @POST("/arc/selectListArc05013_n.do")
    suspend fun reserve(
        @Field("jobId") jobId: String,          // "1101"=개인, "1102"=대기
        @Field("jrnyCnt") jrnyCnt: String = "1",
        @Field("jrnyTpCd") jrnyTpCd: String = "11",
        @Field("jrnySqno1") jrnySqno1: String = "001",
        @Field("stndFlg") stndFlg: String = "N",
        @Field("trnGpCd1") trnGpCd1: String = "300",
        @Field("trnGpCd") trnGpCd: String = "109",
        @Field("grpDv") grpDv: String = "0",
        @Field("rtnDv") rtnDv: String = "0",
        @Field("stlbTrnClsfCd1") trainCode: String,
        @Field("dptRsStnCd1") depCode: String,
        @Field("dptRsStnCdNm1") depName: String,
        @Field("arvRsStnCd1") arrCode: String,
        @Field("arvRsStnCdNm1") arrName: String,
        @Field("dptDt1") depDate: String,
        @Field("dptTm1") depTime: String,
        @Field("arvTm1") arrTime: String,
        @Field("trnNo1") trainNo: String,
        @Field("runDt1") runDate: String,
        @Field("dptStnConsOrdr1") depConsOrdr: String,
        @Field("arvStnConsOrdr1") arrConsOrdr: String,
        @Field("dptStnRunOrdr1") depRunOrdr: String,
        @Field("arvStnRunOrdr1") arrRunOrdr: String,
        @Field("totPrnb") totPrnb: String = "1",
        @Field("psgGridcnt") psgGridcnt: String = "1",
        @Field("psgTpCd1") psgTpCd: String = "1",
        @Field("psgInfoPerPrnb1") psgCount: String = "1",
        @Field("psrmClCd1") seatClass: String,  // "1"=일반, "2"=특실
        @Field("locSeatAttCd1") locSeat: String = "000",
        @Field("rqSeatAttCd1") rqSeat: String = "015",
        @Field("dirSeatAttCd1") dirSeat: String = "009",
        @Field("smkSeatAttCd1") smkSeat: String = "000",
        @Field("etcSeatAttCd1") etcSeat: String = "000",
        @Field("reserveType") reserveType: String = "11",
        @Field("netfunnelKey") netfunnelKey: String,
    ): SrtReserveResponse
}
```

- [ ] **Step 3: NetworkModule 작성**

```kotlin
// di/NetworkModule.kt
package com.trainchecker.di

import com.trainchecker.data.api.ktx.KtxApiService
import com.trainchecker.data.api.srt.SrtApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton @Named("srt")
    fun provideSrtRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://app.srail.or.kr/")
        .client(client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 15; SM-S912N Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.7103.125 Mobile Safari/537.36SRT-APP-Android V.2.0.38")
                    .header("Accept", "application/json")
                    .build())
            }
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideSrtApiService(@Named("srt") retrofit: Retrofit): SrtApiService =
        retrofit.create(SrtApiService::class.java)

    @Provides @Singleton @Named("ktx")
    fun provideKtxRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://smart.letskorail.com/")
        .client(client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; SM-S912N Build/UP1A.231005.007)")
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Host", "smart.letskorail.com")
                    .header("Connection", "Keep-Alive")
                    .header("Accept-Encoding", "gzip")
                    .build())
            }
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideKtxApiService(@Named("ktx") retrofit: Retrofit): KtxApiService =
        retrofit.create(KtxApiService::class.java)
}
```

- [ ] **Step 4: SrtRepository 작성**

```kotlin
// data/api/srt/SrtRepository.kt
package com.trainchecker.data.api.srt

import com.trainchecker.data.model.SeatType
import javax.inject.Inject
import javax.inject.Singleton

data class SrtTrain(val raw: SrtTrainData) {
    val depStation get() = SRT_STATION_NAME[raw.dptRsStnCd] ?: raw.dptRsStnCd
    val arrStation get() = SRT_STATION_NAME[raw.arvRsStnCd] ?: raw.arvRsStnCd
    val depDate get() = raw.dptDt
    val depTime get() = raw.dptTm
    val trainNo get() = raw.trnNo.padStart(5, '0')
    fun generalAvailable() = "예약가능" in raw.gnrmRsvPsbStr
    fun specialAvailable() = "예약가능" in raw.sprmRsvPsbStr
    fun seatAvailable(seatType: SeatType) = when (seatType) {
        SeatType.GENERAL -> generalAvailable()
        SeatType.SPECIAL -> specialAvailable()
        SeatType.ANY -> generalAvailable() || specialAvailable()
    }
    fun waitAvailable() = raw.rsvWaitPsbCd == "9"
}

sealed class SrtResult {
    data class Success(val reservationNo: String) : SrtResult()
    data class Error(val message: String) : SrtResult()
}

@Singleton
class SrtRepository @Inject constructor(
    private val api: SrtApiService,
    private val netFunnel: NetFunnelHelper,
) {
    private var loggedIn = false

    suspend fun login(id: String, pw: String): Boolean {
        val loginType = when {
            Regex("[^@]+@[^@]+\\.[^@]+").matches(id) -> "2"
            Regex("\\d{3}-\\d{3,4}-\\d{4}").matches(id) -> "3"
            else -> "1"
        }
        val cleanId = if (loginType == "3") id.replace("-", "") else id
        val resp = api.login(loginType = loginType, id = cleanId, pw = pw)
        loggedIn = resp.userMap != null
        return loggedIn
    }

    suspend fun searchTrains(
        dep: String, arr: String, date: String, timeFrom: String,
    ): List<SrtTrain> {
        val depCode = SRT_STATION_CODE[dep] ?: error("Unknown station: $dep")
        val arrCode = SRT_STATION_CODE[arr] ?: error("Unknown station: $arr")
        val time = "${timeFrom}00"
        val key = netFunnel.getToken()
        val resp = api.searchTrains(
            date = date, time = time, date1 = date, time1 = "${timeFrom.take(2)}0000",
            depCode = depCode, arrCode = arrCode, netfunnelKey = key,
        )
        val result = resp.resultMap?.firstOrNull() ?: return emptyList()
        if (result.strResult != "SUCC") return emptyList()
        return resp.outDataSets?.dsOutput1
            ?.filter { it.stlbTrnClsfCd == "17" }
            ?.map { SrtTrain(it) } ?: emptyList()
    }

    suspend fun reserve(train: SrtTrain, seatType: SeatType): SrtResult {
        val isSpecial = when (seatType) {
            SeatType.SPECIAL -> true
            SeatType.GENERAL -> false
            SeatType.ANY -> !train.generalAvailable()
        }
        val key = netFunnel.getToken()
        val resp = api.reserve(
            jobId = "1101",
            trainCode = train.raw.stlbTrnClsfCd,
            depCode = train.raw.dptRsStnCd,
            depName = train.depStation,
            arrCode = train.raw.arvRsStnCd,
            arrName = train.arrStation,
            depDate = train.raw.dptDt,
            depTime = train.raw.dptTm,
            arrTime = train.raw.arvTm,
            trainNo = train.trainNo,
            runDate = train.raw.dptDt,
            depConsOrdr = train.raw.dptStnConsOrdr,
            arrConsOrdr = train.raw.arvStnConsOrdr,
            depRunOrdr = train.raw.dptStnRunOrdr,
            arrRunOrdr = train.raw.arvStnRunOrdr,
            seatClass = if (isSpecial) "2" else "1",
            netfunnelKey = key,
        )
        val result = resp.resultMap?.firstOrNull()
        return if (result?.strResult == "SUCC") {
            val resNo = resp.reservListMap?.firstOrNull()?.get("pnrNo") ?: ""
            SrtResult.Success(resNo)
        } else {
            SrtResult.Error(result?.msgTxt ?: "예약 실패")
        }
    }
}
```

- [ ] **Step 5: MockWebServer 테스트 작성**

```kotlin
// test/api/srt/SrtRepositoryTest.kt
package com.trainchecker.api.srt

import com.trainchecker.data.api.srt.*
import com.trainchecker.data.model.SeatType
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SrtRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: SrtRepository
    private lateinit var netFunnel: NetFunnelHelper

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SrtApiService::class.java)
        netFunnel = NetFunnelHelper(client, server.url("/").toString())
        repo = SrtRepository(api, netFunnel)
    }

    @After fun teardown() = server.shutdown()

    private fun enqueueNetFunnel() {
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=tok123&nwait=0&ip=${server.hostName}'"
        ))
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=tok123&nwait=0&ip=${server.hostName}'"
        ))
    }

    @Test fun `login returns true on success`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"userMap":{"MB_CRD_NO":"12345","CUST_NM":"홍길동","MBL_PHONE":"01012345678"}}"""
        ))
        assertTrue(repo.login("test@email.com", "pw"))
    }

    @Test fun `searchTrains returns list on SUCC`() = runTest {
        enqueueNetFunnel()
        server.enqueue(MockResponse().setBody("""
            {
              "resultMap":[{"strResult":"SUCC","msgTxt":""}],
              "outDataSets":{"dsOutput1":[{
                "stlbTrnClsfCd":"17","trnNo":"101",
                "dptDt":"20241225","dptTm":"080000",
                "dptRsStnCd":"0551","arvDt":"20241225","arvTm":"110000",
                "arvRsStnCd":"0020",
                "gnrmRsvPsbStr":"예약가능","sprmRsvPsbStr":"매진",
                "rsvWaitPsbCd":"-1","rsvWaitPsbCdNm":"없음",
                "dptStnRunOrdr":"001","dptStnConsOrdr":"001",
                "arvStnRunOrdr":"010","arvStnConsOrdr":"010"
              }]}
            }
        """.trimIndent()))
        val trains = repo.searchTrains("수서", "부산", "20241225", "0800")
        assertEquals(1, trains.size)
        assertTrue(trains[0].generalAvailable())
    }

    @Test fun `searchTrains returns empty on FAIL`() = runTest {
        enqueueNetFunnel()
        server.enqueue(MockResponse().setBody(
            """{"resultMap":[{"strResult":"FAIL","msgTxt":"조회 오류"}]}"""
        ))
        val trains = repo.searchTrains("수서", "부산", "20241225", "0800")
        assertEquals(0, trains.size)
    }
}
```

- [ ] **Step 6: 테스트 실행**

```bash
./gradlew testDebugUnitTest --tests "com.trainchecker.api.srt.SrtRepositoryTest"
```
Expected: 3 tests PASSED

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: SrtRepository with login/search/reserve"
```

---

## Task 6: KTX — AesHelper + Repository

**Files:**
- Create: `app/src/main/java/com/trainchecker/data/api/ktx/KtxModels.kt`
- Create: `app/src/main/java/com/trainchecker/data/api/ktx/KtxApiService.kt`
- Create: `app/src/main/java/com/trainchecker/data/api/ktx/KtxAesHelper.kt`
- Create: `app/src/main/java/com/trainchecker/data/api/ktx/KtxRepository.kt`
- Test: `app/src/test/java/com/trainchecker/api/ktx/KtxAesHelperTest.kt`
- Test: `app/src/test/java/com/trainchecker/api/ktx/KtxRepositoryTest.kt`

- [ ] **Step 1: KtxModels 작성**

```kotlin
// data/api/ktx/KtxModels.kt
package com.trainchecker.data.api.ktx

data class KtxCodeResponse(
    val strResult: String,
    @SerializedName("app.login.cphd") val cipherInfo: KtxCipherInfo? = null,
)
data class KtxCipherInfo(val key: String, val idx: String)

data class KtxLoginResponse(
    val strResult: String,
    val strMbCrdNo: String? = null,
    val strCustNm: String? = null,
    val strCpNo: String? = null,
)

data class KtxSearchResponse(
    val strResult: String? = null,
    val h_msg_txt: String? = null,
    val trn_infos: KtxTrnInfos? = null,
)
data class KtxTrnInfos(val trn_info: List<KtxTrainData>? = null)
data class KtxTrainData(
    val h_trn_clsf_cd: String,
    val h_trn_gp_cd: String,
    val h_trn_no: String,
    val h_dpt_rs_stn_nm: String,
    val h_dpt_rs_stn_cd: String,
    val h_dpt_dt: String,
    val h_dpt_tm: String,
    val h_arv_rs_stn_nm: String,
    val h_arv_rs_stn_cd: String,
    val h_arv_dt: String,
    val h_arv_tm: String,
    val h_run_dt: String,
    val h_rsv_psb_flg: String,    // "Y"=예약가능
    val h_spe_rsv_cd: String,     // "11"=특실 가능
    val h_gen_rsv_cd: String,     // "11"=일반 가능
    val h_wait_rsv_flg: String,   // "9"=대기 가능
)

data class KtxReserveResponse(
    val strResult: String,
    val h_msg_txt: String? = null,
    val h_pnr_no: String? = null,
)
```

- [ ] **Step 2: KtxApiService 작성**

```kotlin
// data/api/ktx/KtxApiService.kt
package com.trainchecker.data.api.ktx

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface KtxApiService {
    @FormUrlEncoded
    @POST("/classes/com.korail.mobile.common.code.do")
    suspend fun getCode(@Field("code") code: String = "app.login.cphd"): KtxCodeResponse

    @FormUrlEncoded
    @POST("/classes/com.korail.mobile.login.Login")
    suspend fun login(
        @Field("Device") device: String = "AD",
        @Field("Version") version: String = "240531001",
        @Field("Key") key: String = "korail1234567890",
        @Field("txtMemberNo") id: String,
        @Field("txtPwd") encPw: String,
        @Field("txtInputFlg") inputFlg: String,  // "2"=회원번호, "4"=전화번호, "5"=이메일
        @Field("idx") idx: String,
    ): KtxLoginResponse

    @FormUrlEncoded
    @POST("/classes/com.korail.mobile.seatMovie.ScheduleView")
    suspend fun searchTrains(
        @Field("Device") device: String = "AD",
        @Field("Version") version: String = "240531001",
        @Field("Sid") sid: String = "",
        @Field("txtMenuId") menuId: String = "11",
        @Field("radJobId") jobId: String = "1",
        @Field("selGoTrain") goTrain: String = "05",
        @Field("txtTrnGpCd") trnGpCd: String = "05",
        @Field("txtGoStart") dep: String,    // 역 이름
        @Field("txtGoEnd") arr: String,
        @Field("txtGoAbrdDt") date: String,  // YYYYMMDD
        @Field("txtGoHour") time: String,    // HHMMSS
        @Field("txtPsgFlg_1") adult: Int = 1,
        @Field("txtPsgFlg_2") child: Int = 0,
        @Field("txtPsgFlg_3") senior: Int = 0,
        @Field("txtPsgFlg_4") dis13: Int = 0,
        @Field("txtPsgFlg_5") dis46: Int = 0,
        @Field("txtSeatAttCd1") seatAtt: String = "000",
        @Field("txtSeatAttCd2") seatAtt2: String = "000",
        @Field("txtSeatAttCd3") seatAtt3: String = "000",
        @Field("txtSeatAttCd4") seatAtt4: String = "015",
        @Field("txtSeatAttCd5") seatAtt5: String = "000",
    ): KtxSearchResponse

    @GET("/classes/com.korail.mobile.certification.TicketReservation")
    suspend fun reserve(
        @Query("Device") device: String = "AD",
        @Query("Version") version: String = "240531001",
        @Query("Key") key: String = "korail1234567890",
        @Query("txtMenuId") menuId: String = "11",
        @Query("txtJobId") jobId: String,        // "1101"=예약, "1102"=대기
        @Query("txtGdNo") gdNo: String = "",
        @Query("hidFreeFlg") freeFlg: String = "N",
        @Query("txtTotPsgCnt") psgCnt: Int = 1,
        @Query("txtSeatAttCd1") sa1: String = "000",
        @Query("txtSeatAttCd2") sa2: String = "000",
        @Query("txtSeatAttCd3") sa3: String = "000",
        @Query("txtSeatAttCd4") sa4: String = "015",
        @Query("txtSeatAttCd5") sa5: String = "000",
        @Query("txtStndFlg") stndFlg: String = "N",
        @Query("txtSrcarCnt") srcarCnt: String = "0",
        @Query("txtJrnyCnt") jrnyCnt: String = "1",
        @Query("txtJrnySqno1") jrnySqno: String = "001",
        @Query("txtJrnyTpCd1") jrnyTpCd: String = "11",
        @Query("txtDptDt1") depDate: String,
        @Query("txtDptRsStnCd1") depCode: String,
        @Query("txtDptTm1") depTime: String,
        @Query("txtArvRsStnCd1") arrCode: String,
        @Query("txtTrnNo1") trnNo: String,
        @Query("txtRunDt1") runDate: String,
        @Query("txtTrnClsfCd1") trnClsfCd: String,
        @Query("txtTrnGpCd1") trnGpCd: String,
        @Query("txtPsrmClCd1") psrmClCd: String,  // "1"=일반, "2"=특실
        @Query("txtChgFlg1") chgFlg: String = "",
        @Query("txtPsgTpCd1") psgTpCd: String = "1",
        @Query("txtDiscKndCd1") discKnd: String = "000",
        @Query("txtPsgInfoPerPrnb1") psgInfo: String = "1",
        @Query("txtCompaCd1") compaCd: String = "5",
    ): KtxReserveResponse
}
```

- [ ] **Step 3: KtxAesHelper 작성 + 테스트**

```kotlin
// data/api/ktx/KtxAesHelper.kt
package com.trainchecker.data.api.ktx

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtxAesHelper @Inject constructor() {
    /**
     * KTX API 비밀번호 암호화.
     * key는 /code 엔드포인트에서 동적 발급. IV = key 앞 16바이트.
     * 암호화 결과를 Base64로 2번 인코딩.
     */
    fun encrypt(password: String, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val iv = key.substring(0, 16).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val b64once = Base64.encode(encrypted, Base64.NO_WRAP)
        return Base64.encodeToString(b64once, Base64.NO_WRAP)
    }
}
```

```kotlin
// test/api/ktx/KtxAesHelperTest.kt
package com.trainchecker.api.ktx

import com.trainchecker.data.api.ktx.KtxAesHelper
import org.junit.Assert.*
import org.junit.Test

class KtxAesHelperTest {
    private val helper = KtxAesHelper()

    @Test fun `encrypt produces non-empty string`() {
        val key = "1234567890abcdef"  // 16바이트 키
        val result = helper.encrypt("mypassword", key)
        assertTrue(result.isNotEmpty())
    }

    @Test fun `encrypt same input same key gives same output`() {
        val key = "1234567890abcdef"
        val r1 = helper.encrypt("password", key)
        val r2 = helper.encrypt("password", key)
        assertEquals(r1, r2)
    }

    @Test fun `encrypt different passwords differ`() {
        val key = "1234567890abcdef"
        assertNotEquals(helper.encrypt("pass1", key), helper.encrypt("pass2", key))
    }
}
```

- [ ] **Step 4: KtxRepository 작성**

```kotlin
// data/api/ktx/KtxRepository.kt
package com.trainchecker.data.api.ktx

import com.trainchecker.data.model.SeatType
import javax.inject.Inject
import javax.inject.Singleton

data class KtxTrain(val raw: KtxTrainData) {
    fun generalAvailable() = raw.h_gen_rsv_cd == "11"
    fun specialAvailable() = raw.h_spe_rsv_cd == "11"
    fun seatAvailable(seatType: SeatType) = when (seatType) {
        SeatType.GENERAL -> generalAvailable()
        SeatType.SPECIAL -> specialAvailable()
        SeatType.ANY -> generalAvailable() || specialAvailable()
    }
    fun waitAvailable() = raw.h_wait_rsv_flg == "9"
}

sealed class KtxResult {
    data class Success(val reservationNo: String) : KtxResult()
    data class Error(val message: String) : KtxResult()
}

@Singleton
class KtxRepository @Inject constructor(
    private val api: KtxApiService,
    private val aes: KtxAesHelper,
) {
    private var loggedIn = false

    suspend fun login(id: String, pw: String): Boolean {
        val codeResp = api.getCode()
        val cipherInfo = codeResp.cipherInfo ?: return false
        val encPw = aes.encrypt(pw, cipherInfo.key)
        val inputFlg = when {
            Regex("[^@]+@[^@]+\\.[^@]+").matches(id) -> "5"
            Regex("\\d{3}-\\d{3,4}-\\d{4}").matches(id) -> "4"
            else -> "2"
        }
        val resp = api.login(id = id, encPw = encPw, inputFlg = inputFlg, idx = cipherInfo.idx)
        loggedIn = resp.strResult == "SUCC" && resp.strMbCrdNo != null
        return loggedIn
    }

    suspend fun searchTrains(
        dep: String, arr: String, date: String, timeFrom: String,
    ): List<KtxTrain> {
        val resp = api.searchTrains(dep = dep, arr = arr, date = date, time = "${timeFrom}00")
        if (resp.strResult != "SUCC") return emptyList()
        return resp.trn_infos?.trn_info
            ?.filter { it.h_trn_clsf_cd in listOf("00", "07", "10") } // KTX 계열만
            ?.map { KtxTrain(it) } ?: emptyList()
    }

    suspend fun reserve(train: KtxTrain, seatType: SeatType): KtxResult {
        val isSpecial = when (seatType) {
            SeatType.SPECIAL -> true
            SeatType.GENERAL -> false
            SeatType.ANY -> !train.generalAvailable()
        }
        val jobId = if (train.seatAvailable(seatType)) "1101" else "1102"
        val resp = api.reserve(
            jobId = jobId,
            depDate = train.raw.h_dpt_dt,
            depCode = train.raw.h_dpt_rs_stn_cd,
            depTime = train.raw.h_dpt_tm,
            arrCode = train.raw.h_arv_rs_stn_cd,
            trnNo = train.raw.h_trn_no,
            runDate = train.raw.h_run_dt,
            trnClsfCd = train.raw.h_trn_clsf_cd,
            trnGpCd = train.raw.h_trn_gp_cd,
            psrmClCd = if (isSpecial) "2" else "1",
        )
        return if (resp.strResult == "SUCC" && resp.h_pnr_no != null) {
            KtxResult.Success(resp.h_pnr_no)
        } else {
            KtxResult.Error(resp.h_msg_txt ?: "예약 실패")
        }
    }
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew testDebugUnitTest --tests "com.trainchecker.api.ktx.KtxAesHelperTest"
```
Expected: 3 tests PASSED

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: KTX repository with AES password encryption"
```

---

## Task 7: NotificationHelper

**Files:**
- Create: `app/src/main/java/com/trainchecker/service/NotificationHelper.kt`

- [ ] **Step 1: NotificationHelper 작성**

```kotlin
// service/NotificationHelper.kt
package com.trainchecker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.trainchecker.MainActivity
import com.trainchecker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    companion object {
        const val CHANNEL_WATCHING = "watching"
        const val CHANNEL_RESULT = "result"
        const val NOTIF_ID_WATCHING = 1001
        const val NOTIF_ID_RESULT = 1002
    }

    init {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCHING, "감시 중", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "예약 결과", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun buildWatchingNotification(summary: String): Notification {
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_WATCHING)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle("취소표 감시 중")
            .setContentText(summary)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    fun notifySuccess(title: String, detail: String) {
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_RESULT, notif)
    }

    fun notifyError(message: String) {
        val notif = NotificationCompat.Builder(ctx, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle("오류 발생")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_RESULT + 1, notif)
    }
}
```

> **Note:** `R.drawable.ic_train` — Vector Asset으로 기차 아이콘 추가 필요. Android Studio: File → New → Vector Asset → Clip Art에서 "train" 검색.

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "feat: NotificationHelper with watching/result channels"
```

---

## Task 8: TicketWatcherService

**Files:**
- Create: `app/src/main/java/com/trainchecker/service/TicketWatcherService.kt`

- [ ] **Step 1: TicketWatcherService 작성**

```kotlin
// service/TicketWatcherService.kt
package com.trainchecker.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.trainchecker.data.api.ktx.KtxRepository
import com.trainchecker.data.api.ktx.KtxResult
import com.trainchecker.data.api.srt.SrtRepository
import com.trainchecker.data.api.srt.SrtResult
import com.trainchecker.data.db.WatchJobDao
import com.trainchecker.data.model.TrainType
import com.trainchecker.data.model.WatchJob
import com.trainchecker.data.model.WatchStatus
import com.trainchecker.data.prefs.CredentialStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TicketWatcherService : Service() {
    @Inject lateinit var srtRepo: SrtRepository
    @Inject lateinit var ktxRepo: KtxRepository
    @Inject lateinit var dao: WatchJobDao
    @Inject lateinit var creds: CredentialStore
    @Inject lateinit var notif: NotificationHelper

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = mutableMapOf<Long, Job>()

    companion object {
        const val ACTION_START = "com.trainchecker.START_WATCH"
        const val ACTION_STOP = "com.trainchecker.STOP_WATCH"
        const val EXTRA_JOB_ID = "job_id"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationHelper.NOTIF_ID_WATCHING,
            notif.buildWatchingNotification("시작 중...")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
                if (jobId >= 0) startWatching(jobId)
            }
            ACTION_STOP -> {
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
                if (jobId >= 0) stopWatching(jobId)
            }
        }
        return START_STICKY
    }

    private fun startWatching(jobId: Long) {
        if (jobs.containsKey(jobId)) return
        jobs[jobId] = scope.launch {
            val watchJob = dao.getActive().find { it.id == jobId } ?: return@launch
            watchLoop(watchJob)
        }
    }

    private fun stopWatching(jobId: Long) {
        jobs.remove(jobId)?.cancel()
        scope.launch {
            dao.updateStatus(jobId, WatchStatus.CANCELLED)
        }
        if (jobs.isEmpty()) stopSelf()
    }

    private suspend fun watchLoop(watchJob: WatchJob) {
        val intervalMs = creds.pollIntervalSeconds * 1000L

        // 로그인
        val loginOk = when (watchJob.trainType) {
            TrainType.SRT -> srtRepo.login(creds.srtId, creds.srtPw)
            TrainType.KTX -> ktxRepo.login(creds.ktxId, creds.ktxPw)
        }
        if (!loginOk) {
            dao.updateStatus(watchJob.id, WatchStatus.FAILED)
            notif.notifyError("로그인 실패 — 설정에서 로그인 정보를 확인하세요")
            return
        }

        while (true) {
            try {
                val result = attemptReserve(watchJob)
                if (result != null) {
                    dao.updateStatus(watchJob.id, WatchStatus.SUCCESS, result)
                    notif.notifySuccess(
                        "예약 완료!",
                        "${watchJob.depStation}→${watchJob.arrStation} ${watchJob.date}"
                    )
                    jobs.remove(watchJob.id)
                    if (jobs.isEmpty()) stopSelf()
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 네트워크 오류 등 — 재시도
            }
            delay(intervalMs)
        }
    }

    private suspend fun attemptReserve(watchJob: WatchJob): String? {
        return when (watchJob.trainType) {
            TrainType.SRT -> {
                val trains = srtRepo.searchTrains(
                    watchJob.depStation, watchJob.arrStation,
                    watchJob.date, watchJob.timeFrom,
                )
                val candidate = trains.firstOrNull { t ->
                    t.seatAvailable(watchJob.seatType) &&
                    (watchJob.timeTo.isEmpty() || t.depTime <= "${watchJob.timeTo}00")
                } ?: return null
                when (val r = srtRepo.reserve(candidate, watchJob.seatType)) {
                    is SrtResult.Success -> r.reservationNo
                    is SrtResult.Error -> null
                }
            }
            TrainType.KTX -> {
                val trains = ktxRepo.searchTrains(
                    watchJob.depStation, watchJob.arrStation,
                    watchJob.date, watchJob.timeFrom,
                )
                val candidate = trains.firstOrNull { t ->
                    t.seatAvailable(watchJob.seatType) &&
                    (watchJob.timeTo.isEmpty() || t.raw.h_dpt_tm <= "${watchJob.timeTo}00")
                } ?: return null
                when (val r = ktxRepo.reserve(candidate, watchJob.seatType)) {
                    is KtxResult.Success -> r.reservationNo
                    is KtxResult.Error -> null
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: TicketWatcherService with coroutine polling"
```

---

## Task 9: SettingsFragment

**Files:**
- Create: `app/src/main/java/com/trainchecker/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/trainchecker/ui/settings/SettingsFragment.kt`
- Create: `app/src/main/res/layout/fragment_settings.xml`

- [ ] **Step 1: SettingsViewModel 작성**

```kotlin
// ui/settings/SettingsViewModel.kt
package com.trainchecker.ui.settings

import androidx.lifecycle.ViewModel
import com.trainchecker.data.prefs.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: CredentialStore,
) : ViewModel() {
    val srtId = MutableStateFlow(store.srtId)
    val srtPw = MutableStateFlow(store.srtPw)
    val ktxId = MutableStateFlow(store.ktxId)
    val ktxPw = MutableStateFlow(store.ktxPw)
    val pollInterval: StateFlow<Int> = MutableStateFlow(store.pollIntervalSeconds)

    fun save(srtId: String, srtPw: String, ktxId: String, ktxPw: String, interval: Int) {
        store.srtId = srtId; store.srtPw = srtPw
        store.ktxId = ktxId; store.ktxPw = ktxPw
        store.pollIntervalSeconds = interval.coerceIn(15, 60)
    }
}
```

- [ ] **Step 2: fragment_settings.xml 작성**

```xml
<!-- res/layout/fragment_settings.xml -->
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gap="12dp">

        <com.google.android.material.textview.MaterialTextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="SRT 계정"
            android:textAppearance="?attr/textAppearanceTitleMedium" />
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="SRT 아이디 (회원번호/이메일/전화번호)">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etSrtId"
                android:layout_width="match_parent" android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="SRT 비밀번호"
            app:endIconMode="password_toggle">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etSrtPw"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:inputType="textPassword" />
        </com.google.android.material.textfield.TextInputLayout>

        <com.google.android.material.textview.MaterialTextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="KTX 계정"
            android:textAppearance="?attr/textAppearanceTitleMedium" />
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="KTX 아이디">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etKtxId"
                android:layout_width="match_parent" android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="KTX 비밀번호"
            app:endIconMode="password_toggle">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etKtxPw"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:inputType="textPassword" />
        </com.google.android.material.textfield.TextInputLayout>

        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:hint="폴링 간격 (초, 15~60)">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etInterval"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:inputType="number" />
        </com.google.android.material.textfield.TextInputLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSave"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="저장" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: SettingsFragment 작성**

```kotlin
// ui/settings/SettingsFragment.kt
package com.trainchecker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.trainchecker.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.etSrtId.setText(vm.srtId.value)
        binding.etSrtPw.setText(vm.srtPw.value)
        binding.etKtxId.setText(vm.ktxId.value)
        binding.etKtxPw.setText(vm.ktxPw.value)
        binding.etInterval.setText(vm.pollInterval.value.toString())

        binding.btnSave.setOnClickListener {
            val interval = binding.etInterval.text.toString().toIntOrNull() ?: 15
            vm.save(
                srtId = binding.etSrtId.text.toString(),
                srtPw = binding.etSrtPw.text.toString(),
                ktxId = binding.etKtxId.text.toString(),
                ktxPw = binding.etKtxPw.text.toString(),
                interval = interval,
            )
            Snackbar.make(view, "저장됨", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
```

> **Note:** ViewBinding 활성화 필요. `app/build.gradle.kts`의 `android {}` 블록에 추가:
> ```kotlin
> buildFeatures { viewBinding = true }
> ```

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: SettingsFragment with credential management"
```

---

## Task 10: HomeFragment (감시 조건 입력)

**Files:**
- Create: `app/src/main/java/com/trainchecker/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/trainchecker/ui/home/HomeFragment.kt`
- Create: `app/src/main/java/com/trainchecker/ui/home/SrtFormFragment.kt`
- Create: `app/src/main/java/com/trainchecker/ui/home/KtxFormFragment.kt`
- Create: `app/src/main/res/layout/fragment_home.xml`
- Create: `app/src/main/res/layout/fragment_srt_form.xml`

- [ ] **Step 1: HomeViewModel 작성**

```kotlin
// ui/home/HomeViewModel.kt
package com.trainchecker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainchecker.data.db.WatchJobDao
import com.trainchecker.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val dao: WatchJobDao) : ViewModel() {

    fun startWatch(
        trainType: TrainType,
        dep: String, arr: String,
        date: String, timeFrom: String, timeTo: String,
        seatType: SeatType,
        onJobId: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val id = dao.insert(WatchJob(
                trainType = trainType,
                depStation = dep, arrStation = arr,
                date = date, timeFrom = timeFrom, timeTo = timeTo,
                seatType = seatType,
                status = WatchStatus.WATCHING,
            ))
            onJobId(id)
        }
    }
}
```

- [ ] **Step 2: fragment_home.xml 작성**

```xml
<!-- res/layout/fragment_home.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

- [ ] **Step 3: fragment_srt_form.xml 작성**

```xml
<!-- res/layout/fragment_srt_form.xml -->
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gap="8dp">

        <!-- 출발역 -->
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="출발역"
            style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu">
            <AutoCompleteTextView
                android:id="@+id/actvDep"
                android:layout_width="match_parent" android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>

        <!-- 도착역 -->
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="도착역"
            style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu">
            <AutoCompleteTextView
                android:id="@+id/actvArr"
                android:layout_width="match_parent" android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>

        <!-- 날짜 -->
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="날짜 (YYYYMMDD)">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etDate"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:inputType="number" android:maxLength="8" />
        </com.google.android.material.textfield.TextInputLayout>

        <!-- 출발 시간 범위 -->
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal" android:gap="8dp">
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="0dp" android:layout_height="wrap_content"
                android:layout_weight="1" android:hint="시작 시간 (HHMM)">
                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/etTimeFrom"
                    android:layout_width="match_parent" android:layout_height="wrap_content"
                    android:inputType="number" android:maxLength="4" />
            </com.google.android.material.textfield.TextInputLayout>
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="0dp" android:layout_height="wrap_content"
                android:layout_weight="1" android:hint="종료 시간 (선택)">
                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/etTimeTo"
                    android:layout_width="match_parent" android:layout_height="wrap_content"
                    android:inputType="number" android:maxLength="4" />
            </com.google.android.material.textfield.TextInputLayout>
        </LinearLayout>

        <!-- 좌석 유형 -->
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:hint="좌석 유형"
            style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu">
            <AutoCompleteTextView
                android:id="@+id/actvSeatType"
                android:layout_width="match_parent" android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnStart"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="감시 시작" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 4: SrtFormFragment 작성**

```kotlin
// ui/home/SrtFormFragment.kt
package com.trainchecker.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.trainchecker.data.api.srt.SRT_STATION_CODE
import com.trainchecker.data.model.SeatType
import com.trainchecker.data.model.TrainType
import com.trainchecker.databinding.FragmentSrtFormBinding
import com.trainchecker.service.TicketWatcherService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SrtFormFragment : Fragment() {
    private var _b: FragmentSrtFormBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSrtFormBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val stations = SRT_STATION_CODE.keys.toList()
        val stationAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stations)
        b.actvDep.setAdapter(stationAdapter)
        b.actvArr.setAdapter(stationAdapter)

        val seatTypes = listOf("일반실" to SeatType.GENERAL, "특실" to SeatType.SPECIAL, "상관없음" to SeatType.ANY)
        b.actvSeatType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, seatTypes.map { it.first }))
        b.actvSeatType.setText("일반실", false)

        b.btnStart.setOnClickListener {
            val dep = b.actvDep.text.toString()
            val arr = b.actvArr.text.toString()
            val date = b.etDate.text.toString()
            val timeFrom = b.etTimeFrom.text.toString()
            if (dep.isEmpty() || arr.isEmpty() || date.length != 8 || timeFrom.length != 4) {
                Toast.makeText(requireContext(), "모든 필드를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val seatIdx = seatTypes.indexOfFirst { it.first == b.actvSeatType.text.toString() }
            val seatType = if (seatIdx >= 0) seatTypes[seatIdx].second else SeatType.GENERAL
            vm.startWatch(TrainType.SRT, dep, arr, date, timeFrom, b.etTimeTo.text.toString(), seatType) { jobId ->
                val svcIntent = Intent(requireContext(), TicketWatcherService::class.java).apply {
                    action = TicketWatcherService.ACTION_START
                    putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
                }
                requireContext().startForegroundService(svcIntent)
                Toast.makeText(requireContext(), "감시 시작됨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
```

- [ ] **Step 5: KtxFormFragment 작성**

KTX는 역 이름을 직접 문자열로 보내므로 자유 입력 허용.

```kotlin
// ui/home/KtxFormFragment.kt
package com.trainchecker.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.trainchecker.data.model.SeatType
import com.trainchecker.data.model.TrainType
import com.trainchecker.databinding.FragmentSrtFormBinding   // 레이아웃 재사용
import com.trainchecker.service.TicketWatcherService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KtxFormFragment : Fragment() {
    private var _b: FragmentSrtFormBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSrtFormBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val seatTypes = listOf("일반실" to SeatType.GENERAL, "특실" to SeatType.SPECIAL, "상관없음" to SeatType.ANY)
        b.actvSeatType.setAdapter(android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, seatTypes.map { it.first }))
        b.actvSeatType.setText("일반실", false)

        b.btnStart.setOnClickListener {
            val dep = b.actvDep.text.toString()
            val arr = b.actvArr.text.toString()
            val date = b.etDate.text.toString()
            val timeFrom = b.etTimeFrom.text.toString()
            if (dep.isEmpty() || arr.isEmpty() || date.length != 8 || timeFrom.length != 4) {
                Toast.makeText(requireContext(), "모든 필드를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val seatIdx = seatTypes.indexOfFirst { it.first == b.actvSeatType.text.toString() }
            val seatType = if (seatIdx >= 0) seatTypes[seatIdx].second else SeatType.GENERAL
            vm.startWatch(TrainType.KTX, dep, arr, date, timeFrom, b.etTimeTo.text.toString(), seatType) { jobId ->
                val svcIntent = Intent(requireContext(), TicketWatcherService::class.java).apply {
                    action = TicketWatcherService.ACTION_START
                    putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
                }
                requireContext().startForegroundService(svcIntent)
                Toast.makeText(requireContext(), "감시 시작됨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
```

- [ ] **Step 6: HomeFragment 작성**

```kotlin
// ui/home/HomeFragment.kt
package com.trainchecker.ui.home

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.trainchecker.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        b.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(pos: Int) = if (pos == 0) SrtFormFragment() else KtxFormFragment()
        }
        TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "SRT" else "KTX"
        }.attach()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
```

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: HomeFragment with SRT/KTX tabs and watch form"
```

---

## Task 11: WatchListFragment

**Files:**
- Create: `app/src/main/java/com/trainchecker/ui/watchlist/WatchListViewModel.kt`
- Create: `app/src/main/java/com/trainchecker/ui/watchlist/WatchJobAdapter.kt`
- Create: `app/src/main/java/com/trainchecker/ui/watchlist/WatchListFragment.kt`
- Create: `app/src/main/res/layout/fragment_watch_list.xml`
- Create: `app/src/main/res/layout/item_watch_job.xml`

- [ ] **Step 1: WatchListViewModel 작성**

```kotlin
// ui/watchlist/WatchListViewModel.kt
package com.trainchecker.ui.watchlist

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainchecker.data.db.WatchJobDao
import com.trainchecker.data.model.WatchJob
import com.trainchecker.data.model.WatchStatus
import com.trainchecker.service.TicketWatcherService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchListViewModel @Inject constructor(private val dao: WatchJobDao) : ViewModel() {
    val jobs = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancel(job: WatchJob, stopServiceIntent: (Long) -> Unit) {
        viewModelScope.launch {
            dao.updateStatus(job.id, WatchStatus.CANCELLED)
            if (job.status == WatchStatus.WATCHING) stopServiceIntent(job.id)
        }
    }

    fun delete(job: WatchJob) {
        viewModelScope.launch { dao.delete(job.id) }
    }
}
```

- [ ] **Step 2: item_watch_job.xml 작성**

```xml
<!-- res/layout/item_watch_job.xml -->
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardCornerRadius="8dp">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">
        <com.google.android.material.textview.MaterialTextView
            android:id="@+id/tvRoute"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceTitleSmall" />
        <com.google.android.material.textview.MaterialTextView
            android:id="@+id/tvDetail"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall" />
        <com.google.android.material.textview.MaterialTextView
            android:id="@+id/tvStatus"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="?attr/textAppearanceLabelSmall" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: WatchJobAdapter 작성**

```kotlin
// ui/watchlist/WatchJobAdapter.kt
package com.trainchecker.ui.watchlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.trainchecker.data.model.WatchJob
import com.trainchecker.data.model.WatchStatus
import com.trainchecker.databinding.ItemWatchJobBinding

class WatchJobAdapter(
    private val onCancel: (WatchJob) -> Unit,
) : ListAdapter<WatchJob, WatchJobAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemWatchJobBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(job: WatchJob) {
            b.tvRoute.text = "${job.trainType} ${job.depStation} → ${job.arrStation}"
            b.tvDetail.text = "${job.date} ${job.timeFrom}~${job.timeTo.ifEmpty { "제한없음" }} ${job.seatType.name}"
            b.tvStatus.text = when (job.status) {
                WatchStatus.WATCHING -> "⏳ 감시 중"
                WatchStatus.SUCCESS -> "✅ 예약 완료 (${job.reservationNumber})"
                WatchStatus.FAILED -> "❌ 실패"
                WatchStatus.CANCELLED -> "🚫 취소됨"
            }
            b.root.setOnLongClickListener {
                if (job.status == WatchStatus.WATCHING) onCancel(job)
                true
            }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        ItemWatchJobBinding.inflate(LayoutInflater.from(p.context), p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WatchJob>() {
            override fun areItemsTheSame(a: WatchJob, b: WatchJob) = a.id == b.id
            override fun areContentsTheSame(a: WatchJob, b: WatchJob) = a == b
        }
    }
}
```

- [ ] **Step 4: fragment_watch_list.xml 작성**

```xml
<!-- res/layout/fragment_watch_list.xml -->
<?xml version="1.0" encoding="utf-8"?>
<androidx.recyclerview.widget.RecyclerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/recyclerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />
```

- [ ] **Step 5: WatchListFragment 작성**

```kotlin
// ui/watchlist/WatchListFragment.kt
package com.trainchecker.ui.watchlist

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.trainchecker.databinding.FragmentWatchListBinding
import com.trainchecker.service.TicketWatcherService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WatchListFragment : Fragment() {
    private var _b: FragmentWatchListBinding? = null
    private val b get() = _b!!
    private val vm: WatchListViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentWatchListBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val adapter = WatchJobAdapter { job ->
            vm.cancel(job) { jobId ->
                requireContext().startService(Intent(requireContext(), TicketWatcherService::class.java).apply {
                    action = TicketWatcherService.ACTION_STOP
                    putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
                })
            }
        }
        b.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.jobs.collect { adapter.submitList(it) }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
```

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: WatchListFragment with job status display"
```

---

## Task 12: MainActivity + Navigation

**Files:**
- Create: `app/src/main/java/com/trainchecker/MainActivity.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/navigation/nav_graph.xml`
- Create: `app/src/main/res/menu/bottom_nav_menu.xml`

- [ ] **Step 1: bottom_nav_menu.xml 작성**

```xml
<!-- res/menu/bottom_nav_menu.xml -->
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/homeFragment" android:title="홈" android:icon="@drawable/ic_home" />
    <item android:id="@+id/watchListFragment" android:title="감시목록" android:icon="@drawable/ic_list" />
    <item android:id="@+id/settingsFragment" android:title="설정" android:icon="@drawable/ic_settings" />
</menu>
```

> **Note:** ic_home, ic_list, ic_settings — Android Studio Vector Asset으로 각각 "home", "list", "settings" 추가.

- [ ] **Step 2: nav_graph.xml 작성**

```xml
<!-- res/navigation/nav_graph.xml -->
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/homeFragment">

    <fragment android:id="@+id/homeFragment"
        android:name="com.trainchecker.ui.home.HomeFragment"
        android:label="홈" />
    <fragment android:id="@+id/watchListFragment"
        android:name="com.trainchecker.ui.watchlist.WatchListFragment"
        android:label="감시목록" />
    <fragment android:id="@+id/settingsFragment"
        android:name="com.trainchecker.ui.settings.SettingsFragment"
        android:label="설정" />
</navigation>
```

- [ ] **Step 3: activity_main.xml 작성**

```xml
<!-- res/layout/activity_main.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/navHostFragment"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_graph" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:menu="@menu/bottom_nav_menu" />
</LinearLayout>
```

- [ ] **Step 4: MainActivity 작성**

```kotlin
// MainActivity.kt
package com.trainchecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.trainchecker.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 결과 무시 — 알림 없이도 동작 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        b.bottomNav.setupWithNavController(navHost.navController)
    }
}
```

- [ ] **Step 5: 최종 빌드**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 기기/에뮬레이터 설치 후 수동 테스트**

```bash
./gradlew installDebug
```

체크리스트:
- [ ] 앱 실행 — 홈/감시목록/설정 탭 전환
- [ ] 설정 탭 — SRT ID/PW 입력 후 저장, 재시작 후 유지 확인
- [ ] 홈 SRT 탭 — 출발역·도착역 드롭다운 동작 확인
- [ ] 감시 시작 버튼 — 상단 알림 "감시 중" 표시 확인
- [ ] 감시목록 탭 — 방금 추가한 작업 표시 확인

- [ ] **Step 7: 최종 Commit**

```bash
git add -A && git commit -m "feat: MainActivity with Bottom Navigation complete"
```

---

## 자기검토

**스펙 커버리지:**
- Bottom Nav (홈·감시목록·설정) ✅ Task 12
- Foreground Service 폴링 ✅ Task 8
- 취소표 발견 시 자동예약 ✅ Task 8 `attemptReserve()`
- 푸시 알림 ✅ Task 7
- SRT login/search/reserve ✅ Task 5
- KTX login/search/reserve ✅ Task 6
- NetFunnel ✅ Task 4
- KTX AES 암호화 ✅ Task 6
- EncryptedSharedPreferences ✅ Task 3
- Room DB WatchJob ✅ Task 2
- 폴링 간격 15초 기본값 ✅ Task 3 `CredentialStore`

**주의사항:**
- KtxCodeResponse의 `app·login·cphd` 필드명에 점(·)이 포함되어 Gson 역직렬화 시 커스텀 처리 필요. 실제 JSON 키는 `app.login.cphd`. `@SerializedName("app.login.cphd")`로 수정 필요.
- `ic_train`, `ic_home`, `ic_list`, `ic_settings` drawable은 Vector Asset으로 직접 생성 필요.
- Room 타입 컨버터: `TrainType`, `SeatType`, `WatchStatus` enum은 Room이 자동 처리하지 않으므로 `@TypeConverters` 필요.
