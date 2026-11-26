package life.rhee.ieumseon

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal enum class Standby { OFF, REQUESTING, HELD, UNAVAILABLE, EXPIRED }
internal enum class RadioSwitch { IDLE, REQUESTED, MOBILE_DEFAULT, RESTORING, RESTORED, FAILED }

internal data class MonitorView(
    val snapshot: LinkSnapshot,
    val advice: Advisory,
    val standby: Standby,
    val handovers: List<Handover>,
    val autoMode: AutoMode,
    val weakWifi: Boolean,
    val deviceOwner: Boolean,
    val restoreNeeded: Boolean,
    val radioSwitch: RadioSwitch,
)

internal class NetworkMonitor(private val context: Context, private val managedControl: Boolean = true,
    private val publish: (MonitorView) -> Unit) {
    private val manager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val policies = context.getSystemService(DevicePolicyManager::class.java)
    private val preferences = context.getSharedPreferences("managed-radio", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val passiveLinks = mutableMapOf<Network, Link>()
    private val passiveBlocked = mutableMapOf<Network, Boolean>()
    private var defaultNetwork: Network? = null
    private var defaultLink: Link? = null
    private var defaultBlocked: Boolean? = null
    private var standbyLink: Link? = null
    private var standbyBlocked: Boolean? = null
    private var standbyCallback: ConnectivityManager.NetworkCallback? = null
    private var standby = Standby.OFF
    private var weak = WeakSignalTracker()
    private var handover = HandoverTracker()
    private val history = ArrayDeque<Handover>()
    private var running = false
    private var mode = AutoMode.OFF
    private var autostandby = false
    private val episode = WeakEpisode()
    private var switchGate = ManagedSwitchGate()
    private var restoreNeeded = preferences.getBoolean("restore-needed", false)
    private var radioSwitch = RadioSwitch.IDLE
    private var radioDeadline = 0L

    val deviceOwner: Boolean
        get() = policies?.isDeviceOwnerApp(context.packageName) == true && wifiManager != null


    private val passiveCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!running) return
            passiveLinks[network] = link(network, capabilities, passiveBlocked[network])
            render()
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (!running) return
            passiveBlocked[network] = blocked
            passiveLinks[network]?.let { passiveLinks[network] = it.copy(blocked = blocked, blockedStatusKnown = true) }
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
            defaultBlocked = passiveBlocked[network]
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
                defaultLink = defaultLink?.copy(blocked = blocked, blockedStatusKnown = true)
                render()
            }
        }

        override fun onLost(network: Network) {
            if (!running || defaultNetwork != network) return
            defaultNetwork = null
            defaultLink = null
            defaultBlocked = null
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
            defaultLink = manager.getNetworkCapabilities(network)?.let { link(network, it, null) }
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
        mode = AutoMode.OFF
        episode.reset()
        switchGate = ManagedSwitchGate()
        releaseStandby(Standby.OFF)
        handler.removeCallbacks(samples)
        manager.unregisterNetworkCallback(passiveCallback)
        manager.unregisterNetworkCallback(defaultCallback)
        passiveLinks.clear()
        passiveBlocked.clear()
        defaultNetwork = null
        defaultLink = null
        defaultBlocked = null
    }

    fun toggleStandby() {
        if (!running) return
        if (standbyCallback != null) {
            releaseStandby(Standby.OFF)
            return
        }
        requestStandby(automatic = false)
        render()
    }

    fun setAutoMode(next: AutoMode) {
        if (!running || (next == AutoMode.MANAGED && (!managedControl || !deviceOwner || restoreNeeded))) return
        mode = next
        episode.reset()
        switchGate = ManagedSwitchGate()
        if (next == AutoMode.OFF && autostandby) releaseStandby(Standby.OFF)
        render()
    }

    fun restoreWifi() {
        if (!running || !restoreNeeded) return
        val now = SystemClock.elapsedRealtime()
        if (requestRadio(enabled = true)) {
            radioSwitch = RadioSwitch.RESTORING
            radioDeadline = now + RADIO_CHECK_MS
        } else {
            radioSwitch = RadioSwitch.FAILED
        }
        render()
    }

    private fun requestStandby(automatic: Boolean) {
        if (standbyCallback != null) return
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
                standbyBlocked = null
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (standbyCallback !== this) return
                standbyBlocked = blocked
                standbyLink = standbyLink?.copy(blocked = blocked, blockedStatusKnown = true)
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
        autostandby = automatic
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
    }

    private fun releaseStandby(next: Standby) {
        val active = standbyCallback
        standbyCallback = null
        autostandby = false
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
        standbyBlocked = null
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
        val currentWifi = primary?.takeIf { it.transport == Transport.WIFI && defaultNetwork?.networkHandle == it.id }
        val weakWifi = weak.sample(currentWifi, now)
        if (mode != AutoMode.OFF && episode.consume(currentWifi, weakWifi)) requestStandby(automatic = true)
        if (managedControl) updateManagedRadio(primary, weakWifi, now)
        else restoreNeeded = preferences.getBoolean("restore-needed", false)
        handover.sample(primary, now)?.let {
            history.addFirst(it)
            if (history.size > 4) history.removeLast()
        }
        val snapshot = LinkSnapshot(primary, wifi, mobile)
        publish(MonitorView(snapshot, advise(snapshot, weakWifi), standby, history.toList(), mode, weakWifi,
            deviceOwner, restoreNeeded, radioSwitch))
    }

    private fun updateManagedRadio(primary: Link?, weakWifi: Boolean, now: Long) {
        val wifiState = wifiManager?.wifiState
        val wifiOn = wifiState == WifiManager.WIFI_STATE_ENABLED
        val wifiOff = wifiState == WifiManager.WIFI_STATE_DISABLED
        val mobileDefault = primary?.transport == Transport.CELLULAR && primary.usable && primary.blockedStatusKnown
        when {
            radioSwitch == RadioSwitch.REQUESTED && wifiOff && mobileDefault -> radioSwitch = RadioSwitch.MOBILE_DEFAULT
            radioSwitch == RadioSwitch.REQUESTED && now >= radioDeadline -> {
                if (wifiOn) {
                    setRestoreMarker(false)
                    radioSwitch = RadioSwitch.FAILED
                } else if (requestRadio(enabled = true)) {
                    radioSwitch = RadioSwitch.RESTORING
                    radioDeadline = now + RADIO_CHECK_MS
                } else {
                    radioSwitch = RadioSwitch.FAILED
                }
            }
            radioSwitch == RadioSwitch.RESTORING && now >= radioDeadline && !wifiOn -> radioSwitch = RadioSwitch.FAILED
            restoreNeeded && wifiOn && radioSwitch != RadioSwitch.REQUESTED -> {
                setRestoreMarker(false)
                radioSwitch = RadioSwitch.RESTORED
            }
        }
        val sameCurrentWifi = defaultNetwork?.networkHandle == primary?.id
        if (!switchGate.sample(mode == AutoMode.MANAGED && deviceOwner && wifiOn &&
                sameCurrentWifi && !restoreNeeded, primary, weakWifi, standbyLink, now)
        ) return
        // Disarm before requesting to prevent oscillation, even if Android rejects this request.
        mode = AutoMode.OFF
        switchGate = ManagedSwitchGate()
        // Record the need to offer recovery before changing system radio state.
        if (!setRestoreMarker(true)) {
            radioSwitch = RadioSwitch.FAILED
            return
        }
        if (requestRadio(enabled = false)) {
            radioSwitch = RadioSwitch.REQUESTED
            radioDeadline = now + RADIO_CHECK_MS
        } else {
            setRestoreMarker(false)
            radioSwitch = RadioSwitch.FAILED
        }
    }

    private fun setRestoreMarker(value: Boolean): Boolean {
        val saved = preferences.edit().putBoolean("restore-needed", value).commit()
        if (saved) restoreNeeded = value
        return saved
    }

    @Suppress("DEPRECATION") // Device Owner is explicitly exempt from the Android 10 restriction.
    private fun requestRadio(enabled: Boolean): Boolean {
        if (!deviceOwner) return false
        return try {
            wifiManager?.setWifiEnabled(enabled) == true
        } catch (_: SecurityException) {
            false
        }
    }

    @Suppress("DEPRECATION") // Platform five-level conversion is used specifically on Android 10.
    private fun signalBars(rssi: Int): Int? {
        val manager = wifiManager ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= 30) normalizeBars(manager.calculateSignalLevel(rssi), manager.maxSignalLevel)
            else WifiManager.calculateSignalLevel(rssi, 5).takeIf { it in 0..4 }?.plus(1)
        } catch (_: RuntimeException) {
            null
        }
    }

    companion object {
        private const val RADIO_CHECK_MS = 8_000L
    }

    private fun link(network: Network, caps: NetworkCapabilities, blocked: Boolean?): Link {
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
            blocked = blocked == true,
            suspended = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
            blockedStatusKnown = blocked != null,
            rssi = signal,
            bars = signal?.let(::signalBars),
            security = security,
        )
    }
}
