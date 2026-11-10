package com.koushik.retrocam.util

import android.os.Build

/** Small helper that recognises the Galaxy S25 family (SM-S931 / SM-S936 / SM-S938). */
object DeviceProfile {
    val isS25Ultra: Boolean get() = Build.MODEL.startsWith("SM-S938")

    /** Furthest zoom that is truly optical, or null when unknown for this device. */
    val maxOpticalZoom: Float? get() = if (isS25Ultra) 5f else null

    val caption: String? get() = if (isS25Ultra) "GALAXY S25 ULTRA \u00B7 0.6x \u00B7 1x \u00B7 3x \u00B7 5x OPTICAL" else null
}
