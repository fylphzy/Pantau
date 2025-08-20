package com.fylphzy.pantau

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

@Suppress("Unused") // mencegah peringatan di IDE saat indexing/build sementara
interface ApiService {

    // login user
    @FormUrlEncoded
    @POST("login.php")
    fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<BasicResponse>

    // register user
    @FormUrlEncoded
    @POST("register.php")
    fun register(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("email") email: String
    ): Call<BasicResponse>

    // update user data (flexible — only send needed fields)
    @FormUrlEncoded
    @POST("update.php")
    fun updateData(@FieldMap fields: Map<String, @JvmSuppressWildcards Any?>): Call<BasicResponse>

    // polling user data
    @GET("read.php")
    fun getUser(
        @Query("username") username: String
    ): Call<ApiResponse>
}
