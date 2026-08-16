package com.youme.memoria.Gallery

import android.database.ContentObserver
import android.os.Handler
import android.util.Log

class GalleryObserver(
    handler: Handler
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d("GalleryObserver", "MediaStore changed")
    }

}