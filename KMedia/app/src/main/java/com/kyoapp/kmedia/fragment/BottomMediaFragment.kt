package com.kyoapp.kmedia.fragment


import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.crashlytics.android.Crashlytics
import com.kyoapp.kmedia.R
import com.kyoapp.kmedia.enum.PlaybackStatus
import com.kyoapp.kmedia.model.Song
import com.kyoapp.kmedia.util.Constraint
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.fragment_bottom_media.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class BottomMediaFragment : Fragment() {

    private var mediaControllerStatus = Constraint.ACTION_PLAY

    companion object {
        fun newInstance(): BottomMediaFragment {
            return BottomMediaFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        EventBus.getDefault().register(this)
        return inflater.inflate(R.layout.fragment_bottom_media, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Fabric.with(activity, Crashlytics())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imgControlPlayer.setOnClickListener {
            if (mediaControllerStatus == Constraint.ACTION_PAUSE) {
                EventBus.getDefault().postSticky(PlaybackStatus.PAUSED)
            } else if (mediaControllerStatus == Constraint.ACTION_PLAY) {
                EventBus.getDefault().postSticky(PlaybackStatus.PLAYING)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSongReceived(song: Song) {
        tvSelectedTitle.text = song.title
        tvSelectedArtist.text = song.artist
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMediaReceived(result: String) {
        mediaControllerStatus = result
        if (result == Constraint.ACTION_PAUSE) {
            imgControlPlayer.setImageResource(android.R.drawable.ic_media_pause)
        } else if (result == Constraint.ACTION_PLAY) {
            imgControlPlayer.setImageResource(android.R.drawable.ic_media_play)
        }
    }
}
