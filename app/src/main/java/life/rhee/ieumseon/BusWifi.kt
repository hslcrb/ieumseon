package life.rhee.ieumseon

import android.content.Intent
import android.net.MacAddress
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import java.security.cert.X509Certificate

internal object BusWifi {
    val profiles = listOf("Public WiFi Secure", "PublicWifi@BUS_Secure", "SecureWiFi@Suwon")
    private val domainPattern = Regex("(?i)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
    private val mac = Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")

    fun validDomain(value: String): Boolean = value.length in 3..253 && domainPattern.matches(value)

    fun validBssid(value: String): Boolean = mac.matches(value) && value != "00:00:00:00:00:00" &&
        value.substring(0, 2).toInt(16) % 2 == 0

    // Android confirms the one-time save; only Android subsequently decides auto-join.
    fun addIntent(name: String, domain: String, bssid: String, ca: X509Certificate): Intent {
        require(name in profiles && validDomain(domain) && (bssid.isEmpty() || validBssid(bssid)))
        ca.checkValidity()
        require(ca.basicConstraints >= 0)
        val eap = WifiEnterpriseConfig().apply {
            eapMethod = WifiEnterpriseConfig.Eap.PEAP
            phase2Method = WifiEnterpriseConfig.Phase2.MSCHAPV2
            identity = "wifi"
            password = "wifi"
            domainSuffixMatch = domain
            setCaCertificate(ca)
        }
        val suggestion = WifiNetworkSuggestion.Builder().setSsid(name).setWpa2EnterpriseConfig(eap)
        if (bssid.isNotEmpty()) suggestion.setBssid(MacAddress.fromString(bssid))
        return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
            Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion.build()),
        )
    }
}
