package com.fylphzy.pantau

import com.google.gson.annotations.SerializedName

data class UserResponse(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("whatsapp") val whatsapp: String? = null,
    @SerializedName("la") val la: Double? = null,
    @SerializedName("lo") val lo: Double? = null,
    @SerializedName("emr") val emr: Int? = null,
    @SerializedName("emr_timestamp") val emrTimestamp: String? = null,
    @SerializedName("conf_status") val confStatus: Int? = null,
    @SerializedName("emr_desc") val emrDesc: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)
