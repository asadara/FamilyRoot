package com.example.familytreeplatform.feature.persondetail

import com.example.familytreeplatform.data.local.OfflineMutationStatus
import com.example.familytreeplatform.data.local.OfflineMutationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonDetailScreenTest {

    @Test
    fun `relationship dates require a real ISO calendar date`() {
        assertTrue(isValidPersonDate("2026-08-03"))
        assertFalse(isValidPersonDate("2026-02-30"))
        assertFalse(isValidPersonDate("03-08-2026"))
    }
    @Test
    fun `historical date picker includes eighteenth century ancestors`() {
        assertEquals(1600, PERSON_DATE_MIN_YEAR)
        assertTrue(1770 >= PERSON_DATE_MIN_YEAR)
    }

    @Test
    fun `profile helpers translate legacy backend values`() {
        assertEquals("BS", personProfileInitials("Budi Santoso"))
        assertEquals("Wanita", genderLabel("FEMALE"))
        assertEquals("Hubungan pasangan", mutationTypeLabel(OfflineMutationType.ADD_SPOUSE))
        assertEquals("Catatan sumber", mutationTypeLabel(OfflineMutationType.CREATE_SOURCE))
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

    @Test
    fun `complete profile requires clear names and valid life dates`() {
        val valid = PersonProfileEditInput(
            fullName = "Raden Ajeng Kartini",
            nickName = "Kartini",
            gender = "FEMALE",
            birthDate = "1879-04-21",
            birthPlace = "Jepara",
            lifeStatus = "DECEASED",
            deceasedAt = "1904-09-17",
            deathPlace = "Rembang",
            notes = ""
        )

        assertNull(profileEditValidationError(valid))
        assertEquals(
            "Nama lengkap dan nama panggilan wajib diisi.",
            profileEditValidationError(valid.copy(nickName = ""))
        )
        assertEquals(
            "Tanggal meninggal tidak boleh sebelum tanggal lahir.",
            profileEditValidationError(valid.copy(deceasedAt = "1800-01-01"))
        )
    }

    @Test
    fun `person deletion actions follow workspace role`() {
        assertEquals(PersonDeletionAction.DELETE, personDeletionAction("OWNER"))
        assertEquals(PersonDeletionAction.DELETE, personDeletionAction("ADMIN"))
        assertEquals(PersonDeletionAction.REQUEST, personDeletionAction("EDITOR"))
        assertNull(personDeletionAction("VIEWER"))
    }
}
