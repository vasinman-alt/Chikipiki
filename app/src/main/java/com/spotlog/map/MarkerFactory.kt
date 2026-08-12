package com.spotlog.map

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.spotlog.R

object MarkerFactory {

    @Volatile
    private var cachedBitmap: Bitmap? = null

    fun getBitmap(context: Context): Bitmap {
        cachedBitmap?.let { return it }
        synchronized(this) {
            cachedBitmap?.let { return it }
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)
                ?: throw IllegalStateException("Marker drawable not found")
            val bitmap = drawable.toBitmap(
                width = drawable.intrinsicWidth.coerceAtLeast(1),
                height = drawable.intrinsicHeight.coerceAtLeast(1)
            )
            cachedBitmap = bitmap
            return bitmap
        }
    }
}