package com.example.familytreeplatform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.ProfilePhotoItem

internal fun accountProfilePhoto(
    claim: ClaimReviewItem?,
    photos: Map<String, ProfilePhotoItem>
): ProfilePhotoItem? = claim?.personId?.let(photos::get)

@Composable
fun ProfilePhotoAvatar(
    photo: ProfilePhotoItem?,
    fallbackText: String,
    modifier: Modifier = Modifier
) {
    val fallback: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(
                fallbackText,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Box(modifier = modifier.clip(CircleShape)) {
        if (photo == null) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.url)
                    .memoryCacheKey(photo.mediaId)
                    .diskCacheKey(photo.mediaId)
                    .size(256, 256)
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto profil",
                contentScale = ContentScale.Crop,
                loading = { fallback() },
                error = { fallback() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
