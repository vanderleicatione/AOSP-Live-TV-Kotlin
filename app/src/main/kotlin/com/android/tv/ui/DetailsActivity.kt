package com.android.tv.ui

import android.os.Bundle
import android.os.Parcelable
import android.transition.Transition
import android.view.View
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.android.tv.R
import com.android.tv.Starter
import dagger.hilt.android.AndroidEntryPoint

/**
 * Detailansicht für Sendungen (die Aufnahme-Ansichten sind mit dem DVR entfernt).
 * Leanback DetailsFragment → DetailsSupportFragment (AndroidX).
 */
@AndroidEntryPoint
class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dvr_details)
        val detailsViewType = intent.getIntExtra(DETAILS_VIEW_TYPE, -1)
        val channelId = intent.getLongExtra(CHANNEL_ID, -1)
        if (detailsViewType == PROGRAM_VIEW && channelId != -1L && savedInstanceState == null) {
            val program = IntentCompat.getParcelableExtra(intent, PROGRAM, Parcelable::class.java)
            if (program != null) {
                val fragment = ProgramDetailsFragment()
                fragment.arguments = Bundle().apply {
                    putLong(CHANNEL_ID, channelId)
                    putParcelable(PROGRAM, program)
                    putString(INPUT_ID, intent.getStringExtra(INPUT_ID))
                }
                supportFragmentManager.beginTransaction().replace(R.id.dvr_details_view_frame, fragment).commit()
            }
        }
        addTransitionListener()
    }

    /** Nach dem Übergang den Fokus auf die Aktionsleiste setzen. Bugfix: Übergang kann fehlen. */
    private fun addTransitionListener() {
        window.sharedElementEnterTransition?.addListener(object : Transition.TransitionListener {
            override fun onTransitionStart(transition: Transition) {}
            override fun onTransitionEnd(transition: Transition) {
                findViewById<View>(R.id.details_overview_actions)?.requestFocus()
            }
            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })
    }

    companion object {
        const val PROGRAM = "program"
        const val CHANNEL_ID = "channel_id"
        const val INPUT_ID = "input_id"
        const val DETAILS_VIEW_TYPE = "details_view_type"
        const val SHARED_ELEMENT_NAME = "shared_element"
        const val PROGRAM_VIEW = 5
    }
}
