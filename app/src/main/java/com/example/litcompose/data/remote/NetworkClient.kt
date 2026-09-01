package com.example.litcompose.data.remote

import com.example.litcompose.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkClient {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun okHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    fun itunesApi(): ItunesApi {
        return Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ItunesApi::class.java)
    }

    fun cocoApi(): CoCoApi {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request =
                        chain.request()
                            .newBuilder()
                            .header("Referer", "https://cocodownloader.markqq.com/")
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
                            )
                            .build()
                    chain.proceed(request)
                }.build()
        return Retrofit.Builder()
            .baseUrl("https://cocodownloader.markqq.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CoCoApi::class.java)
    }
}

