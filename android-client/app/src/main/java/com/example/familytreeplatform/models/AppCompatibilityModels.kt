package com.example.familytreeplatform.models

data class AppCompatibilityResponse(
    val status: String,
    val blocking: Boolean,
    val message: String,
    val channel: String,
    val minimumSupportedVersionCode: Int,
    val latestVersionCode: Int,
    val backendApiContractVersion: Int,
    val updateUrl: String? = null,
    val policyUpdatedAt: String? = null,
    val checkedAt: String
)

enum class CompatibilityGateStatus {
    CHECKING,
    COMPATIBLE,
    UPDATE_AVAILABLE,
    BLOCKED,
    UNAVAILABLE
}

data class AppCompatibilityState(
    val status: CompatibilityGateStatus = CompatibilityGateStatus.CHECKING,
    val response: AppCompatibilityResponse? = null,
    val usingCachedPolicy: Boolean = false,
    val updateWarningAcknowledged: Boolean = false,
    val error: String? = null
)
