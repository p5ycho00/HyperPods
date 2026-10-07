package com.hyperpods.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bounded icon cache.
 *
 * Adaptive icons rasterise at their intrinsic size (often 432×432), which is ~750 KB each as
 * ARGB_8888; keeping every icon ever scrolled past pushed the process through repeated GC and
 * showed up as scroll jank. Icons are now rasterised at the size they are drawn, and the cache is
 * evicted by total byte size.
 */
private object IconCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val entries = LinkedHashMap<String, ImageBitmap>(0, 0.75f, true)
    private var bytes = 0

    @Synchronized
    operator fun get(key: String): ImageBitmap? = entries[key]

    @Synchronized
    fun put(key: String, value: ImageBitmap) {
        entries[key]?.let { bytes -= it.byteSize() }
        entries[key] = value
        bytes += value.byteSize()
        val iterator = entries.entries.iterator()
        while (bytes > MAX_BYTES && iterator.hasNext()) {
            val entry = iterator.next()
            bytes -= entry.value.byteSize()
            iterator.remove()
        }
    }

    private fun ImageBitmap.byteSize(): Int = width * height * 4
}

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = with(density) { size.dp.roundToPx() }.coerceAtLeast(1)
    val cacheKey = remember(packageName, targetPx) { "$packageName@$targetPx" }

    val iconState = produceState<ImageBitmap?>(initialValue = IconCache[cacheKey], cacheKey) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(targetPx)
                        .asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                IconCache.put(cacheKey, loaded)
                value = loaded
            }
        }
    }

    iconState.value?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size.dp),
        )
    }
}

/** Rasterises straight to [size]×[size]; the drawable never keeps its intrinsic dimensions. */
private fun Drawable.toBitmap(size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bitmap
}
