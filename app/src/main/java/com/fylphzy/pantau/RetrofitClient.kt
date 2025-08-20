package com.fylphzy.pantau

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Ganti BASE_URL sesuai endpoint backend Anda (harus ada trailing slash)
    private const val BASE_URL = "http://gpsangkutan.kir.my.id/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
