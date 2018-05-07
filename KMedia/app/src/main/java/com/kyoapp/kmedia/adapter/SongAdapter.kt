package com.kyoapp.kmedia.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kyoapp.kmedia.R
import com.kyoapp.kmedia.impl.OnSongClickListener
import com.kyoapp.kmedia.model.Song
import kotlinx.android.synthetic.main.item_songs.view.*

class SongAdapter(var context: Context, var songs: ArrayList<Song>) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {
    private lateinit var listener: OnSongClickListener
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_songs, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return songs.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = songs[position]
        holder.itemView.tvTitle.text = song.title
        holder.itemView.tvArtist.text = song.artist
    }

    fun setOnSongClickListener(listener: OnSongClickListener) {
        this.listener = listener
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener { listener.onSongClick(adapterPosition) }
        }
    }
}