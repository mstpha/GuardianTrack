// com/guardian/track/data/remote/dto/IncidentDto.kt
package com.guardian.track.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Used when INSERTing — no id yet, server generates it
@Serializable
data class IncidentInsertDto(
    val id: Long,
    val timestamp: Long,
    val type: String,
    val latitude: Double,
    val longitude: Double
)

// Used when SELECTing — server returns the generated id too
@Serializable
data class IncidentFetchDto(
    val id: Long,
    val timestamp: Long,
    val type: String,
    val latitude: Double,
    val longitude: Double
)