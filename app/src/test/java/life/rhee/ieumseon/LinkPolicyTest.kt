package life.rhee.ieumseon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPolicyTest {
    private fun wifi(rssi: Int? = -76, id: Long = 7L, open: Boolean = false) =
        Link(id, Transport.WIFI, validated = true, rssi = rssi, security = if (open) WiFiSecurity.OPEN else WiFiSecurity.ENCRYPTED)

    private val cell = Link(9L, Transport.CELLULAR, validated = true)

    @Test
    fun portalHasPriorityOverSignalAndMobileCandidate() {
        val portal = wifi(-80, open = true).copy(portal = true)
        assertEquals(Advisory.SIGN_IN_WIFI, advise(LinkSnapshot(portal, portal, cell), weak = true))
    }

    @Test
    fun blockedCellularIsNotAUsableBackupEvenIfValidated() {
        val blocked = cell.copy(blocked = true)
        assertFalse(LinkSnapshot(wifi(), wifi(), blocked).mobileAvailable)
        assertEquals(Advisory.MOBILE_UNVERIFIED, advise(LinkSnapshot(blocked, null, blocked), weak = false))
    }

    @Test
    fun openIsNotInferredFromUnknownSecurityOrUnavailableRssi() {
        val open = wifi(-80, open = true)
        assertEquals(Advisory.WEAK_OPEN_WIFI, advise(LinkSnapshot(open, open, cell), weak = true))
        assertEquals(Advisory.OPEN_WIFI, advise(LinkSnapshot(open, open, cell), weak = false))
        val unknown = wifi(null).copy(security = WiFiSecurity.UNKNOWN)
        assertEquals(Advisory.WIFI_SIGNAL_UNAVAILABLE, advise(LinkSnapshot(unknown, unknown, cell), weak = false))
    }

    @Test
    fun weakSignalNeedsSixSecondsAndEightDbmRecoveryHysteresis() {
        val track = WeakSignalTracker()
        assertFalse(track.sample(wifi(-76), 1_000))
        assertFalse(track.sample(wifi(-78), 6_999))
        assertTrue(track.sample(wifi(-77), 7_000))
        assertTrue(track.sample(wifi(-72), 8_000))
        assertFalse(track.sample(wifi(-68), 9_000))
        assertFalse(track.sample(wifi(-81), 10_000))
        assertTrue(track.sample(wifi(-81), 16_000))
    }

    @Test
    fun unknownSampleAndNewNetworkResetWeakEvidence() {
        val track = WeakSignalTracker()
        assertFalse(track.sample(wifi(-80), 1_000))
        assertFalse(track.sample(wifi(null), 7_000))
        assertFalse(track.sample(wifi(-80), 8_000))
        assertFalse(track.sample(wifi(-80, id = 8L), 15_000))
        assertTrue(track.sample(wifi(-80, id = 8L), 21_000))
    }

    @Test
    fun handoverReportsObservedGapWithoutPretendingPacketContinuity() {
        val track = HandoverTracker()
        assertNull(track.sample(wifi(), 1_000))
        assertNull(track.sample(null, 2_000))
        assertNull(track.sample(cell.copy(validated = false), 3_000))
        assertEquals(Handover(Transport.WIFI, Transport.CELLULAR, 2_000), track.sample(cell, 4_000))
        assertNull(track.sample(cell, 5_000))
        assertEquals(Handover(Transport.CELLULAR, Transport.WIFI, null), track.sample(wifi(), 6_000))
    }

    @Test
    fun vpnRemainsAnExplicitDefaultRatherThanBeingBypassed() {
        val vpn = Link(11L, Transport.VPN, validated = true)
        assertEquals(Advisory.VPN_ACTIVE, advise(LinkSnapshot(vpn, wifi(), cell), weak = true))
    }

    @Test
    fun visibleWifiWithoutValidationDoesNotClaimInternetOrUpdateRoute() {
        val invalid = wifi().copy(validated = false)
        assertEquals(Advisory.UNVERIFIED_WIFI, advise(LinkSnapshot(invalid, invalid, cell), weak = true))
        assertEquals(Advisory.NO_ROUTE, advise(LinkSnapshot(null, invalid, cell), weak = true))
    }
}
