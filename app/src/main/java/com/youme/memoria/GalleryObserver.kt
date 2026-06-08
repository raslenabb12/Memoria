package com.youme.memoria

import android.content.ContentUris
import android.database.ContentObserver
import android.os.Handler
import android.provider.MediaStore
import android.util.Log

class GalleryObserver(
    handler: Handler
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d("GalleryObserver", "MediaStore changed")
    }

}