package com.lulite.app

import android.app.Application
import android.content.*
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class StreamViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val running: Boolean = false,
        val statusText: String = "Idle",
        val width: Int = 1280,
        val height: Int = 720,
        val fps: Int = 30,
        val offerB64: String? = null
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private var bound = false
    private var controller: StreamForegroundService.Controller? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            controller = (service as StreamForegroundService.LocalBinder).controller()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            controller = null
        }
    }

    fun bindService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, StreamForegroundService::class.java)
        ctx.startForegroundService(intent)
        ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (bound) {
            getApplication<Application>().unbindService(conn)
            bound = false
        }
    }

    fun setPreset(is1080p: Boolean) {
        _state.value = _state.value!!.copy(
            width = if (is1080p) 1920 else 1280,
            height = if (is1080p) 1080 else 720
        )
    }

    fun start(projCode: Int, projData: Intent) {
        val s = _state.value!!
        controller?.startStreaming(
            StreamForegroundService.Config(s.width, s.height, s.fps),
            projCode, projData
        ) { offer ->
            _state.postValue(s.copy(running = true, statusText = "Offer ready, scan/paste answer", offerB64 = offer))
        } ?: run {
            _state.postValue(s.copy(statusText = "Service not bound"))
        }
    }

    fun applyAnswer(answerB64: String) {
        val s = _state.value!!
        controller?.applyAnswer(answerB64) {
            _state.postValue(s.copy(statusText = "Answer applied, connecting…"))
        } ?: run {
            _state.postValue(s.copy(statusText = "Service not bound"))
        }
    }

    fun stop() {
        controller?.stopStreaming()
        _state.postValue(_state.value!!.copy(running = false, statusText = "Stopped"))
    }
}
