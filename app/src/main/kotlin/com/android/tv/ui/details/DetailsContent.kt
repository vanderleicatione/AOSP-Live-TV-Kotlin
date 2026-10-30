package com.android.tv.ui.details

import android.content.Context
import android.media.tv.TvContract
import android.text.TextUtils
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.TextAppearanceSpan

/** Inhalt der Detailansicht (Titel, Zeiten, Beschreibung, Bild-URIs). */
class DetailsContent private constructor() {
    /** Titel. */
    var title: CharSequence? = null
        private set
    /** Startzeit. */
    var startTimeUtcMillis: Long = 0
        private set
    /** Endzeit. */
    var endTimeUtcMillis: Long = 0
        private set
    /** Beschreibung. */
    var description: String? = null
        private set
    /** Logo-Bild-URI. */
    var logoImageUri: String? = null
        private set
    /** Hintergrundbild-URI. */
    var backgroundImageUri: String? = null
        private set
    /** Ob die Bild-URIs vom Kanallogo stammen. */
    var isUsingChannelLogo: Boolean = false
        private set
    private var showErrorMessage: Boolean = false

    /** Ob die Fehlermeldung angezeigt werden soll. */
    fun shouldShowErrorMessage(): Boolean = showErrorMessage

    /** Übernimmt die Werte eines anderen Inhalts. */
    fun copyFrom(other: DetailsContent) {
        if (this === other) return
        title = other.title
        startTimeUtcMillis = other.startTimeUtcMillis
        endTimeUtcMillis = other.endTimeUtcMillis
        description = other.description
        logoImageUri = other.logoImageUri
        backgroundImageUri = other.backgroundImageUri
        isUsingChannelLogo = other.isUsingChannelLogo
        showErrorMessage = other.showErrorMessage
    }

    /** Baut einen [DetailsContent]. */
    class Builder {
        private val detailsContent = DetailsContent().apply {
            startTimeUtcMillis = INVALID_TIME
            endTimeUtcMillis = INVALID_TIME
        }
        private var channelId = 0L
        private var programTitle: String? = null
        private var seasonNumber: String? = null
        private var episodeNumber: String? = null
        private var posterArtUri: String? = null
        private var thumbnailUri: String? = null

        fun setTitle(title: CharSequence?) = apply { detailsContent.title = title }
        fun setStartTimeUtcMillis(startTimeUtcMillis: Long) = apply { detailsContent.startTimeUtcMillis = startTimeUtcMillis }
        fun setEndTimeUtcMillis(endTimeUtcMillis: Long) = apply { detailsContent.endTimeUtcMillis = endTimeUtcMillis }
        fun setDescription(description: String?) = apply { detailsContent.description = description }
        fun setLogoImageUri(logoImageUri: String?) = apply { detailsContent.logoImageUri = logoImageUri }
        fun setBackgroundImageUri(backgroundImageUri: String?) = apply { detailsContent.backgroundImageUri = backgroundImageUri }

        internal fun setProgramTitle(programTitle: String?) = apply { this.programTitle = programTitle }
        internal fun setSeasonNumber(seasonNumber: String?) = apply { this.seasonNumber = seasonNumber }
        internal fun setEpisodeNumber(episodeNumber: String?) = apply { this.episodeNumber = episodeNumber }
        internal fun setChannelId(channelId: Long) = apply { this.channelId = channelId }
        internal fun setPosterArtUri(posterArtUri: String?) = apply { this.posterArtUri = posterArtUri }
        internal fun setThumbnailUri(thumbnailUri: String?) = apply { this.thumbnailUri = thumbnailUri }

        private fun createStyledTitle(context: Context, channel: Channel?) {
            val title = getStyledTitleWithEpisodeNumber(
                context, programTitle, seasonNumber, episodeNumber,
                R.style.text_appearance_card_view_episode_number)
            detailsContent.title = if (TextUtils.isEmpty(title)) {
                if (channel != null) channel.displayName
                else context.resources.getString(R.string.no_program_information)
            } else {
                title
            }
        }

