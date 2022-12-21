package com.github.libretube.ui.activities

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.libretube.constants.IntentData
import com.github.libretube.databinding.ActivityOfflinePlayerBinding
import com.github.libretube.databinding.ExoStyledPlayerControlViewBinding
import com.github.libretube.db.DatabaseHolder.Companion.Database
import com.github.libretube.enums.FileType
import com.github.libretube.extensions.awaitQuery
import com.github.libretube.ui.base.BaseActivity
import com.github.libretube.ui.extensions.setAspectRatio
import com.github.libretube.ui.models.PlayerViewModel
import com.github.libretube.util.PlayerHelper
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.FileDataSource
import java.io.File

class OfflinePlayerActivity : BaseActivity() {
    private lateinit var binding: ActivityOfflinePlayerBinding
    private lateinit var videoId: String
    private lateinit var player: ExoPlayer
    private lateinit var playerView: StyledPlayerView
    private lateinit var playerBinding: ExoStyledPlayerControlViewBinding
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        hideSystemBars()

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        super.onCreate(savedInstanceState)

        videoId = intent?.getStringExtra(IntentData.videoId)!!

        binding = ActivityOfflinePlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializePlayer()
        playVideo()

        requestedOrientation = PlayerHelper.getOrientation(player.videoSize)
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()

        playerView = binding.player

        playerView.player = player

        playerBinding = binding.player.binding

        playerBinding.fullscreen.visibility = View.GONE
        playerBinding.closeImageButton.setOnClickListener {
            finish()
        }

        binding.player.initialize(
            null,
            binding.doubleTapOverlay.binding,
            binding.playerGestureControlsView.binding,
            null
        )
    }

    private fun File.toUri(): Uri? {
        return if (this.exists()) Uri.fromFile(this) else null
    }

    private fun playVideo() {
        val downloadFiles = awaitQuery {
            Database.downloadDao().findById(videoId).downloadItems
        }

        val video = downloadFiles.firstOrNull { it.type == FileType.VIDEO }
        val audio = downloadFiles.firstOrNull { it.type == FileType.AUDIO }
        val subtitle = downloadFiles.firstOrNull { it.type == FileType.SUBTITLE }

        val videoUri = video?.path?.let { File(it).toUri() }
        val audioUri = audio?.path?.let { File(it).toUri() }
        val subtitleUri = subtitle?.path?.let { File(it).toUri() }

        setMediaSource(
            videoUri,
            audioUri
        )

        player.prepare()
        player.play()
    }

    private fun setMediaSource(videoUri: Uri?, audioUri: Uri?) {
        when {
            videoUri != null && audioUri != null -> {
                val videoSource = ProgressiveMediaSource.Factory(FileDataSource.Factory())
                    .createMediaSource(
                        MediaItem.fromUri(videoUri)
                    )

                val audioSource = ProgressiveMediaSource.Factory(FileDataSource.Factory())
                    .createMediaSource(
                        MediaItem.fromUri(audioUri)
                    )

                val mediaSource = MergingMediaSource(
                    audioSource,
                    videoSource
                )

                player.setMediaSource(mediaSource)
            }
            videoUri != null -> player.setMediaItem(
                MediaItem.fromUri(videoUri)
            )
            audioUri != null -> player.setMediaItem(
                MediaItem.fromUri(audioUri)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window?.decorView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        window.statusBarColor = Color.TRANSPARENT

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        supportActionBar?.hide()

        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        playerViewModel.isFullscreen.value = true
        super.onResume()
    }

    override fun onPause() {
        playerViewModel.isFullscreen.value = false
        super.onPause()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        if (!PlayerHelper.pipEnabled) return

        if (player.playbackState == PlaybackState.STATE_PAUSED) return

        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setActions(emptyList())
                .setAspectRatio(player.videoSize.width, player.videoSize.height)
                .build()
        )

        super.onUserLeaveHint()
    }
}
