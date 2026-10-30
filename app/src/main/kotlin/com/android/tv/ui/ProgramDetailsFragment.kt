package com.android.tv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.DetailsOverviewRowPresenter
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.SparseArrayObjectAdapter
import androidx.leanback.widget.VerticalGridView
import com.android.tv.R
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Channel
import com.android.tv.ui.details.ActionPresenterSelector
import com.android.tv.ui.details.DetailsContent
import com.android.tv.ui.details.DetailsContentPresenter
import com.android.tv.ui.details.DetailsViewBackgroundHelper
import com.android.tv.util.images.ImageLoader

/** Details einer Sendung (ohne Aufnahme-Aktionen: DVR entfernt). */
class ProgramDetailsFragment : DetailsSupportFragment() {

    private lateinit var backgroundHelper: DetailsViewBackgroundHelper
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var detailsOverview: DetailsOverviewRow
    private lateinit var program: ProgramImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!onLoadDetails(requireArguments())) requireActivity().finish()
    }

    override fun onStart() {
        super.onStart()
        requireActivity().findViewById<VerticalGridView>(R.id.container_list)?.apply {
            itemAlignmentOffset = 0
            windowAlignmentOffset = resources.getDimensionPixelSize(R.dimen.lb_details_rows_align_top)
        }
    }

    private fun setupAdapter() {
        val rowPresenter = DetailsOverviewRowPresenter(DetailsContentPresenter(requireActivity())).apply {
            backgroundColor = resources.getColor(R.color.common_tv_background, null)
            setSharedElementEnterTransition(requireActivity(), DetailsActivity.SHARED_ELEMENT_NAME)
        }
        rowsAdapter = ArrayObjectAdapter(onCreatePresenterSelector(rowPresenter))
        adapter = rowsAdapter
    }

    private fun setDetailsOverviewRow(detailsContent: DetailsContent) {
        detailsOverview = DetailsOverviewRow(detailsContent)
        detailsOverview.actionsAdapter = SparseArrayObjectAdapter(ActionPresenterSelector())
        rowsAdapter.add(detailsOverview)
        onLoadLogoAndBackgroundImages(detailsContent)
    }

    private fun onCreatePresenterSelector(rowPresenter: DetailsOverviewRowPresenter): PresenterSelector =
        ClassPresenterSelector().apply { addClassPresenter(DetailsOverviewRow::class.java, rowPresenter) }

    private fun onLoadDetails(args: Bundle): Boolean {
        val program = BundleCompat.getParcelable(args, DetailsActivity.PROGRAM, ProgramImpl::class.java)
        val channelId = args.getLong(DetailsActivity.CHANNEL_ID)
        val inputId = args.getString(DetailsActivity.INPUT_ID)
        if (program == null || channelId == Channel.INVALID_ID || inputId.isNullOrEmpty()) return false
        this.program = program
        backgroundHelper = DetailsViewBackgroundHelper(requireActivity())
        setupAdapter()
        setDetailsOverviewRow(DetailsContent.createFromProgram(requireContext(), program))
        return true
    }

    /** Logo und Hintergrund laden; gleiche URI nur einmal laden. */
    private fun onLoadLogoAndBackgroundImages(detailsContent: DetailsContent) {
        var logoDrawable: Drawable? = null
        var backgroundDrawable: Drawable? = null
        if (detailsContent.logoImageUri.isNullOrEmpty()) {
            logoDrawable = resources.getDrawable(R.drawable.dvr_default_poster, null)
            detailsOverview.imageDrawable = logoDrawable
        }
        if (detailsContent.backgroundImageUri.isNullOrEmpty()) {
            backgroundDrawable = resources.getDrawable(R.drawable.dvr_default_poster, null)
            backgroundHelper.setBackground(backgroundDrawable)
        }
        if (logoDrawable != null && backgroundDrawable != null) return
        val context = requireContext()
        if (logoDrawable == null && backgroundDrawable == null &&
            detailsContent.logoImageUri == detailsContent.backgroundImageUri
        ) {
            ImageLoader.loadBitmap(context, detailsContent.logoImageUri,
                callback = MyImageLoaderCallback(this, LOAD_LOGO_IMAGE or LOAD_BACKGROUND_IMAGE, context))
            return
        }
        if (logoDrawable == null) {
            ImageLoader.loadBitmap(context, detailsContent.logoImageUri,
                resources.getDimensionPixelSize(R.dimen.dvr_details_poster_width),
                resources.getDimensionPixelSize(R.dimen.dvr_details_poster_height),
                MyImageLoaderCallback(this, LOAD_LOGO_IMAGE, context))
        }
        if (backgroundDrawable == null) {
            ImageLoader.loadBitmap(context, detailsContent.backgroundImageUri,
                callback = MyImageLoaderCallback(this, LOAD_BACKGROUND_IMAGE, context))
        }
    }

    private class MyImageLoaderCallback(fragment: ProgramDetailsFragment, private val loadType: Int, private val context: Context) :
        ImageLoader.ImageLoaderCallback<ProgramDetailsFragment>(fragment) {
        override fun onBitmapLoaded(referent: ProgramDetailsFragment, bitmap: Bitmap?) {
            var type = loadType
            val res = context.resources
            val drawable: Drawable
            if (bitmap == null) {
                drawable = res.getDrawable(R.drawable.dvr_default_poster, null)
                if (type and LOAD_BACKGROUND_IMAGE != 0 && !referent.isDetached) {
                    type = type and LOAD_BACKGROUND_IMAGE.inv()
                    referent.backgroundHelper.setBackgroundColor(res.getColor(R.color.dvr_detail_default_background, null))
                    referent.backgroundHelper.setScrim(res.getColor(R.color.dvr_detail_default_background_scrim, null))
                }
            } else {
                drawable = BitmapDrawable(res, bitmap)
            }
            if (!referent.isDetached) {
                if (type and LOAD_LOGO_IMAGE != 0) referent.detailsOverview.imageDrawable = drawable
                if (type and LOAD_BACKGROUND_IMAGE != 0) referent.backgroundHelper.setBackground(drawable)
            }
        }
    }

    companion object {
        private const val LOAD_LOGO_IMAGE = 1
        private const val LOAD_BACKGROUND_IMAGE = 2
    }
}
