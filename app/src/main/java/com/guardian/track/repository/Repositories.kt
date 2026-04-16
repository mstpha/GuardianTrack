package com.guardian.track.repository

import android.content.Context
import android.util.Log
import androidx.work.*
import com.guardian.track.data.local.dao.EmergencyContactDao
import com.guardian.track.data.local.dao.IncidentDao
import com.guardian.track.data.local.entity.EmergencyContactEntity
import com.guardian.track.data.local.entity.IncidentEntity
import com.guardian.track.data.remote.SupabaseProvider
import android.provider.Settings
import com.guardian.track.data.remote.dto.IncidentFetchDto
import com.guardian.track.data.remote.dto.IncidentInsertDto
import com.guardian.track.model.*
import com.guardian.track.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Returning
import io.github.jan.supabase.postgrest.result.PostgrestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository — single source of truth for incident data.
 *
 * The Repository pattern hides the complexity of "where does data come from?"
 * from the ViewModel. The ViewModel says "give me incidents" — it doesn't
 * care whether they come from Room, Retrofit, or a cache.
 *
 * OFFLINE-FIRST strategy:
 * 1. Always save to Room first (so data is never lost).
 * 2. Try Retrofit immediately if network is available.
 * 3. If Retrofit fails, schedule WorkManager to retry when connected.
 */
@Singleton
class IncidentRepository @Inject constructor(
    private val incidentDao: IncidentDao,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) {
    /**
     * Returns a Flow of domain models.
     * The .map{} transforms List<Entity> → List<Incident> before the ViewModel sees it.
     * Room emits automatically whenever the table changes.
     */

    private val deviceId: String
        get() = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

    fun getAllIncidents(): Flow<List<Incident>> =
        incidentDao.getAllIncidents().map { entities ->
            entities.map { entity ->
                Incident(
                    id = entity.id,
                    formattedDate = entity.timestamp.toFormattedDate(),
                    formattedTime = entity.timestamp.toFormattedTime(),
                    type = entity.type,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    isSynced = entity.isSynced
                )
            }
        }

    /**
     * Saves an incident then attempts immediate sync.
     * If sync fails → schedules WorkManager for later.
     */
    suspend fun saveAndSync(type: String, latitude: Double, longitude: Double) {
        // 1. Room first — data is never lost even if network is down
        val entity = IncidentEntity(
            timestamp = System.currentTimeMillis(),
            type = type,
            latitude = latitude,
            longitude = longitude,
            isSynced = false
        )
        val localId = incidentDao.insertIncident(entity)

        // 2. Try Supabase immediately
        tryPushToSupabase(localId, entity.timestamp, type, latitude, longitude)
    }

    // Called by SyncWorker — retries all rows that never made it to Supabase
    suspend fun syncPendingIncidents() {
        incidentDao.getUnsyncedIncidents().forEach { entity ->
            tryPushToSupabase(
                entity.id, entity.timestamp,
                entity.type, entity.latitude, entity.longitude
            )
        }
    }

    suspend fun fetchRemoteAndMerge() {
        try {
            val remote = SupabaseProvider.client
                .postgrest["incidents"]
                .select {
                    filter {
                        eq("device_id", deviceId)
                    }
                }
                .decodeList<IncidentFetchDto>()

            remote.forEach { dto ->
                val exists = incidentDao.getByTimestampAndType(dto.timestamp, dto.type)
                if (exists == null) {
                    incidentDao.insertIncident(
                        IncidentEntity(
                            timestamp = dto.timestamp,
                            type = dto.type,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            isSynced = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("IncidentRepo", "fetchRemoteAndMerge failed: ${e.message}")
        }
    }

    suspend fun deleteIncident(id: Long) {
        try {
            val result = SupabaseProvider.client.postgrest["incidents"].delete {
                filter { eq("id", id) }
            }
            incidentDao.deleteById(id)
        } catch (e: Exception) {
            Log.e("IncidentRepo", "Failed to delete incident $id from Supabase: ${e.message}")
        }
    }

    suspend fun getAllForExport() = incidentDao.getAllIncidentsOnce()
    private suspend fun tryPushToSupabase(
        localId: Long,
        timestamp: Long,
        type: String,
        latitude: Double,
        longitude: Double
    ) {
        try {
            SupabaseProvider.client
                .postgrest["incidents"]
                .insert(
                    IncidentInsertDto(
                        id = localId,
                        timestamp = timestamp,
                        type = type,
                        latitude = latitude,
                        longitude = longitude
                    )
                )
            incidentDao.markAsSynced(localId)
            Log.d("IncidentRepo", "Pushed incident $localId to Supabase")
        } catch (e: Exception) {
            Log.d("IncidentRepo", "Push failed for $localId, WorkManager will retry: ${e.message}")
            scheduleSyncWork()
        }
    }

    private fun scheduleSyncWork() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            "sync_incidents",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}



/**
 * Repository for emergency contacts.
 * Simple CRUD — no network sync needed for contacts.
 */
@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: EmergencyContactDao
) {
    fun getAllContacts(): Flow<List<EmergencyContactEntity>> =
        contactDao.getAllContacts()

    suspend fun addContact(name: String, phone: String) {
        contactDao.insertContact(EmergencyContactEntity(name = name, phoneNumber = phone))
    }

    suspend fun deleteContact(contact: EmergencyContactEntity) =
        contactDao.deleteContact(contact)
}
