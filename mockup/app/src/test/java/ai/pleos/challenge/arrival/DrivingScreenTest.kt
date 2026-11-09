/*
 * 주행 전용 화면을 언제 보여 줄지 판단하는 규칙을 검증하는 테스트.
 *
 * -Author: Haneol Kim
 * -Contact: bestwook7@gmail.com
 */
package ai.pleos.challenge.arrival

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingScreenTest {
    @Test fun drivingGearShowsDrivingScreenEvenWithoutSystemRestriction() {
        assertTrue(showsDrivingScreen(restricted = false, parked = false))
    }

    /** Gear is unread before consent, so the consent form must not stay open just because gear is unknown. */
    @Test fun systemRestrictionShowsDrivingScreenWhenGearIsUnknown() {
        assertTrue(showsDrivingScreen(restricted = true, parked = null))
    }

    /** The gear signal can lag behind the system's restriction. */
    @Test fun systemRestrictionWinsOverAParkedGear() {
        assertTrue(showsDrivingScreen(restricted = true, parked = true))
    }

    @Test fun parkedCarKeepsTheFullApp() {
        assertFalse(showsDrivingScreen(restricted = false, parked = true))
    }

    /** Before consent the gear stays unknown; the parked car must still reach the consent form. */
    @Test fun unknownGearWithoutRestrictionKeepsTheFullApp() {
        assertFalse(showsDrivingScreen(restricted = false, parked = null))
    }
}
