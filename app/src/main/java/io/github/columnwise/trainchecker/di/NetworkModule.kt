package io.github.columnwise.trainchecker.di

import io.github.columnwise.trainchecker.data.api.ktx.KtxApiService
import io.github.columnwise.trainchecker.data.api.srt.SrtApiService
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
