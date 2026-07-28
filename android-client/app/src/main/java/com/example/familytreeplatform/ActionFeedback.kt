package com.example.familytreeplatform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ActionFeedbackKind { SUCCESS, WARNING, ERROR, INFO }

data class ActionFeedback(
    val kind: ActionFeedbackKind,
    val message: String
)

internal fun actionFeedbackForHttp(
    method: String,
    path: String,
    statusCode: Int
): ActionFeedback? {
    if (method.uppercase() !in setOf("POST", "PATCH", "DELETE")) return null
    if (
        path.startsWith("/auth") ||
        path.startsWith("/app-compatibility") ||
        path.startsWith("/notifications")
    ) {
        return null
    }
    if (statusCode >= 400) {
        return when (statusCode) {
            409 -> ActionFeedback(
                ActionFeedbackKind.WARNING,
                "Belum tersimpan — data berubah atau masih memiliki konflik."
            )
            403 -> ActionFeedback(
                ActionFeedbackKind.WARNING,
                "Tidak diizinkan — periksa kembali peran atau akses Anda."
            )
            400, 422 -> ActionFeedback(
                ActionFeedbackKind.ERROR,
                "Belum tersimpan — periksa kembali isian dan aturan keluarga."
            )
            else -> ActionFeedback(
                ActionFeedbackKind.ERROR,
                "Gagal disimpan — server belum dapat menyelesaikan tindakan."
            )
        }
    }
    val message = when {
        path.contains("/comments") -> "Berhasil — komentar telah dikirim."
        path.contains("/proposals/approve") ||
            path.contains("/proposals/reject") ->
            "Berhasil — keputusan usulan telah disimpan."
        path.contains("/relationships") || path.contains("/parent-child") ->
            "Berhasil — hubungan keluarga telah disimpan."
        path.contains("/persons") -> "Berhasil — data person telah disimpan."
        path.contains("/invitations") -> "Berhasil — undangan telah diperbarui."
        path.contains("/claims") -> "Berhasil — status validasi telah diperbarui."
        else -> "Berhasil — perubahan telah disimpan."
    }
    return ActionFeedback(ActionFeedbackKind.SUCCESS, message)
}

object ActionFeedbackStore {
    private val mutableEvents = MutableSharedFlow<ActionFeedback>(
        extraBufferCapacity = 32
    )
    val events = mutableEvents.asSharedFlow()

    private var lastKey: String? = null
    private var lastAt: Long = 0L

    @Synchronized
    fun publish(feedback: ActionFeedback) {
        val now = System.currentTimeMillis()
        val key = "${feedback.kind}:${feedback.message}"
        if (key == lastKey && now - lastAt < 1_500L) return
        lastKey = key
        lastAt = now
        mutableEvents.tryEmit(feedback)
    }

    fun waitingForSync() {
        publish(
            ActionFeedback(
                ActionFeedbackKind.WARNING,
                "Menunggu sinkronisasi — perubahan aman tersimpan di perangkat."
            )
        )
    }
}
