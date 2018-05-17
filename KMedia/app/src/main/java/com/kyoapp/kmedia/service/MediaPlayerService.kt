package com.kyoapp.kmedia.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.app.NotificationCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.kyoapp.kmedia.R
import com.kyoapp.kmedia.enum.PlaybackStatus
import com.kyoapp.kmedia.model.Song
import com.kyoapp.kmedia.util.Constraint
import com.kyoapp.kmedia.util.MediaStyleHelper
import com.kyoapp.kmedia.util.StorageUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.IOException


class MediaPlayerService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    private val iBinder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var resumePosition: Int? = null
    private lateinit var audioManager: AudioManager
    private var audioIndex: Int = -1
    private var musicList: ArrayList<Song> = ArrayList()
    private var activeSong: Song? = null

    //media session
    private var mediaSession: MediaSessionCompat? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null

    //handling incoming call
    private var onGoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private lateinit var telephonyManager: TelephonyManager

    private val playNewAudio = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            //get the new media index from SharedPreferences
            audioIndex = StorageUtil(applicationContext).loadAudioIndex()
            if (audioIndex != -1 && audioIndex < musicList.size) {
                activeSong = musicList[audioIndex]
            } else {
                stopSelf()
            }

            // PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Song
            stopMedia()
            mediaPlayer!!.reset()
            initMediaPlayer()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun registerPlayNewSong() {
        //register playNewMedia receiver
        val filter = IntentFilter(Constraint.BROADCAST_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudio, filter)
    }

    //becoming noisy - due to a change in audio outputs
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia()
            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    private fun registerBecomingNoisyReceiver() {
        //register after getting audio focus
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    //handling incoming phone calls
    private fun callStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                when (state) {
                //pause the media if call exist or the phone is ringing
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                        if (mediaPlayer != null) {
                            pauseMedia()
                            onGoingCall = true
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (mediaPlayer != null) {
                            if (onGoingCall) {
                                onGoingCall = false
                                resumeMedia()
                            }
                        }
                    }
                }
            }
        }
        //register the listener with the telephony manager
        //listen for changes to the device call state
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (mediaSession != null)
            return
