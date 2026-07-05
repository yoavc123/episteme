package com.aryan.reader.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

class SyncedAudioPlaybackManager(
    context: Context,
    private val onStopSyntheticTts: () -> Unit = {}
) : Player.Listener {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build().also { it.addListener(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cacheDir = File(appContext.cacheDir, "epub-media-overlays").also { it.mkdirs() }
    private var overlay: EpubMediaOverlay? = null
    private var source: AudioSource? = null
    private var clipEndJob: Job? = null

    private val _state = MutableStateFlow(SyncedAudioPlaybackState())
    val state = _state.asStateFlow()

    fun load(overlay: EpubMediaOverlay, source: AudioSource) {
        stop()
        this.overlay = overlay.takeIf { it.isAvailable }
        this.source = source
        _state.value = SyncedAudioPlaybackState(
            isAvailable = overlay.isAvailable,
            totalClips = overlay.clips.size
        )
    }

    fun play(index: Int = _state.value.currentIndex.coerceAtLeast(0)) {
        val loaded = overlay ?: return
        if (loaded.clips.isEmpty()) return
        onStopSyntheticTts()
        playClip(index.coerceIn(loaded.clips.indices), autoPlay = true)
    }

    fun pause() {
        player.pause()
        clipEndJob?.cancel()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun resume() {
        val clip = _state.value.currentClip
        if (clip == null) {
            play()
            return
        }
        player.play()
        _state.value = _state.value.copy(isPlaying = true)
        scheduleClipEnd(clip)
    }

    fun stop() {
        clipEndJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = _state.value.copy(isPlaying = false, currentClip = null, currentIndex = -1)
    }

    fun next() {
        val loaded = overlay ?: return
        val nextIndex = _state.value.currentIndex + 1
        if (nextIndex in loaded.clips.indices) playClip(nextIndex, autoPlay = true) else stop()
    }

    fun previous() {
        val previousIndex = (_state.value.currentIndex - 1).coerceAtLeast(0)
        playClip(previousIndex, autoPlay = true)
    }

    fun release() {
        stop()
        player.removeListener(this)
        player.release()
        cacheDir.deleteRecursively()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) next()
    }

    private fun playClip(index: Int, autoPlay: Boolean) {
        val loaded = overlay ?: return
        val audioSource = source ?: return
        val clip = loaded.clips.getOrNull(index) ?: return
        val audioFile = audioSource.fileFor(clip, cacheDir) ?: run {
            Timber.w("Synced audio clip missing audio entry: ${clip.audioEntryName}")
            return
        }
        clipEndJob?.cancel()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(audioFile)))
        player.prepare()
        player.seekTo((clip.clipBeginSeconds * 1000).toLong().coerceAtLeast(0L))
        _state.value = SyncedAudioPlaybackState(
            isAvailable = true,
            isPlaying = autoPlay,
            currentIndex = index,
            totalClips = loaded.clips.size,
            currentClip = clip
        )
        if (autoPlay) {
            player.play()
            scheduleClipEnd(clip)
        }
    }

    private fun scheduleClipEnd(clip: EpubMediaOverlayClip) {
        clipEndJob?.cancel()
        clipEndJob = scope.launch {
            val clipEndMs = (clip.clipEndSeconds * 1000).toLong().coerceAtLeast(0L)
            while (isActive) {
                if (player.currentPosition >= clipEndMs) {
                    next()
                    return@launch
                }
                delay(80L)
            }
        }
    }

    sealed interface AudioSource {
        fun fileFor(clip: EpubMediaOverlayClip, cacheDir: File): File?

        data class ExtractedDirectory(val root: File) : AudioSource {
            override fun fileFor(clip: EpubMediaOverlayClip, cacheDir: File): File? {
                val normalized = resolveZipPath("", clip.audioEntryName) ?: return null
                val rootCanonical = root.canonicalFile
                val file = File(rootCanonical, normalized).canonicalFile
                return file.takeIf { it.isFile && it.path.startsWith(rootCanonical.path + File.separator) }
            }
        }

        data class EpubZip(val epubFile: File) : AudioSource {
            override fun fileFor(clip: EpubMediaOverlayClip, cacheDir: File): File? {
                val normalized = resolveZipPath("", clip.audioEntryName) ?: return null
                if (!epubFile.isFile) return null
                return ZipFile(epubFile).use { zip ->
                    val entry = zip.getEntry(normalized)?.takeIf { !it.isDirectory } ?: return@use null
                    val target = File(cacheDir, normalized.replace('/', '_'))
                    if (!target.isFile || target.length() != entry.size) {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
                    }
                    target
                }
            }
        }
    }
}

data class SyncedAudioPlaybackState(
    val isAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val currentIndex: Int = -1,
    val totalClips: Int = 0,
    val currentClip: EpubMediaOverlayClip? = null
)