        private fun createImageUris(channel: Channel?) {
            detailsContent.logoImageUri = null
            detailsContent.backgroundImageUri = null
            detailsContent.isUsingChannelLogo = false
            if (!TextUtils.isEmpty(posterArtUri) && !TextUtils.isEmpty(thumbnailUri)) {
                detailsContent.logoImageUri = posterArtUri
                detailsContent.backgroundImageUri = thumbnailUri
            } else if (!TextUtils.isEmpty(posterArtUri)) {
                // thumbnailUri ist leer
                detailsContent.logoImageUri = posterArtUri
                detailsContent.backgroundImageUri = posterArtUri
            } else if (!TextUtils.isEmpty(thumbnailUri)) {
                // posterArtUri ist leer
                detailsContent.logoImageUri = thumbnailUri
                detailsContent.backgroundImageUri = thumbnailUri
            }
            if (TextUtils.isEmpty(detailsContent.logoImageUri) && channel != null) {
                val channelLogoUri = TvContract.buildChannelLogoUri(channel.id).toString()
                detailsContent.logoImageUri = channelLogoUri
                detailsContent.backgroundImageUri = channelLogoUri
                detailsContent.isUsingChannelLogo = true
            }
        }

        /** Baut den Inhalt (Titel und Bilder ggf. aus dem Kanal). */
        fun build(context: Context): DetailsContent {
            val channel = TvSingletons.getSingletons(context).getChannelDataManager().getChannel(channelId)
            if (detailsContent.title == null) createStyledTitle(context, channel)
            if (detailsContent.backgroundImageUri == null && detailsContent.logoImageUri == null) {
                createImageUris(channel)
            }
            return DetailsContent().also { it.copyFrom(detailsContent) }
        }
    }

    companion object {
        /** Ungültige Zeit. */
        const val INVALID_TIME = -1L

        @JvmStatic
        fun createFromProgram(context: Context, program: Program): DetailsContent =
            Builder()
                .setChannelId(program.channelId)
                .setProgramTitle(program.title)
                .setSeasonNumber(program.seasonNumber)
                .setEpisodeNumber(program.episodeNumber)
                .setStartTimeUtcMillis(program.startTimeUtcMillis)
                .setEndTimeUtcMillis(program.endTimeUtcMillis)
                .setDescription(
                    if (TextUtils.isEmpty(program.longDescription)) program.description
                    else program.longDescription)
                .setPosterArtUri(program.posterArtUri)
                .setThumbnailUri(program.thumbnailUri)
                .build(context)

        /** "Titel · S1 F2" mit eigenem Stil für die Folgennummer (Span aus dem HTML-String). */
        private fun getStyledTitleWithEpisodeNumber(context: Context, title: String?, seasonNumber: String?, episodeNumber: String?, styleResId: Int): CharSequence {
            if (title.isNullOrEmpty()) return ""
            val builder = if (seasonNumber.isNullOrEmpty() || seasonNumber == "0") {
                SpannableStringBuilder.valueOf(
                    if (episodeNumber.isNullOrEmpty()) SpannableStringBuilder.valueOf(title)
                    else Html.fromHtml(context.getString(R.string.program_title_with_episode_number_no_season, title, episodeNumber), Html.FROM_HTML_MODE_LEGACY))
            } else {
                SpannableStringBuilder.valueOf(Html.fromHtml(context.getString(R.string.program_title_with_episode_number, title, seasonNumber, episodeNumber),
                    Html.FROM_HTML_MODE_LEGACY))
            }
            val spans = builder.getSpans(0, builder.length, Any::class.java)
            if (spans.isNotEmpty()) {
                if (styleResId != 0) {
                    builder.setSpan(TextAppearanceSpan(context, styleResId), builder.getSpanStart(spans[0]), builder.getSpanEnd(spans[0]),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                builder.removeSpan(spans[0])
            }
            return SpannableString(builder)
        }
    }
}
