package com.jay.m3play.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.jay.m3play.constants.HapticsEnabledKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object Haptics {

    // Cache variable jo turant read hoga bina Main Thread block kiye
    private var isHapticsEnabled = true
    private var observerJob: Job? = null

    // Ye function chupchap background me setting observe karega
    private fun ensureObserving(context: Context) {
        if (observerJob == null) {
            observerJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    context.dataStore.data.collect { prefs ->
                        isHapticsEnabled = prefs[HapticsEnabledKey] ?: true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun click(haptic: HapticFeedback? = null, context: Context? = null) {
        context?.let { ensureObserving(it.applicationContext) }
        
        if (context != null && !isHapticsEnabled) return

        haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            ?: context?.let { vibrate(it, 12L, 80) }
    }

    fun tick(haptic: HapticFeedback? = null, context: Context? = null) {
        context?.let { ensureObserving(it.applicationContext) }
        
        if (context != null && !isHapticsEnabled) return

        haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            ?: context?.let { vibrate(it, 8L, 60) }
    }

    fun longPress(haptic: HapticFeedback? = null, context: Context? = null) {
        context?.let { ensureObserving(it.applicationContext) }
        
        if (context != null && !isHapticsEnabled) return

        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            ?: context?.let { vibrate(it, 43L, 120) }
    }

    private fun waveform(
        context: Context,
        timings: LongArray,
        amplitudes: IntArray
    ) {
        val vibrator = getVibrator(context) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings.sum())
        }
    }

    private fun vibrate(
        context: Context,
        duration: Long,
        amplitude: Int
    ) {
        val vibrator = getVibrator(context) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    amplitude.coerceIn(1, 255)
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
