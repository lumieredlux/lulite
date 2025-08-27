package com.lulite.app

import android.util.Base64
import org.json.JSONObject

object SdpUtils {
    data class Signal(val type: String, val sdp: String)

    fun encodeOfferForQr(sdp: String): String {
        val obj = JSONObject().apply {
            put("type", "offer")
            put("sdp", sdp)
        }
        return Base64.encodeToString(obj.toString().toByteArray(), Base64.NO_WRAP)
    }

    fun decodeAnswerFromInput(b64: String): Signal {
        val json = String(Base64.decode(b64.trim(), Base64.DEFAULT))
        val o = JSONObject(json)
        return Signal(o.getString("type"), o.getString("sdp"))
    }
}
