package com.example.familytreeplatform.ui.branding

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.familytreeplatform.R

object TredhahBrand {
    const val NAME = "TRêdhAH"
    const val TAGLINE = "Merangkai jejak, menyatukan trah"
}

@Composable
fun TredhahLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Logo ${TredhahBrand.NAME}"
) {
    Image(
        painter = painterResource(R.drawable.tredhah_mark_hd),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
