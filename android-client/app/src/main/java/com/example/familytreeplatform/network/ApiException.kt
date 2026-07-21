package com.example.familytreeplatform.network

import java.io.IOException

/** Structured server failure retained so UI can distinguish validation, auth, and outage errors. */
class ApiException(
    val statusCode: Int,
    val errorCode: String?,
    val serverMessage: String
) : IOException(serverMessage) {
    override val message: String
        get() = buildString {
            append("HTTP ").append(statusCode)
            errorCode?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
            append(": ").append(serverMessage)
        }
}
