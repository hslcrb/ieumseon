package life.rhee.ieumseon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPolicyTest {
    private fun wifi(rssi: Int? = -76, id: Long = 7L, open: Boolean = false, bars: Int? = rssi?.let { 2 }) =
        Link(id, Transport.WIFI, validated = true, rssi = rssi, bars = bars, security = if (open) WiFiSecurity.OPEN else WiFiSecurity.ENCRYPTED)

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
    fun fiveBarsNormalizeVendorScaleAndRejectInvalidValues() {
        assertEquals(listOf(1, 2, 3, 4, 5), (0..4).map { normalizeBars(it, 4) })
        assertEquals(listOf(1, 2, 4, 5), (0..3).map { normalizeBars(it, 3) })
        assertNull(normalizeBars(6, 4))
        assertNull(normalizeBars(0, 0))
    }

    @Test
    fun oneAndTwoBarsRequireSixSecondsAndThreeResets() {
        val track = WeakSignalTracker()
        assertFalse(track.sample(wifi(bars = 1), 1_000))
        assertFalse(track.sample(wifi(bars = 2), 6_999))
        assertTrue(track.sample(wifi(bars = 2), 7_000))
        assertFalse(track.sample(wifi(bars = 3), 8_000))
        assertFalse(track.sample(wifi(bars = 1), 10_000))
        assertTrue(track.sample(wifi(bars = 1), 16_000))
    }

    @Test
    fun unknownSampleAndNewNetworkResetWeakEvidence() {
        val track = WeakSignalTracker()
        assertFalse(track.sample(wifi(), 1_000))
        assertFalse(track.sample(wifi(bars = null), 7_000))
        assertFalse(track.sample(wifi(), 8_000))
        assertFalse(track.sample(wifi(id = 8L), 15_000))
        assertTrue(track.sample(wifi(id = 8L), 21_000))
    }

    @Test
    fun onlyOneAutomaticRequestPerWeakEpisode() {
        val episodes = WeakEpisode()
        assertFalse(episodes.consume(wifi(), false))
        assertTrue(episodes.consume(wifi(), true))
        assertFalse(episodes.consume(wifi(), true))
        assertFalse(episodes.consume(cell, true))
        assertTrue(episodes.consume(wifi(), true))
    }

    @Test
    fun managedCellMustBeValidatedKnownUnblockedAndStable() {
        val gate = ManagedSwitchGate()
        val tracked = cell.copy(blockedStatusKnown = true)
        assertFalse(gate.sample(false, wifi(), true, tracked, 0L))
        assertFalse(gate.sample(true, wifi(), true, cell, 100L))
        assertFalse(gate.sample(true, wifi(), true, tracked.copy(validated = false), 200L))
        assertFalse(gate.sample(true, wifi(), true, tracked, 1_000L))
        assertFalse(gate.sample(true, wifi(), true, tracked, 2_999L))
        assertTrue(gate.sample(true, wifi(), true, tracked, 3_000L))
        assertFalse(gate.sample(true, wifi(), true, tracked.copy(blocked = true), 3_100L))
        assertFalse(gate.sample(true, wifi(), true, tracked.copy(suspended = true), 3_200L))
        assertFalse(gate.sample(true, wifi(), true, tracked, 3_300L))
    }

    @Test
    fun managedSwitchProtectsVpnPortalAndRecoveredWifi() {
        val gate = ManagedSwitchGate()
        val tracked = cell.copy(blockedStatusKnown = true)
        assertFalse(gate.sample(true, wifi().copy(portal = true), true, tracked, 0L))
        assertFalse(gate.sample(true, wifi(bars = 3), true, tracked, 10_000L))
        assertFalse(gate.sample(true, Link(12L, Transport.VPN, validated = true), true, tracked, 20_000L))
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
