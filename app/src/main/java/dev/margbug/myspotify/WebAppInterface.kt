package dev.margbug.myspotify

import android.webkit.JavascriptInterface

class WebAppInterface(private val onStateChange: (isPlaying: Boolean, title: String, artist: String, coverUrl: String) -> Unit) {

    @JavascriptInterface
    fun onPlaybackStateChanged(isPlaying: Boolean, title: String, artist: String, coverUrl: String) {
        onStateChange(isPlaying, title, artist, coverUrl)
    }
}
