package com.example.familytreeplatform.ui

import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.ProfilePhotoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePhotoAvatarTest {
    private val photo = ProfilePhotoItem(
        personId = "person-1",
        mediaId = "media-1",
        url = "https://storage.example.test/profile.jpg",
        expiresIn = 300
    )

    @Test
    fun `pending claim uses its person photo in the account frame`() {
        val claim = claim(status = "PENDING")

        assertEquals(photo, accountProfilePhoto(claim, mapOf(photo.personId to photo)))
    }

    @Test
    fun `verified claim continues to use its person photo`() {
        val claim = claim(status = "VERIFIED")

        assertEquals(photo, accountProfilePhoto(claim, mapOf(photo.personId to photo)))
    }

    @Test
    fun `missing claim or photo falls back to initials`() {
        assertNull(accountProfilePhoto(null, mapOf(photo.personId to photo)))
        assertNull(accountProfilePhoto(claim(status = "PENDING"), emptyMap()))
    }

    private fun claim(status: String) = ClaimReviewItem(
        claimId = "claim-1",
        spaceId = "space-1",
        userId = "user-1",
        personId = photo.personId,
        status = status,
        requestedAt = "2026-08-02T00:00:00Z"
    )
}
