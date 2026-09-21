package com.youme.inkdex.roomCach

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
@Entity(
    tableName = "album_photo",
    primaryKeys = ["albumId", "uri"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(entity = PhotoEntity::class, parentColumns = ["uri"], childColumns = ["uri"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("albumId"), Index("uri")]
)
data class AlbumPhotoEntity(
    val albumId: String,
    val uri: String
)
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val centroidEmbedding: ByteArray,
    val autoUpdate : Boolean = true
)


fun FloatArray.toByteArray(): ByteArray {
    val buf = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buf.putFloat(it) }
    return buf.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    if (isEmpty()) return FloatArray(0)
    val buf = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buf.getFloat() }
}