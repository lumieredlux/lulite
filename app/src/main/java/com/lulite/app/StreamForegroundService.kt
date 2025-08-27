package com.lulite.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.webrtc.PeerConnection

class StreamForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun controller(): Controller = controller
    }

    data class Config(val width: Int, val height: Int, val fps: Int)
    enum class State { IDLE, PREPARING, OFFERING, WAITING_ANSWER, CONNECTED, STOPPED, ERROR }

    interface Controller {
        fun state(): State
        fun startStreaming(config: Config, projectionResultCode: Int, projectionData: Intent, onOfferReady: (String) -> Unit)
        fun applyAnswer(answerB64: String, onApplied: () -> Unit)
        fun stopStreaming()
    }

    private val binder = LocalBinder()
    private var state: State = State.IDLE
    private lateinit var webrtc: WebRtcEngine
    private var lastOfferB64: String? = null

    private val controller = object : Controller {
        override fun state() = state

        override fun startStreaming(
            config: Config,
            projectionResultCode: Int,
            projectionData: Intent,
            onOfferReady: (String) -> Unit
        ) {
            state = State.PREPARING
            webrtc.init()
            webrtc.createPeerConnection(
                config.width, config.height, config.fps,
                projectionData, projectionResultCode,
                onIce = { /* no trickle exchange */ },
                onGatheringComplete = {
                    // After gathering complete, local SDP contains candidates
                    val sdp = webrtc.getCurrentLocalSdp().orEmpty()
                    val offerB64 = SdpUtils.encodeOfferForQr(sdp)
                    lastOfferB64 = offerB64
                    state = State.WAITING_ANSWER
                    onOfferReady(offerB64)
                },
                onConnectionState = { newState ->
                    if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                        state = State.CONNECTED
                        updateNotification("Streaming: CONNECTED")
                    }
                }
            )
            state = State.OFFERING
            webrtc.createOffer {
                updateNotification("Streaming: creating offer...")
            }
            updateNotification("Streaming: offering...")
        }

        override fun applyAnswer(answerB64: String, onApplied: () -> Unit) {
            try {
                val ans = SdpUtils.decodeAnswerFromInput(answerB64)
                if (ans.type.lowercase() != "answer") throw IllegalArgumentException("Not an ANSWER")
                webrtc.setRemoteAnswer(ans.sdp) {
                    updateNotification("Streaming: answer applied")
                    onApplied()
                }
            } catch (e: Exception) {
                state = State.ERROR
                updateNotification("Error applying answer")
            }
        }

        override fun stopStreaming() {
            webrtc.stop()
            state = State.STOPPED
            updateNotification("Streaming: stopped")
        }
    }

    override fun onCreate() {
        super.onCreate()
        webrtc = WebRtcEngine(this)
        createChannel()
        startForeground(1, baseNotification("Starting…"))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        webrtc.dispose()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "lulite.channel", getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_desc) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun baseNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, "lulite.channel")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("LuLite")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(1, baseNotification(text))
    }
}
