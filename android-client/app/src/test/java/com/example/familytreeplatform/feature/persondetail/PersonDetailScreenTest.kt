package com.example.familytreeplatform.feature.persondetail

import com.example.familytreeplatform.data.local.OfflineMutationStatus
import com.example.familytreeplatform.data.local.OfflineMutationType
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonDetailScreenTest {
    @Test
    fun `profile helpers translate legacy backend values`() {
        assertEquals("BS", personProfileInitials("Budi Santoso"))
        assertEquals("Wanita", genderLabel("FEMALE"))
        assertEquals("Hubungan pasangan", mutationTypeLabel(OfflineMutationType.ADD_SPOUSE))
        assertEquals("Perlu menyelesaikan konflik", syncStatusLabel(OfflineMutationStatus.CONFLICT))
        assertEquals("Menunggu verifikasi", claimStatusLabel("PENDING"))
        assertEquals("Adopsi", relationshipMetaLabel("ADOPTIVE"))
        assertEquals("Tiri", relationshipMetaLabel("STEP"))
    }

    @Test
    fun `technical messages are converted to user language`() {
        assertEquals(
            "Perubahan disimpan di perangkat dan masuk antrean sinkronisasi.",
            personMessage("Profile saved locally; sync queued")
        )
        assertEquals(
            "Data belum dapat diperbarui. Periksa koneksi lalu coba kembali.",
            personErrorMessage("unexpected end of stream on http://127.0.0.1:3001")
        )
        assertEquals(
            "Hubungan anak berhasil ditentukan dan menunggu sinkronisasi.",
            personMessage("Child relationship saved (adoptive) locally; sync queued")
        )
    }
}
