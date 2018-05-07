package com.kyoapp.kmedia.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore.Audio
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kyoapp.kmedia.model.Song


class StorageUtil(var context: Context) {
    private lateinit var preferences: SharedPreferences

    fun storeAudio(songArray: ArrayList<Song>) {
        preferences = context.getSharedPreferences(Constraint.STORAGE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        val gson = Gson()
        val json = gson.toJson(songArray)
        editor.putString(Constraint.MUSIC_LIST, json)
        editor.apply()
    }

    fun loadAudio(): ArrayList<Song> {
        preferences = context.getSharedPreferences(Constraint.STORAGE, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = preferences.getString(Constraint.MUSIC_LIST, null)
        val type = object : TypeToken<ArrayList<Song>>() {

        }.type
        return gson.fromJson(json, type)
    }

    fun storeAudioIndex(index: Int) {
        preferences = context.getSharedPreferences(Constraint.STORAGE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putInt(Constraint.MUSIC_TEMP_POSITION, index)
        editor.apply()
    }

    fun loadAudioIndex(): Int {
        preferences = context.getSharedPreferences(Constraint.STORAGE, Context.MODE_PRIVATE)
        return preferences.getInt(Constraint.MUSIC_TEMP_POSITION, -1)
    }

    fun clearCachedAudioPlayList() {
        preferences = context.getSharedPreferences(Constraint.STORAGE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.clear()
        editor.apply()
    }
}