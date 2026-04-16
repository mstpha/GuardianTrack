package com.guardian.track.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.*
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.guardian.track.R
import com.guardian.track.data.local.PreferencesManager
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.ui.MainActivity
import com.guardian.track.util.NotificationHelper
import com.guardian.track.util.SmsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class SurveillanceService : Service() {

    @Inject lateinit var incidentRepository: IncidentRepository
    @Inject lateinit var fusedLocation: FusedLocationProviderClient
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var smsHelper: SmsHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler

    @Volatile private var fallThreshold = 15.0f
    private var freeFallStartTime = 0L
    private var inFreeFall = false

    companion object {
        const val CHANNEL_ID = "guardian_surveillance"
        const val NOTIFICATION_ID = 1
        const val FREEFALL_MAGNITUDE = 3.0f
        const val FREEFALL_DURATION_MS = 100L
        const val IMPACT_WINDOW_MS = 200L
        private const val TAG = "SurveillanceService"

        fun startService(context: Context) =
            context.startForegroundService(Intent(context, SurveillanceService::class.java))

        fun stopService(context: Context) =
            context.stopService(Intent(context, SurveillanceService::class.java))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch {
            preferencesManager.fallThreshold.collect { newThreshold ->
                fallThreshold = newThreshold
            }
        }
        setupAccelerometer()
    }

    private fun setupAccelerometer() {
        sensorThread = HandlerThread("SensorThread").apply { start() }
        sensorHandler = Handler(sensorThread.looper)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(
                sensorEventListener, it,
                SensorManager.SENSOR_DELAY_GAME,
                sensorHandler
            )
        } ?: Log.w(TAG, "No accelerometer found on device")
    }

    private val sensorEventListener = object : SensorEventListener {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onSensorChanged(event: SensorEvent) {
            val magnitude = sqrt(
                event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
            )
            val now = System.currentTimeMillis()

            if (magnitude < FREEFALL_MAGNITUDE) {
                if (!inFreeFall) {
                    inFreeFall = true
                    freeFallStartTime = now
                }
                return
            }

            if (inFreeFall) {
                val elapsed = now - freeFallStartTime
                if (magnitude > fallThreshold &&
                    elapsed >= FREEFALL_DURATION_MS &&
                    elapsed <= FREEFALL_DURATION_MS + IMPACT_WINDOW_MS
                ) {
                    onFallDetected()
                }
                inFreeFall = false
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun onFallDetected() {
        Log.i(TAG, "FALL DETECTED")
        serviceScope.launch(Dispatchers.IO) {
            val (lat, lon) = getLastLocation()
            incidentRepository.saveAndSync("FALL", lat, lon)
            val phone = preferencesManager.emergencyNumber.first()
            val simMode = preferencesManager.smsSimulationMode.first()
            smsHelper.sendAlert(phone,lat,lon, "FALL", simMode, this@SurveillanceService)
            NotificationHelper.showIncidentNotification(
                this@SurveillanceService,
                "Fall Detected",
                "A fall was detected and an alert has been sent."
            )
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getLastLocation(): Pair<Double, Double> =
        try {
            val loc = fusedLocation.lastLocation.await()
            Pair(loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(sensorEventListener)
        sensorThread.quitSafely()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Surveillance", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "GuardianTrack active monitoring" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuardianTrack Active")
            .setContentText("Monitoring for falls and alerts")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }
}