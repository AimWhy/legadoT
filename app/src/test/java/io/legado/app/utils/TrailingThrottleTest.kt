package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailingThrottleTest {

    private val scheduled = mutableListOf<Runnable>()
    private val throttle = TrailingThrottle(
        150,
        { r, _ -> scheduled.add(r) },
        { r -> scheduled.remove(r) },
    )

    private fun fire() {
        val r = scheduled.removeAt(0)
        r.run()
    }

    @Test
    fun submitSchedulesOnceAndAppliesLatest() {
        var applied = -1
        throttle.submit { applied = 1 }
        throttle.submit { applied = 2 }
        throttle.submit { applied = 3 }
        assertEquals(1, scheduled.size)
        fire()
        assertEquals(3, applied)
    }

    @Test
    fun afterFireNextSubmitSchedulesAgain()  {
        var applied = -1
        throttle.submit { applied = 1 }
        fire()
        assertEquals(1, applied)
        throttle.submit { applied = 2 }
        assertEquals(1, scheduled.size)
        fire()
        assertEquals(2, applied)
    }

    @Test
    fun firedRunnableWithoutNewSubmitDoesNothing() {
        var count = 0
        throttle.submit { count++ }
        fire()
        assertEquals(1, count)
        assertEquals(0, scheduled.size)
    }

    @Test
    fun cancelDropsPendingAndUnschedules() {
        var applied = -1
        throttle.submit { applied = 1 }
        throttle.cancel()
        assertEquals(0, scheduled.size)
        assertEquals(-1, applied)
        // cancel 后再 submit 恢复正常工作
        throttle.submit { applied = 2 }
        assertEquals(1, scheduled.size)
        fire()
        assertEquals(2, applied)
    }
}
