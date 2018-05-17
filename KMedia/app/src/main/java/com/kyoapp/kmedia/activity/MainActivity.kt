package com.kyoapp.kmedia.activity

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import android.widget.Toast
import com.kyoapp.kmedia.R
import com.kyoapp.kmedia.adapter.SongAdapter
import com.kyoapp.kmedia.fragment.BottomMediaFragment
import com.kyoapp.kmedia.fragment.DetailMediaFragment
import com.kyoapp.kmedia.impl.OnSongClickListener
import com.kyoapp.kmedia.model.Song
import com.kyoapp.kmedia.service.MediaPlayerService
import com.kyoapp.kmedia.util.Constraint
import com.kyoapp.kmedia.util.StorageUtil
import com.kyoapp.kmedia.util.addFragmentToActivity
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), OnSongClickListener {

    private lateinit var player: MediaPlayerService
    private var serviceBound = false
    private val songs: ArrayList<Song> = ArrayList()
    private lateinit var songAdapter: SongAdapter
    private lateinit var bottomMediaFragment: BottomMediaFragment

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
            serviceBound = false

        }

        override fun onServiceConnected(p0: ComponentName?, service: IBinder?) {
            //bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as MediaPlayerService.LocalBinder
            player = binder.service
            serviceBound = true
        }
    }

    override fun onSongClick(position: Int) {
        playAudio(position)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomMediaFragment = supportFragmentManager.findFragmentById(R.id.frameBottom)
                as BottomMediaFragment? ?: BottomMediaFragment.newInstance().also {
            addFragmentToActivity(it, R.id.frameBottom)
        }

        supportFragmentManager.findFragmentById(R.id.frameDetail)
                as DetailMediaFragment? ?: DetailMediaFragment.newInstance().also {
            addFragmentToActivity(it, R.id.frameDetail)
        }

        initSlidingUpPanel()

        checkPermission()
    }

    private fun initSlidingUpPanel() {

        slidingUp.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {

            }

            override fun onPanelStateChanged(panel: View?, previousState: SlidingUpPanelLayout.PanelState?, newState: SlidingUpPanelLayout.PanelState?) {
                Log.e("sliding", "onPanelStateChanged: $newState")
                if (newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                    frameBottom.visibility = View.GONE
                    frameDetail.visibility = View.VISIBLE
                } else if (newState == SlidingUpPanelLayout.PanelState.DRAGGING) {
                    frameBottom.visibility = View.VISIBLE
                    frameDetail.visibility = View.VISIBLE
                } else {
                    frameBottom.visibility = View.VISIBLE
                    frameDetail.visibility = View.GONE
                }
            }

        })

        slidingUp.setFadeOnClickListener { slidingUp.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    Constraint.PERMISSION_REQUEST_READ_EXTERNAL_STORAGE)
        } else {
            loadAudio()
        }
    }

    private fun playAudio(position: Int) {
        if (!serviceBound) {
            //store audio list to SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudio(songs)
            storage.storeAudioIndex(position)

            //load service
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            //store the new audio index to SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(position)

            //service is active
            //send media with BroadcastReceiver
            val broadcastIntent = Intent(Constraint.BROADCAST_PLAY_NEW_AUDIO)
            sendBroadcast(broadcastIntent)
        }
    }

    private fun loadAudio() {
        val songCursor: Cursor? = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null, null, null, null)
        while (songCursor != null && songCursor.moveToNext()) {
            val data = songCursor.getString(songCursor.getColumnIndex(MediaStore.Audio.Media.DATA))
            val title = songCursor.getString(songCursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
            val album = songCursor.getString(songCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
            val artist = songCursor.getString(songCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
            songs.add(Song(data, title, album, artist))
//            musicList.add(data)
        }
        songCursor!!.close()
        songAdapter = SongAdapter(this, songs)
        songAdapter.setOnSongClickListener(this)
        val layoutManager = LinearLayoutManager(this)
        rcvSongs.layoutManager = layoutManager
        rcvSongs.adapter = songAdapter
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState!!.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("kyo", "onDestroy")
        if (serviceBound) {
            unbindService(serviceConnection)
            player.stopSelf()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == Constraint.PERMISSION_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                loadAudio()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onBackPressed() {
        minimize()
    }

    private fun minimize() {
        val minimize = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(minimize)
    }
}