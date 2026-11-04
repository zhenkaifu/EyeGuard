package com.eyeguard.app.feature.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.eyeguard.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 修炼配乐的唯一播放入口。计划页只在前台播放，修炼与休息可随前台服务在后台播放。
 * 所有音频均为本地资源；外部播放器取得音频焦点时立即暂停或降低音量。
 */
@Singleton
class FocusMusicManager @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: FocusRepository
) {
    private enum class Scene(val resourceId: Int, val volume: Float) {
        PLAN(R.raw.cultivation_plan, 0.35f),
        FOCUS(R.raw.cultivation_focus, 0.22f)
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(::onAudioFocusChange)
        .build()

    private var snapshot = FocusSnapshot()
    private var snapshotLoaded = false
    private var screenVisible = false
    private var serviceRunning = false
    private var player: MediaPlayer? = null
    private var currentScene: Scene? = null
    private var hasAudioFocus = false
    private var ducked = false

    init {
        scope.launch {
            repository.focusState.collectLatest {
                snapshot = it
                snapshotLoaded = true
                synchronizePlayback()
            }
        }
    }

    fun setScreenVisible(visible: Boolean) {
        if (screenVisible == visible) return
        screenVisible = visible
        synchronizePlayback()
    }

    fun setServiceRunning(running: Boolean) {
        if (serviceRunning == running) return
        serviceRunning = running
        synchronizePlayback()
    }

    private fun desiredScene(): Scene? {
        if (!snapshotLoaded || !snapshot.musicEnabled) return null
        return when (snapshot.phase) {
            FocusPhase.IDLE -> if (screenVisible) Scene.PLAN else null
            FocusPhase.ACTIVE, FocusPhase.RESTING ->
                if (screenVisible || serviceRunning) Scene.FOCUS else null
            else -> null
        }
    }

    private fun synchronizePlayback() {
        val desired = desiredScene()
        if (desired == null) {
            stopPlayback()
            return
        }
        if (player != null && currentScene == desired && hasAudioFocus) {
            applyVolume()
            if (!ducked && player?.isPlaying == false) runCatching { player?.start() }
            return
        }

        stopPlayback()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        hasAudioFocus = true
        runCatching {
            MediaPlayer.create(context, desired.resourceId)?.apply {
                setAudioAttributes(audioAttributes)
                isLooping = true
                setOnErrorListener { failedPlayer, _, _ ->
                    if (player === failedPlayer) {
                        releasePlayer()
                        abandonAudioFocus()
                    }
                    true
                }
            } ?: error("Unable to open cultivation soundtrack")
        }.onSuccess { newPlayer ->
            player = newPlayer
            currentScene = desired
            applyVolume()
            newPlayer.start()
        }.onFailure {
            releasePlayer()
            abandonAudioFocus()
        }
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                ducked = false
                synchronizePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                ducked = false
                runCatching { if (player?.isPlaying == true) player?.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = true
                ducked = true
                applyVolume()
            }
        }
    }

    private fun applyVolume() {
        val scene = currentScene ?: return
        val volume = scene.volume * if (ducked) 0.35f else 1f
        runCatching { player?.setVolume(volume, volume) }
    }

    private fun stopPlayback() {
        releasePlayer()
        abandonAudioFocus()
    }

    private fun releasePlayer() {
        runCatching { player?.release() }
        player = null
        currentScene = null
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        hasAudioFocus = false
        ducked = false
    }
}
