package com.fylphzy.pantau

import com.google.gson.annotations.SerializedName

data class BasicResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null
)
