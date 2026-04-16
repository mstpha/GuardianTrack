package com.guardian.track.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.service.SurveillanceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * SyncWorker — runs when network becomes available to upload pending incidents.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: IncidentRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            repository.syncPendingIncidents()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

/**
 * BatteryCriticalWorker — saves a BATTERY incident to Room.
 */
@HiltWorker
class BatteryCriticalWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val incidentRepository: IncidentRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        SurveillanceService.createNotificationChannel(applicationContext)
        return try {
            incidentRepository.saveAndSync("BATTERY", 0.0, 0.0)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(3, createNotification())

    private fun createNotification() =
        android.app.Notification.Builder(applicationContext, SurveillanceService.CHANNEL_ID)
            .setContentTitle("Recording battery incident...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .build()
}

/**
 * BootSurveillanceWorker — restarts the SurveillanceService after device boot.
 */
@HiltWorker
class BootSurveillanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Essential: Create channel before starting service/notification
        SurveillanceService.createNotificationChannel(applicationContext)
        
        return try {
            SurveillanceService.startService(applicationContext)
            Log.i("BootWorker", "SurveillanceService restarted after boot")
            Result.success()
        } catch (e: Exception) {
            Log.e("BootWorker", "Failed to start service: ${e.message}")
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        SurveillanceService.createNotificationChannel(applicationContext)
        return ForegroundInfo(4, createNotification())
    }

    private fun createNotification() =
        android.app.Notification.Builder(applicationContext, SurveillanceService.CHANNEL_ID)
            .setContentTitle("GuardianTrack starting...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .build()
}
