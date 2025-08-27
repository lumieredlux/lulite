package com.lulite.app

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import org.webrtc.*

class WebRtcEngine(
    private val context: Context,
    private val eglBase: EglBase = EglBase.create()
) {
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: ScreenCapturerAndroid? = null

    fun init() {
        if (factory != null) return
        val initOpts = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("WebRTC-IntelVP8/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOpts)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext, /* enableIntelVp8Encoder */true, /* enableH264HighProfile */true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(
        width: Int,
        height: Int,
        fps: Int,
        projectionPermissionData: Intent,
        projectionResultCode: Int,
        onIce: (IceCandidate) -> Unit,
        onGatheringComplete: () -> Unit,
        onConnectionState: (PeerConnection.PeerConnectionState) -> Unit
    ) {
        releasePeer()

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            disableIpv6 = true
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) = onIce(candidate)
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) onGatheringComplete()
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) =
                onConnectionState(newState)

            override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        }

        peer = factory!!.createPeerConnection(rtcConfig, observer)

        // MediaProjection -> Screen capturer
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection: MediaProjection = mpm.getMediaProjection(projectionResultCode, projectionPermissionData)

        val surfaceHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
        capturer = ScreenCapturerAndroid(projectionPermissionData, object : MediaProjection.Callback() {})

        videoSource = factory!!.createVideoSource(true)
        capturer!!.initialize(surfaceHelper, context, videoSource!!.capturerObserver)
        capturer!!.startCapture(width, height, fps)

        videoTrack = factory!!.createVideoTrack("screen", videoSource)
        peer!!.addTrack(videoTrack)
    }

    fun createOffer(onSdpReady: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peer?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peer?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() = onSdpReady(desc)
                    override fun onSetFailure(p0: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun setRemoteAnswer(sdp: String, onDone: () -> Unit = {}) {
        val remote = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peer?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() = onDone()
            override fun onSetFailure(p0: String?) {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remote)
    }

    fun getCurrentLocalSdp(): String? = peer?.localDescription?.description

    fun stop() {
        try { capturer?.stopCapture() } catch (_: Throwable) {}
        releasePeer()
    }

    private fun releasePeer() {
        videoTrack?.dispose(); videoTrack = null
        videoSource?.dispose(); videoSource = null
        capturer?.dispose(); capturer = null
        peer?.dispose(); peer = null
    }

    fun dispose() {
        stop()
        factory?.dispose(); factory = null
        eglBase.release()
    }
}
