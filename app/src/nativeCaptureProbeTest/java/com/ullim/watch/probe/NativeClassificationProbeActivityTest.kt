package com.ullim.watch.probe

import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeClassificationProbeActivityTest {
    @Test fun openingWithoutExplicitActionNeverStartsTheModelWorker() {
        val controller = Robolectric.buildActivity(NativeClassificationProbeActivity::class.java).setup()
        try {
            controller.get().onWindowFocusChanged(true)
            assertNull(NativeClassificationProbeActivity::class.java.getDeclaredField("worker").apply {
                isAccessible = true
            }.get(controller.get()))
        } finally { controller.pause().stop().destroy() }
    }
}
