package life.rhee.ieumseon

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var monitor: NetworkMonitor
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var wifiReading: TextView
    private lateinit var mobileReading: TextView
    private lateinit var standbyMessage: TextView
    private lateinit var standbyButton: Button
    private lateinit var wifiSettingsButton: Button
    private lateinit var events: TextView
    private var previousView: MonitorView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Marine
        window.navigationBarColor = Marine
        monitor = NetworkMonitor(applicationContext, ::render)
        val page = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Marine)
        }
        val column = stack().apply { setPadding(dp(22), dp(20), dp(22), dp(32)) }
        page.addView(column)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            page.setOnApplyWindowInsetsListener { _, windowInsets ->
                val safe = windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                page.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                windowInsets
            }
        }
        setContentView(page)

        val wordmark = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        wordmark.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_mark)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) })
        val brand = label("이음선", 20, Mint, true)
        brand.isAccessibilityHeading = true
        wordmark.addView(brand)
        column.addView(wordmark)
        column.addView(label("IEUMSEON   /   연결 관측소", 12, Muted, true), margin(2, 18))

        val status = surface()
        status.addView(label("기본 연결", 12, Muted, true))
        statusTitle = label("연결 확인 중", 30, Ivory, true).apply {
            isAccessibilityHeading = true
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        status.addView(statusTitle, margin(7, 5))
        statusMessage = label("Android가 보고하는 네트워크 상태를 읽고 있어요.", 15, Ivory)
        status.addView(statusMessage)
        status.addView(button("인터넷 설정 열기", true, ::openInternetPanel), margin(17, 0))
        wifiSettingsButton = button("Wi‑Fi 자동 연결 설정", false, ::openWifiSettings).apply { visibility = View.GONE }
        status.addView(wifiSettingsButton, margin(7, 0))
        column.addView(status, margin(0, 14))

        val readings = surface()
        readings.addView(label("보이는 경로", 18, Ivory, true).apply { isAccessibilityHeading = true })
        wifiReading = label("Wi‑Fi   /   확인 중", 15, Ivory)
        mobileReading = label("모바일   /   확인 중", 15, Ivory)
        readings.addView(wifiReading, margin(12, 0))
        readings.addView(mobileReading, margin(7, 0))
        column.addView(readings, margin(0, 14))

        val standby = surface()
        standby.addView(label("모바일 대기", 18, Ivory, true).apply { isAccessibilityHeading = true })
        standbyMessage = label("이 화면을 보면서 최대 2분 동안 모바일 경로를 요청합니다. 배터리 소모가 늘 수 있어요.", 15, Muted)
        standby.addView(standbyMessage, margin(6, 4))
        standbyButton = button("2분 모바일 대기 요청", false, monitor::toggleStandby)
        standby.addView(standbyButton, margin(10, 0))
        column.addView(standby, margin(0, 14))

        val changes = surface()
        changes.addView(label("최근 전환", 18, Ivory, true).apply { isAccessibilityHeading = true })
        events = label("이 화면에서 확인한 전환이 없어요.", 15, Muted)
        changes.addView(events, margin(7, 0))
        column.addView(changes, margin(0, 15))

        column.addView(label(
            "이음선은 다른 앱의 기본 경로를 강제로 바꾸거나 기존 통화·다운로드를 이어 주지 않습니다. " +
                "주변 네트워크를 스캔하거나 이름·위치·트래픽을 저장하지 않아요.",
            13, Muted,
        ), margin(2, 0))
    }

    override fun onStart() {
        super.onStart()
        monitor.start()
    }

    override fun onStop() {
        monitor.stop()
        super.onStop()
    }

    private fun openInternetPanel() {
        try {
            startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        } catch (_: ActivityNotFoundException) {
            openWifiSettings()
        }
    }

    private fun openWifiSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "기본 설정 앱에서 인터넷 설정을 열어 주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun render(view: MonitorView) {
        if (view == previousView) return
        previousView = view
        val ready = view.snapshot.mobileAvailable
        val option = if (ready) "모바일 경로가 검증됐어요. 인터넷 설정에서 전환할 수 있습니다."
        else "모바일 경로도 아직 검증되지 않았어요. 인터넷 설정에서 상태를 확인해 보세요."
        val (title, detail) = when (view.advice) {
            Advisory.NO_ROUTE -> "기본 경로 없음" to "Android에서 기본 경로가 보이지 않아요. $option"
            Advisory.SIGN_IN_WIFI -> "Wi‑Fi 로그인 필요" to "접속 페이지가 응답하고 있지만 인터넷은 검증되지 않았어요. $option"
            Advisory.UNVERIFIED_WIFI -> "Wi‑Fi 인터넷 미검증" to "Wi‑Fi에 연결됐지만 인터넷 확인이 되지 않았어요. $option"
            Advisory.WEAK_OPEN_WIFI -> "약한 개방형 Wi‑Fi" to "암호화되지 않은 Wi‑Fi의 약한 신호가 지속됐어요. $option"
            Advisory.WEAK_WIFI -> "Wi‑Fi 신호 약함" to "Wi‑Fi의 약한 신호가 지속됐어요. $option"
            Advisory.OPEN_WIFI -> "개방형 Wi‑Fi" to "인터넷은 확인됐지만 이 Wi‑Fi는 암호화되지 않았어요."
            Advisory.WIFI_SIGNAL_UNAVAILABLE -> "Wi‑Fi 인터넷 확인" to "인터넷은 검증됐지만 기기가 신호 값을 제공하지 않아 세기는 판단하지 않아요."
            Advisory.HEALTHY_WIFI -> "Wi‑Fi 연결" to "Android가 인터넷을 검증했고 지속적인 약한 신호는 확인되지 않았어요."
            Advisory.MOBILE_VALIDATED -> "모바일 데이터 연결" to "현재 기본 경로에서 Android가 인터넷을 검증했어요."
            Advisory.MOBILE_UNVERIFIED -> "모바일 인터넷 미검증" to "모바일 경로가 보이지만 인터넷 연결은 아직 확인되지 않았어요."
            Advisory.VPN_ACTIVE -> "VPN이 기본 경로" to "현재 앱의 기본 경로는 VPN입니다. VPN을 우회하거나 변경하지 않아요."
            Advisory.OTHER_ROUTE -> "다른 기본 경로" to "Android에서 Wi‑Fi와 모바일 데이터 이외의 기본 경로를 사용 중이에요."
        }
        update(statusTitle, title)
        val openWifi = view.snapshot.primary?.security == WiFiSecurity.OPEN
        val wifiHint = " Wi‑Fi 설정에서 해당 네트워크의 자동 연결을 끄거나 목록에서 삭제할 수 있어요. 메뉴 이름은 기기마다 달라요."
        update(statusMessage, if (openWifi) detail + wifiHint else detail)
        wifiSettingsButton.visibility = if (openWifi) View.VISIBLE else View.GONE
        update(wifiReading, "Wi‑Fi   /   " + describe(view.snapshot.wifi))
        update(mobileReading, "모바일   /   " + describe(view.snapshot.cellular))
        update(standbyMessage, when (view.standby) {
            Standby.OFF -> "이 화면을 보면서 최대 2분 동안 모바일 경로를 요청합니다. 배터리 소모가 늘 수 있어요."
            Standby.REQUESTING -> "Android에 모바일 경로를 요청하는 중입니다. 약 15초 안에 경로가 없으면 요청을 해제합니다."
            Standby.HELD -> if (ready) "모바일 인터넷이 검증됐어요. 기본 경로는 아직 바꾸지 않았습니다."
                else "모바일 경로가 보이지만 아직 인터넷이 검증되지 않았어요."
            Standby.UNAVAILABLE -> "모바일 경로를 유지할 수 없었어요. 데이터 설정과 수신 상태를 확인해 주세요."
            Standby.EXPIRED -> "2분 대기 요청을 해제했어요. 필요하면 다시 시작할 수 있습니다."
        })
        update(standbyButton, if (view.standby in setOf(Standby.REQUESTING, Standby.HELD)) "모바일 대기 해제" else "2분 모바일 대기 요청")
        update(events, view.handovers.joinToString("\n") { change ->
            val gap = change.observedGapMillis
            val note = if (gap == null) "공백 미관측" else "미검증 상태 관측: 약 ${(gap / 1000.0).roundToInt()}초"
            "${transportName(change.from)} → ${transportName(change.to)}  ·  $note"
        }.ifEmpty { "이 화면에서 확인한 전환이 없어요." })
    }

    private fun describe(link: Link?): String {
        if (link == null) return "감지되지 않음"
        val label = when {
            link.blocked -> "사용 불가"
            link.portal -> "로그인 필요"
            link.usable -> "인터넷 확인됨"
            else -> "인터넷 미검증"
        }
        if (link.transport != Transport.WIFI) return label
        val security = when (link.security) {
            WiFiSecurity.OPEN -> "개방형"
            WiFiSecurity.ENCRYPTED -> "암호화"
            WiFiSecurity.UNKNOWN -> "보안 유형 미확인"
        }
        return "$label · $security · ${link.rssi?.let { "$it dBm" } ?: "신호 미제공"}"
    }

    private fun transportName(transport: Transport): String = when (transport) {
        Transport.WIFI -> "Wi‑Fi"
        Transport.CELLULAR -> "모바일"
        Transport.VPN -> "VPN"
        Transport.OTHER -> "기타"
    }

    private fun update(view: TextView, value: String) {
        if (view.text.toString() != value) view.text = value
    }

    private fun surface() = stack().apply {
        setPadding(dp(18), dp(15), dp(18), dp(15))
        background = box(Surface, 17, Line)
    }

    private fun stack() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun label(string: String, size: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = string
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(2).toFloat(), 1.1f)
        }

    private fun button(string: String, primary: Boolean, act: () -> Unit) = Button(this).apply {
        text = string
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        minHeight = dp(48)
        gravity = Gravity.CENTER
        setTextColor(if (primary) Marine else Mint)
        val fill = if (primary) Mint else Surface
        background = RippleDrawable(
            ColorStateList.valueOf(if (primary) 0x240B2527 else 0x30A6F3C8),
            box(fill, 11, if (primary) null else Mint),
            box(Color.WHITE, 11, null),
        )
        isFocusable = true
        setOnFocusChangeListener { _, focused ->
            elevation = if (focused) dp(4).toFloat() else 0f
            background = RippleDrawable(
                ColorStateList.valueOf(0x333D8267),
                box(fill, 11, if (focused) Ivory else if (primary) null else Mint),
                box(Color.WHITE, 11, null),
            )
        }
        setOnClickListener { act() }
    }

    private fun box(fill: Int, radius: Int, border: Int?) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        border?.let { setStroke(dp(1), it) }
    }

    private fun margin(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(px: Int) = (px * resources.displayMetrics.density).roundToInt()

    companion object {
        private val Marine = Color.rgb(11, 37, 39)
        private val Surface = Color.rgb(17, 48, 51)
        private val Line = Color.rgb(64, 104, 105)
        private val Mint = Color.rgb(166, 243, 200)
        private val Ivory = Color.rgb(246, 245, 233)
        private val Muted = Color.rgb(185, 215, 209)
    }
}
