package io.github.columnwise.trainchecker.di

import io.github.columnwise.trainchecker.data.api.srt.SrtApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
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
    fun provideSrtApiService(retrofit: Retrofit): SrtApiService =
        retrofit.create(SrtApiService::class.java)
}
