package com.youme.memoria.Encoder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EncodeService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    private fun createChannel() {
        val channelId = "encode_channel"
        val channel = NotificationChannel(
            channelId,
            "Encoding Images",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val uris = intent?.getStringArrayListExtra("uris") ?: arrayListOf()

        startForeground(1, createNotification(0, uris.size))


        if (uris.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {

            val repo = PhotoRepository(this@EncodeService)
            repo.initializeModel()

            uris.forEachIndexed { index, uri ->
                val embedding = repo.encodeImage(this@EncodeService, uri.toUri())
                repo.saveEmbedding(uri, embedding)
                updateNotification(index + 1, uris.size)
            }

            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotification(progress: Int, max: Int): Notification {
        val channelId = "encode_channel"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Encoding images")
            .setContentText(if (max > 0) "$progress / $max" else "Initializing...")
            .setSmallIcon(R.drawable.baseline_image_24)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Int, max: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        NotificationManagerCompat.from(this).notify(1, createNotification(progress, max))
    }
}