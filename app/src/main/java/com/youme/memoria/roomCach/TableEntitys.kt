package com.youme.inkdex.roomCach

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(tableName = "photo")
data class PhotoEntity(
    @PrimaryKey() val uri: String,
    @ColumnInfo(name = "embedding") val embedding: ByteArray,
    val width: Int,
    val height: Int,
    val dateTaken: Long? = 0,
    val folderPath: String? = "",
    val cameraMake: String? = null,
    val cameraModel: String? = null
)
fun FloatArray.toByteArray(): ByteArray {
    val buf = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buf.putFloat(it) }
    return buf.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buf = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buf.getFloat() }
}