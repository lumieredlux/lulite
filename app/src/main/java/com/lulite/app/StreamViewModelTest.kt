package com.lulite.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamViewModelTest {
    @Test
    fun presetSwitch_updatesResolution() {
        val vm = StreamViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.setPreset(false)
        assertEquals(1280, vm.state.value!!.width)
        assertEquals(720, vm.state.value!!.height)
        vm.setPreset(true)
        assertEquals(1920, vm.state.value!!.width)
        assertEquals(1080, vm.state.value!!.height)
    }
}
