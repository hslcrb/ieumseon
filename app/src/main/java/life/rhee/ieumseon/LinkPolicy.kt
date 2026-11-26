package life.rhee.ieumseon

internal enum class Transport { WIFI, CELLULAR, VPN, OTHER }
internal enum class WiFiSecurity { OPEN, ENCRYPTED, UNKNOWN }
internal enum class Advisory {
    NO_ROUTE, SIGN_IN_WIFI, UNVERIFIED_WIFI, WEAK_OPEN_WIFI, WEAK_WIFI, OPEN_WIFI,
    WIFI_SIGNAL_UNAVAILABLE, HEALTHY_WIFI, MOBILE_VALIDATED, MOBILE_UNVERIFIED,
    VPN_ACTIVE, OTHER_ROUTE,
}

internal data class Link(
    val id: Long,
    val transport: Transport,
    val validated: Boolean = false,
    val portal: Boolean = false,
    val blocked: Boolean = false,
    val rssi: Int? = null,
    val security: WiFiSecurity = WiFiSecurity.UNKNOWN,
) {
    val usable: Boolean get() = validated && !portal && !blocked
}

internal data class LinkSnapshot(
    val primary: Link?,
    val wifi: Link?,
    val cellular: Link?,
) {
    val mobileAvailable: Boolean get() = cellular?.usable == true
}

internal class WeakSignalTracker {
    private var wifiId: Long? = null
    private var weakSince: Long? = null
    private var weak = false

    fun sample(wifi: Link?, nowMillis: Long): Boolean {
        if (wifi?.id != wifiId) {
            wifiId = wifi?.id
            weakSince = null
            weak = false
        }
        val rssi = wifi?.rssi
        if (rssi == null) {
            weakSince = null
            weak = false
            return false
        }
        if (rssi >= RECOVERY_RSSI) {
            weakSince = null
            weak = false
            return false
        }
        if (rssi <= WEAK_RSSI) {
            val since = weakSince ?: nowMillis.also { weakSince = it }
            if (nowMillis - since >= WEAK_DURATION_MS) weak = true
        } else if (!weak) {
            weakSince = null
        }
        return weak
    }

    companion object {
        const val WEAK_RSSI = -76
        const val RECOVERY_RSSI = -68
        const val WEAK_DURATION_MS = 6_000L
    }
}

internal fun advise(snapshot: LinkSnapshot, weak: Boolean): Advisory {
    val primary = snapshot.primary ?: return Advisory.NO_ROUTE
    return when (primary.transport) {
        Transport.WIFI -> when {
            primary.blocked -> Advisory.UNVERIFIED_WIFI
            primary.portal -> Advisory.SIGN_IN_WIFI
            !primary.usable -> Advisory.UNVERIFIED_WIFI
            weak && primary.security == WiFiSecurity.OPEN -> Advisory.WEAK_OPEN_WIFI
            weak -> Advisory.WEAK_WIFI
            primary.security == WiFiSecurity.OPEN -> Advisory.OPEN_WIFI
            primary.rssi == null -> Advisory.WIFI_SIGNAL_UNAVAILABLE
            else -> Advisory.HEALTHY_WIFI
        }
        Transport.CELLULAR -> if (primary.usable) Advisory.MOBILE_VALIDATED else Advisory.MOBILE_UNVERIFIED
        Transport.VPN -> Advisory.VPN_ACTIVE
        Transport.OTHER -> Advisory.OTHER_ROUTE
    }
}

internal data class Handover(
    val from: Transport,
    val to: Transport,
    val observedGapMillis: Long?,
)

internal class HandoverTracker {
    private var previousVerified: Transport? = null
    private var gapStartedAt: Long? = null

    fun sample(primary: Link?, nowMillis: Long): Handover? {
        if (primary?.usable != true) {
            if (previousVerified != null && gapStartedAt == null) gapStartedAt = nowMillis
            return null
        }
        val previous = previousVerified
        val gap = gapStartedAt?.let { (nowMillis - it).coerceAtLeast(0) }
        previousVerified = primary.transport
        gapStartedAt = null
        return if (previous != null && previous != primary.transport) {
            Handover(previous, primary.transport, gap)
        } else null
    }
}
