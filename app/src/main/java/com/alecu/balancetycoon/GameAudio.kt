package com.alecu.balancetycoon

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Efectos de sonido del juego (generados con tools/generar_audio.py) y su volumen relativo. */
enum class Sfx(val res: Int, val volume: Float) {
    TAP(R.raw.sfx_tap, 0.5f),
    COIN(R.raw.sfx_coin, 0.4f),
    BUY(R.raw.sfx_buy, 0.7f),
    HIRE(R.raw.sfx_hire, 0.8f),
    CORRECT(R.raw.sfx_correct, 0.8f),
    WRONG(R.raw.sfx_wrong, 0.7f),
    LEVEL_UP(R.raw.sfx_levelup, 0.85f),
    MOVE(R.raw.sfx_move, 0.9f),
    ALERT(R.raw.sfx_alert, 0.7f),
    CHIME(R.raw.sfx_chime, 0.6f),
}

/**
 * Sonido, música y vibración. Los efectos van por SoundPool (latencia baja) y la música, en
 * bucle, por MediaPlayer, que solo suena con la app en primer plano. Las preferencias se
 * guardan en SharedPreferences, aparte de la partida.
 */
class GameAudio(private val context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var soundOn: Boolean by mutableStateOf(prefs.getBoolean(KEY_SOUND, true))
        private set
    var musicOn: Boolean by mutableStateOf(prefs.getBoolean(KEY_MUSIC, true))
        private set
    var vibrationOn: Boolean by mutableStateOf(prefs.getBoolean(KEY_VIBRATION, true))
        private set

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids: Map<Sfx, Int> = Sfx.entries.associateWith { pool.load(context, it.res, 1) }
    private val lastPlayed = HashMap<Sfx, Long>()
    private var player: MediaPlayer? = null
    private var foreground = false

    /** Reproduce un efecto. El mismo efecto no se repite antes de 80 ms, para no saturar. */
    fun play(sfx: Sfx) {
        if (!soundOn) return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastPlayed[sfx] ?: 0L) < 80L) return
        lastPlayed[sfx] = now
        pool.play(ids.getValue(sfx), sfx.volume, sfx.volume, 1, 0, 1f)
    }

    fun setSound(on: Boolean) {
        soundOn = on
        prefs.edit().putBoolean(KEY_SOUND, on).apply()
    }

    fun setMusic(on: Boolean) {
        musicOn = on
        prefs.edit().putBoolean(KEY_MUSIC, on).apply()
        updateMusic()
    }

    fun setVibration(on: Boolean) {
        vibrationOn = on
        prefs.edit().putBoolean(KEY_VIBRATION, on).apply()
    }

    fun onForeground() {
        foreground = true
        updateMusic()
    }

    fun onBackground() {
        foreground = false
        updateMusic()
    }

    private fun updateMusic() {
        if (musicOn && foreground) {
            val p = player ?: MediaPlayer.create(context, R.raw.music_office)?.also {
                it.isLooping = true
                it.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                player = it
            } ?: return
            if (!p.isPlaying) p.start()
        } else {
            player?.let { if (it.isPlaying) it.pause() }
        }
    }

    fun release() {
        pool.release()
        player?.release()
        player = null
    }

    companion object {
        private const val KEY_SOUND = "sonido"
        private const val KEY_MUSIC = "musica"
        private const val KEY_VIBRATION = "vibracion"
        private const val MUSIC_VOLUME = 0.35f
    }
}
