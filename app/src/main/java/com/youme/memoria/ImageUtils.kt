package com.youme.memoria

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri

object ImageSizeUtil {
    fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else null
        } catch (e: Exception) {
            null
        }
    }
}