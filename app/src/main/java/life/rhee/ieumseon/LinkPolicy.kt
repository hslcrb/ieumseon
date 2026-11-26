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
    val suspended: Boolean = false,
    val blockedStatusKnown: Boolean = false,
    val rssi: Int? = null,
    val bars: Int? = null,
    val security: WiFiSecurity = WiFiSecurity.UNKNOWN,
) {
    val usable: Boolean get() = validated && !portal && !blocked && !suspended
}

internal data class LinkSnapshot(
    val primary: Link?,
    val wifi: Link?,
    val cellular: Link?,
) {
    val mobileAvailable: Boolean get() = cellular?.usable == true
}

internal fun normalizeBars(level: Int, platformMaximum: Int): Int? {
    if (platformMaximum <= 0 || level !in 0..platformMaximum) return null
    return 1 + ((level.toLong() * 4 + platformMaximum / 2) / platformMaximum).toInt()
}

internal class WeakSignalTracker {
    private var wifiId: Long? = null
    private var weakSince: Long? = null

    fun sample(wifi: Link?, nowMillis: Long): Boolean {
        if (wifi?.id != wifiId) {
            wifiId = wifi?.id
            weakSince = null
        }
        if (wifi?.transport != Transport.WIFI || wifi.bars !in 1..2) {
            weakSince = null
            return false
        }
        val since = weakSince ?: nowMillis.also { weakSince = it }
        return nowMillis - since >= WEAK_DURATION_MS
    }

    companion object {
        const val WEAK_DURATION_MS = 6_000L
    }
}

internal enum class AutoMode { OFF, READINESS, MANAGED }

internal class WeakEpisode {
    private var requestedWifi: Long? = null

    fun reset() { requestedWifi = null }

    fun consume(primary: Link?, weak: Boolean): Boolean {
        if (!weak || primary?.transport != Transport.WIFI) {
            requestedWifi = null
            return false
        }
        if (requestedWifi == primary.id) return false
        requestedWifi = primary.id
        return true
    }
}

internal class ManagedSwitchGate {
    private var cellId: Long? = null
    private var readySince: Long? = null

    fun sample(
        authorized: Boolean,
        primary: Link?,
        weak: Boolean,
        requestedCellular: Link?,
        nowMillis: Long,
    ): Boolean {
        if (!authorized || primary?.transport != Transport.WIFI || primary.portal ||
            primary.bars !in 1..2 || !weak || requestedCellular?.transport != Transport.CELLULAR ||
            !requestedCellular.usable || !requestedCellular.blockedStatusKnown
        ) {
            cellId = null
            readySince = null
            return false
        }
        if (cellId != requestedCellular.id) {
            cellId = requestedCellular.id
            readySince = nowMillis
        }
        return nowMillis - (readySince ?: nowMillis) >= STABLE_MOBILE_MS
    }

    companion object {
        const val STABLE_MOBILE_MS = 2_000L
    }
}

internal fun advise(snapshot: LinkSnapshot, weak: Boolean): Advisory {
    val primary = snapshot.primary ?: return Advisory.NO_ROUTE
    return when (primary.transport) {
        Transport.WIFI -> when {
            primary.blocked || primary.suspended -> Advisory.UNVERIFIED_WIFI
            primary.portal -> Advisory.SIGN_IN_WIFI
            !primary.usable -> Advisory.UNVERIFIED_WIFI
            weak && primary.security == WiFiSecurity.OPEN -> Advisory.WEAK_OPEN_WIFI
            weak -> Advisory.WEAK_WIFI
            primary.security == WiFiSecurity.OPEN -> Advisory.OPEN_WIFI
            primary.bars == null -> Advisory.WIFI_SIGNAL_UNAVAILABLE
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
