package io.matin.filedownloader.data

import com.google.gson.annotations.SerializedName


data class DownloadMetadata(
    @SerializedName("fileName") val fileName: String,
    @SerializedName("fileLength") val fileLength: Long,
    @SerializedName("checkSum") val checkSum: String
)
