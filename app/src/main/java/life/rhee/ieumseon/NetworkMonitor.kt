package life.rhee.ieumseon

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal enum class Standby { OFF, REQUESTING, HELD, UNAVAILABLE, EXPIRED }

internal data class MonitorView(
    val snapshot: LinkSnapshot,
    val advice: Advisory,
    val standby: Standby,
    val handovers: List<Handover>,
)

internal class NetworkMonitor(context: Context, private val publish: (MonitorView) -> Unit) {
    private val manager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
    private val handler = Handler(Looper.getMainLooper())
    private val passiveLinks = mutableMapOf<Network, Link>()
    private val passiveBlocked = mutableSetOf<Network>()
    private var defaultNetwork: Network? = null
    private var defaultLink: Link? = null
    private var defaultBlocked = false
    private var standbyLink: Link? = null
    private var standbyBlocked = false
    private var standbyCallback: ConnectivityManager.NetworkCallback? = null
    private var standby = Standby.OFF
    private var weak = WeakSignalTracker()
    private var handover = HandoverTracker()
    private val history = ArrayDeque<Handover>()
    private var running = false

    private val passiveCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!running) return
            passiveLinks[network] = link(network, capabilities, network in passiveBlocked)
            render()
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (!running) return
            if (blocked) passiveBlocked.add(network) else passiveBlocked.remove(network)
            passiveLinks[network]?.let { passiveLinks[network] = it.copy(blocked = blocked) }
            render()
        }

        override fun onLost(network: Network) {
            if (!running) return
            passiveLinks.remove(network)
            passiveBlocked.remove(network)
            render()
        }
    }

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!running) return
            defaultNetwork = network
            defaultBlocked = network in passiveBlocked
            // onCapabilitiesChanged follows. Do not manufacture a gap from callback ordering.
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!running || defaultNetwork != network) return
            defaultLink = link(network, capabilities, defaultBlocked)
            render()
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (!running || defaultNetwork != network) return
            defaultBlocked = blocked
            if (defaultLink?.id == network.networkHandle) {
                defaultLink = defaultLink?.copy(blocked = blocked)
                render()
            }
        }

        override fun onLost(network: Network) {
            if (!running || defaultNetwork != network) return
            defaultNetwork = null
            defaultLink = null
            defaultBlocked = false
            render()
        }
    }

    private val samples = object : Runnable {
        override fun run() {
            if (!running) return
            render()
            handler.postDelayed(this, 1_000)
        }
    }

    private val standbyAcquireTimeout = Runnable {
        if (standby == Standby.REQUESTING) releaseStandby(Standby.UNAVAILABLE)
    }

    private val standbyRelease = Runnable { releaseStandby(Standby.EXPIRED) }

    fun start() {
        if (running) return
        running = true
        weak = WeakSignalTracker()
        handover = HandoverTracker()
        manager.activeNetwork?.let { network ->
            defaultNetwork = network
            defaultLink = manager.getNetworkCapabilities(network)?.let { link(network, it, false) }
        }
        manager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build(),
            passiveCallback,
            handler,
        )
        manager.registerDefaultNetworkCallback(defaultCallback, handler)
        handler.post(samples)
    }

    fun stop() {
        if (!running) return
        running = false
        releaseStandby(Standby.OFF)
        handler.removeCallbacks(samples)
        manager.unregisterNetworkCallback(passiveCallback)
        manager.unregisterNetworkCallback(defaultCallback)
        passiveLinks.clear()
        passiveBlocked.clear()
        defaultNetwork = null
        defaultLink = null
        defaultBlocked = false
    }

    fun toggleStandby() {
        if (!running) return
        if (standbyCallback != null) {
            releaseStandby(Standby.OFF)
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (standbyCallback !== this) return
                standbyLink = link(network, capabilities, standbyBlocked)
                standby = Standby.HELD
                handler.removeCallbacks(standbyAcquireTimeout)
                render()
            }

            override fun onAvailable(network: Network) {
                if (standbyCallback !== this) return
                standbyBlocked = false
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (standbyCallback !== this) return
                standbyBlocked = blocked
                standbyLink = standbyLink?.copy(blocked = blocked)
                render()
            }

            override fun onLost(network: Network) {
                if (standbyCallback !== this) return
                releaseStandby(Standby.UNAVAILABLE)
            }

            override fun onUnavailable() {
                if (standbyCallback === this) releaseStandby(Standby.UNAVAILABLE)
            }
        }
        standbyCallback = callback
        standby = Standby.REQUESTING
        try {
            manager.requestNetwork(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build(),
                callback,
                handler,
            )
            handler.postDelayed(standbyAcquireTimeout, 15_000)
            handler.postDelayed(standbyRelease, 120_000)
        } catch (_: RuntimeException) {
            releaseStandby(Standby.UNAVAILABLE)
        }
        render()
    }

    private fun releaseStandby(next: Standby) {
        val active = standbyCallback
        standbyCallback = null
        handler.removeCallbacks(standbyAcquireTimeout)
        handler.removeCallbacks(standbyRelease)
        if (active != null) {
            try {
                manager.unregisterNetworkCallback(active)
            } catch (_: IllegalArgumentException) {
                // The platform may have already released a request after onUnavailable.
            }
        }
        standby = next
        standbyLink = null
        standbyBlocked = false
        render()
    }

    private fun render() {
        if (!running) return
        val primary = defaultLink
        val values = passiveLinks.values + listOfNotNull(standbyLink)
        val wifi = primary?.takeIf { it.transport == Transport.WIFI }
            ?: values.filter { it.transport == Transport.WIFI }
                .maxWithOrNull(compareBy<Link> { it.usable }.thenBy { it.rssi ?: Int.MIN_VALUE }.thenBy { it.id })
        val mobile = primary?.takeIf { it.transport == Transport.CELLULAR }
            ?: values.filter { it.transport == Transport.CELLULAR }
                .maxWithOrNull(compareBy<Link> { it.usable }.thenBy { it.id })
        val now = SystemClock.elapsedRealtime()
        val weakWifi = weak.sample(wifi, now)
        handover.sample(primary, now)?.let {
            history.addFirst(it)
            if (history.size > 4) history.removeLast()
        }
        val snapshot = LinkSnapshot(primary, wifi, mobile)
        publish(MonitorView(snapshot, advise(snapshot, weakWifi), standby, history.toList()))
    }

    private fun link(network: Network, caps: NetworkCapabilities, blocked: Boolean): Link {
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            else -> Transport.OTHER
        }
        val info = caps.transportInfo as? WifiInfo
        val signal = if (transport == Transport.WIFI) {
            info?.rssi?.takeIf { it in -126..-1 } ?: caps.signalStrength.takeIf { it in -126..-1 }
        } else null
        val security = if (transport == Transport.WIFI && Build.VERSION.SDK_INT >= 31 && info != null) {
            when (info.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_OPEN -> WiFiSecurity.OPEN
                WifiInfo.SECURITY_TYPE_UNKNOWN -> WiFiSecurity.UNKNOWN
                else -> WiFiSecurity.ENCRYPTED
            }
        } else WiFiSecurity.UNKNOWN
        return Link(
            id = network.networkHandle,
            transport = transport,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            portal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            blocked = blocked || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
            rssi = signal,
            security = security,
        )
    }
}