//        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        //create a new mediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //get mediaSession transport controls
        transportControls = mediaSession!!.controller.transportControls
        //set MediaSession - ready to receive media commands
        mediaSession!!.isActive = true
        //indicate that the mediaSession handles transport control commands
        //through its mediaSessionCompat.Callback
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)

        //set mediaSession's Data
        updateMetaData()

        //attach callback to receive mediaSession updates
        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                Log.e("callback", "onPause")
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSED)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                Log.e("callback", "onSkipToNext")
                skipToNext()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                Log.e("callback", "onSkipToPrevious")
                skipToPrevious()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
            }

            override fun onStop() {
                super.onStop()
                mediaSession!!.isActive = false
                removeNotification()
                stopSelf()
            }
        })
    }

    private fun updateMetaData() {
        val albumArt = BitmapFactory.decodeResource(resources, R.drawable.music_icon)
        //update the current metaData
        mediaSession!!.setMetadata(MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeSong!!.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeSong!!.album)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeSong!!.title)
                .build())
    }

    private fun skipToNext() {
        if (audioIndex == musicList.size - 1) {
            //if last in playlist
            audioIndex = 0
            activeSong = musicList[audioIndex]
        } else {
            //get next in playlist
            activeSong = musicList[++audioIndex]
        }
        //update stored index
        StorageUtil(applicationContext).storeAudioIndex(audioIndex)
        stopMedia()
        //reset media player
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun skipToPrevious() {
        if (audioIndex == 0) {
            //if first in the playlist
            audioIndex = musicList.size - 1
            activeSong = musicList[audioIndex]
        } else {
            //get previous in playlist
            activeSong = musicList[--audioIndex]
        }
        //update stored index
        StorageUtil(applicationContext).storeAudioIndex(audioIndex)
        stopMedia()
        Log.e("skipToPrevious", "skipToPrevious_Stop")
        //reset media player
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun buildNotification(playbackStatus: PlaybackStatus) {
        var notificationAction = android.R.drawable.ic_media_pause
        var playPauseAction: PendingIntent? = null
        //build a new notification according to the current state of the media player
        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause
            //create the pause action
            playPauseAction = playBackAction(1)
            EventBus.getDefault().post(Constraint.ACTION_PAUSE)

        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play
            //create the play action
            playPauseAction = playBackAction(0)
            EventBus.getDefault().post(Constraint.ACTION_PLAY)
        }

//        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.music_icon)
        //create a new notification
        val notificationBuilder = MediaStyleHelper.from(this, mediaSession)
        notificationBuilder.setSmallIcon(R.drawable.music_icon)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .addAction(android.R.drawable.ic_media_previous, "previous", playBackAction(3))
                .addAction(notificationAction, "previous", playPauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playBackAction(2))
                .setStyle(NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession!!.sessionToken))

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(Constraint.NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun removeNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Constraint.NOTIFICATION_ID)
    }

    private fun playBackAction(actionNumber: Int): PendingIntent? {
        val playBackIntent = Intent(this, MediaPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                playBackIntent.action = Constraint.ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playBackIntent, 0)
            }
            1 -> {
                playBackIntent.action = Constraint.ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playBackIntent, 0)
            }
            2 -> {
                playBackIntent.action = Constraint.ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playBackIntent, 0)
            }
            3 -> {
                playBackIntent.action = Constraint.ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playBackIntent, 0)
            }
        }
        return null
    }

    private fun handleIncomingActions(playBackAction: Intent?) {
        if (playBackAction == null || playBackAction.action == null)
            return
        val actionString = playBackAction.action
        when (actionString) {
            Constraint.ACTION_PLAY -> {
                Log.e("action", "ACTION_PLAY")
                transportControls!!.play()
            }
            Constraint.ACTION_PAUSE -> {
                Log.e("action", "ACTION_PAUSE")
                transportControls!!.pause()
            }
            Constraint.ACTION_NEXT -> transportControls!!.skipToNext()
            Constraint.ACTION_PREVIOUS -> transportControls!!.skipToPrevious()
            Constraint.ACTION_STOP -> transportControls!!.stop()
        }
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnSeekCompleteListener(this)
        mediaPlayer?.setOnInfoListener(this)
        mediaPlayer?.setOnBufferingUpdateListener(this)

        mediaPlayer?.reset()
        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)

        try {
            mediaPlayer?.setDataSource(activeSong!!.data)
            EventBus.getDefault().post(activeSong)
            val mediaStatus = Intent(Constraint.BROADCAST_MEDIA_STATUS)
            sendBroadcast(mediaStatus)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }

        mediaPlayer?.prepareAsync()
    }

    private fun playMedia() {
        if (!mediaPlayer?.isPlaying!!) {
            Log.e("playMedia", "play")
            mediaPlayer?.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer?.isPlaying!!) {
            Log.e("stopMedia", "stop")
            mediaPlayer?.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer?.isPlaying!!) {
            Log.e("pauseMedia", "pause_playing")
            mediaPlayer?.pause()
            resumePosition = mediaPlayer?.currentPosition
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer?.isPlaying!!) {
            mediaPlayer?.seekTo(resumePosition!!)
            mediaPlayer?.start()
        }
    }

    override fun onPrepared(p0: MediaPlayer?) {
        //Invoked when the media source is ready for playback.
        Log.e("onPrepared", "onPrepared")
        if (mediaPlayer!!.isPlaying) {
            pauseMedia()
        } else {
            playMedia()
        }
    }

    override fun onError(p0: MediaPlayer?, what: Int, extra: Int): Boolean {
        when (what) {
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> {
                Log.e(Constraint.TAG, "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK $extra")
            }
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
                Log.e(Constraint.TAG, "MEDIA_ERROR_SERVER_DIED $extra")
            }
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> {
                Log.e(Constraint.TAG, "MEDIA_ERROR_UNKNOWN $extra")
            }
        }
        return false
    }

    override fun onSeekComplete(p0: MediaPlayer?) {
    }

    override fun onInfo(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        return false
    }

    override fun onBufferingUpdate(p0: MediaPlayer?, p1: Int) {
    }

    override fun onAudioFocusChange(focusState: Int) {
        when (focusState) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                //resume playback
                if (!mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.start()
                }
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                //lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                //lost focus for a short time, but we have to stop playback
                //we don't release the media player because playback is likely to resume
                if (mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.stop()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                //lost focus for a short time, but it's ok to keep playing
                //at an attenuated level
                if (mediaPlayer?.isPlaying!!) {
                    mediaPlayer?.setVolume(0.1f, 0.1f)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return false
    }

    private fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this)
    }

    override fun onBind(p0: Intent?): IBinder {
        return iBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //request audio focus
        if (!requestAudioFocus()) {
            stopSelf()
        }

        try {
            initMediaSession()
        } catch (e: RemoteException) {
            e.printStackTrace()
            stopSelf()
        }

        buildNotification(PlaybackStatus.PLAYING)
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        handleIncomingActions(intent)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer?.release()
        }
        removeAudioFocus()

        //disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }

        removeNotification()

        //unregister BroadcastReceiver
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(playNewAudio)

        //clear cached playlist
        StorageUtil(applicationContext).clearCachedAudioPlayList()

        EventBus.getDefault().unregister(this)
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        //invoked when playback of a media source has completed.
        stopMedia()
        buildNotification(PlaybackStatus.PAUSED)
        //stop the service
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()

        try {
            //load data from SharedPreferences
            val storage = StorageUtil(applicationContext)
            musicList = storage.loadAudio()
            audioIndex = storage.loadAudioIndex()
            if (audioIndex != -1 && audioIndex < musicList.size) {
                activeSong = musicList[audioIndex]
            } else {
                stopSelf()
            }
        } catch (e: NullPointerException) {
            stopSelf()
        }
        initMediaPlayer()

        // perform one-time setup procedures
        //manage incoming calls during playback
        callStateListener()

        registerBecomingNoisyReceiver()
        registerPlayNewSong()

        EventBus.getDefault().register(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun receivePlayBack(playbackStatus: PlaybackStatus) {
        if (playbackStatus == PlaybackStatus.PLAYING) {
            resumeMedia()
            buildNotification(PlaybackStatus.PLAYING)
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            pauseMedia()
            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }
}