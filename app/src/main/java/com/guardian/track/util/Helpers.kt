package com.guardian.track.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.guardian.track.R
import javax.inject.Inject
import javax.inject.Singleton

object NotificationHelper {

    private const val CHANNEL_ID = "guardian_incidents"
    const val EXTRA_NOTIF_ID = "notif_id"
    private var notifId = 100
    private var ringtone: Ringtone? = null

    fun stopAlarm(context: Context, id: Int) {
        ringtone?.stop()
        ringtone = null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun showIncidentNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incident Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        val id = notifId++

        val intent = Intent(context, StopAlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIF_ID, id)
        }

        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            id + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Stop Alarm", stopPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .build()

        if (ringtone?.isPlaying != true) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.isLooping = true
            ringtone?.play()

            android.os.Handler(context.mainLooper).postDelayed({
                ringtone?.stop()
            }, 60_000)
        }

        manager.notify(id, notification)
    }
}

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getIntExtra(NotificationHelper.EXTRA_NOTIF_ID, -1) ?: return
        if (id != -1) NotificationHelper.stopAlarm(context, id)
    }
}

@Singleton
class SmsHelper @Inject constructor() {

    @RequiresApi(Build.VERSION_CODES.P)
    fun sendAlert(
        phoneNumber: String,
        lat:Double,
        lon:Double,
        incidentType: String,
        simulationMode: Boolean,
        context: Context
    ) {
        val message = buildMessage(incidentType,lat,lon)

        if (simulationMode) {
            Log.i("SmsHelper", "SIMULATION -> $phoneNumber: $message")
            NotificationHelper.showIncidentNotification(
                context,
                "SMS Simulated",
                "Would send to $phoneNumber"
            )
            return
        }

        if (phoneNumber.isBlank()) return

        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } catch (e: Exception) {
            Log.e("SmsHelper", "SMS failed: ${e.message}")
        }
    }

    private fun buildMessage(type: String, lat: Double, lon: Double): String {
        val time = java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "GuardianTrack ALERT: $type at $time. My location: https://www.google.com/maps?q=$lat,$lon"    }
}