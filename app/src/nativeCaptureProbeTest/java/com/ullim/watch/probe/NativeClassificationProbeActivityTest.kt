package com.ullim.watch.probe

import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeClassificationProbeActivityTest {
    @Test fun focusLostDuringFinalCleanupCannotBecomePass() {
        val controller = Robolectric.buildActivity(NativeClassificationProbeActivity::class.java).create()
        try {
            assertThrows(InterruptedException::class.java) {
                controller.get().verifyActive { controller.get().onWindowFocusChanged(false) }
            }
        } finally { controller.destroy() }
    }

    @Test fun interruptionRestoredByCleanupCannotBecomePass() {
        val controller = Robolectric.buildActivity(NativeClassificationProbeActivity::class.java).create()
        try {
            assertThrows(InterruptedException::class.java) {
                controller.get().verifyActive { Thread.currentThread().interrupt() }
            }
        } finally { Thread.interrupted(); controller.destroy() }
    }

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
