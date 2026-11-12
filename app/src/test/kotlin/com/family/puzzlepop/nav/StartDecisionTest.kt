package com.family.puzzlepop.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDecisionTest {
    private fun d(pin: Boolean, ever: Boolean = false, corrupt: Boolean = false, photos: Boolean = true, kid: Boolean = false, always: Boolean = false) =
        startDecision(pin, ever, corrupt, photos, kid, always)

    @Test fun stage1UpgradeAsksForPin() = assertEquals(Start.SETUP, d(pin = false))
    @Test fun freshInstallAsksForPin() = assertEquals(Start.SETUP, d(pin = false, photos = false))
    @Test fun corruptionWithPhotosIsKidMode() = assertEquals(Start.KID, d(pin = false, corrupt = true))
    @Test fun lostPinEverSetWithPhotosIsKidMode() = assertEquals(Start.KID, d(pin = false, ever = true))
    @Test fun corruptionWithoutPhotosAsksForPin() = assertEquals(Start.SETUP, d(pin = false, corrupt = true, photos = false))
    @Test fun normalIsLibrary() = assertEquals(Start.LIBRARY, d(pin = true))
    @Test fun kidModeActiveIsKid() = assertEquals(Start.KID, d(pin = true, kid = true))
    @Test fun alwaysKidIsKid() = assertEquals(Start.KID, d(pin = true, always = true))
}
