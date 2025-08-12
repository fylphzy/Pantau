package com.fylphzy.pantau

import retrofit2.Call
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @GET("read.php")
    fun getUser(@Query("username") username: String): Call<ApiResponse>

    @FormUrlEncoded
    @POST("update.php")
    fun updateData(@FieldMap fields: Map<String, @JvmSuppressWildcards Any?>): Call<Void>
}
