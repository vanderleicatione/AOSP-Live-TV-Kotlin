package com.android.tv.util

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * App-Einstellungen in den Standard-SharedPreferences.
 * Entfällt: Altersfreigabe-Systeme/-Stufen (Kindersicherung, nur System-App).
 */
object TvSettings {
    const val PREF_DISPLAY_MODE = "display_mode" // int
    const val PREF_PIN = "pin" // 4-stellig, sonst nicht gesetzt

    private const val PREF_MULTI_AUDIO_ID = "pref.multi_audio_id"
    private const val PREF_MULTI_AUDIO_LANGUAGE = "pref.multi_audio_language"
    private const val PREF_MULTI_AUDIO_CHANNEL_COUNT = "pref.multi_audio_channel_count"
    private const val PREF_DISABLE_PIN_UNTIL = "pref.disable_pin_until"

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    @JvmStatic fun getMultiAudioId(context: Context): String? = prefs(context).getString(PREF_MULTI_AUDIO_ID, null)
    @JvmStatic fun setMultiAudioId(context: Context, id: String?) = prefs(context).edit().putString(PREF_MULTI_AUDIO_ID, id).apply()
    @JvmStatic fun getMultiAudioLanguage(context: Context): String? = prefs(context).getString(PREF_MULTI_AUDIO_LANGUAGE, null)
    @JvmStatic fun setMultiAudioLanguage(context: Context, language: String?) =
        prefs(context).edit().putString(PREF_MULTI_AUDIO_LANGUAGE, language).apply()
    @JvmStatic fun getMultiAudioChannelCount(context: Context): Int = prefs(context).getInt(PREF_MULTI_AUDIO_CHANNEL_COUNT, 0)
    @JvmStatic fun setMultiAudioChannelCount(context: Context, channelCount: Int) =
        prefs(context).edit().putInt(PREF_MULTI_AUDIO_CHANNEL_COUNT, channelCount).apply()

    @JvmStatic fun getDisablePinUntil(context: Context): Long = prefs(context).getLong(PREF_DISABLE_PIN_UNTIL, 0)
    @JvmStatic fun setDisablePinUntil(context: Context, timeMillis: Long) =
        prefs(context).edit().putLong(PREF_DISABLE_PIN_UNTIL, timeMillis).apply()

}
