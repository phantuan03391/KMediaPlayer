package com.kyoapp.kmedia.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent


class MediaStyleHelper {
    companion object {
        fun from(context: Context, mediaSession: MediaSessionCompat?): NotificationCompat.Builder {
            val controller = mediaSession!!.controller
            val mediaMetadata = controller.metadata
            val description = mediaMetadata.description

            val builder = NotificationCompat.Builder(context, "kyo")
            builder
                    .setContentTitle(description.title)
                    .setContentText(description.subtitle)
                    .setSubText(description.description)
                    .setLargeIcon(description.iconBitmap)
                    .setContentIntent(controller.sessionActivity)
                    .setDeleteIntent(
                            MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            return builder
        }

        fun getActionIntent(context: Context, mediaKeyEvent: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            intent.`package` = context.packageName
            intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyEvent))
            return PendingIntent.getBroadcast(context, mediaKeyEvent, intent, 0)
        }
    }
}