package com.myrtilles.blackjackcounter

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object SoundManager {
    private var soundPool: SoundPool? = null
    private var cardSoundId: Int = 0
    private var loaded = false

    fun init(context: Context) {
        if (soundPool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sp = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        sp.setOnLoadCompleteListener { _, _, status ->
            loaded = (status == 0)
        }

        cardSoundId = sp.load(context, R.raw.card_flip, 1)
        soundPool = sp
    }

    fun playCard() {
        if (loaded) {
            soundPool?.play(cardSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
    }
}
