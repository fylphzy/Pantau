package com.fylphzy.pantau

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<UserResponse> = emptyList(),
    @SerializedName("recycle") val recycle: List<UserResponse> = emptyList(),
    @SerializedName("notify") val notify: List<UserResponse> = emptyList()
)
