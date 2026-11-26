package life.rhee.ieumseon

import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var signalReading: TextView
    private lateinit var internetButton: Button
    private lateinit var autoMessage: TextView
    private lateinit var autoButton: Button
    private lateinit var managedPanel: LinearLayout
    private lateinit var managedMessage: TextView
    private lateinit var managedButton: Button
    private lateinit var restoreButton: Button
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
        signalReading = label("", 15, Mint, true).apply { visibility = View.GONE }
        status.addView(signalReading, margin(5, 0))
        statusTitle = label("연결 확인 중", 30, Ivory, true).apply {
            isAccessibilityHeading = true
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        status.addView(statusTitle, margin(7, 5))
        statusMessage = label("Android가 보고하는 네트워크 상태를 읽고 있어요.", 15, Ivory)
        status.addView(statusMessage)
        internetButton = button("인터넷 설정 열기", true, ::openInternetPanel)
        status.addView(internetButton, margin(17, 0))
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

        val automatic = surface()
        automatic.addView(label("1–2칸 자동 대기", 18, Ivory, true).apply { isAccessibilityHeading = true })
        autoMessage = label("이 화면에서만, 약한 Wi‑Fi가 6초 지속되면 모바일 경로를 자동으로 요청합니다.", 15, Muted)
        automatic.addView(autoMessage, margin(6, 4))
        autoButton = button("자동 대기 켜기", false) {
            monitor.setAutoMode(if (previousView?.autoMode == AutoMode.READINESS) AutoMode.OFF else AutoMode.READINESS)
        }
        automatic.addView(autoButton, margin(10, 0))
        column.addView(automatic, margin(0, 14))

        managedPanel = surface().apply { visibility = View.GONE }
        managedPanel.addView(label("기기 소유자 · 관리 기기 전환", 18, Ivory, true).apply { isAccessibilityHeading = true })
        managedMessage = label("", 15, Muted)
        managedPanel.addView(managedMessage, margin(6, 4))
        managedButton = button("관리 기기 자동 전환 준비", false, ::confirmManaged)
        managedPanel.addView(managedButton, margin(10, 0))
        restoreButton = button("Wi‑Fi 다시 켜기", true, monitor::restoreWifi)
        managedPanel.addView(restoreButton, margin(8, 0))
        column.addView(managedPanel, margin(0, 14))

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
            "일반 폰에서 기기 전체 전환은 Android 인터넷 설정에서 합니다. 기존 통화·다운로드의 무중단 전환을 보장하지 않습니다. " +
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

    private fun confirmManaged() {
        if (!monitor.deviceOwner) return
        if (previousView?.autoMode == AutoMode.MANAGED) {
            monitor.setAutoMode(AutoMode.OFF)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("관리 기기에서 이번 전환을 준비할까요?")
            .setMessage("이 화면에서 최대 2분간 관측합니다. Wi‑Fi 1–2칸이 6초 지속되고 모바일 인터넷이 확인되면 Wi‑Fi를 끄도록 한 번 요청합니다. 다른 앱도 영향을 받고 모바일 요금이 발생할 수 있습니다. 무중단을 보장하지 않습니다. Wi‑Fi는 이 앱 또는 Android 설정에서 다시 켤 수 있습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("한 번 준비") { _, _ -> monitor.setAutoMode(AutoMode.MANAGED) }
            .show()
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
        val bars = view.snapshot.wifi?.bars
        signalReading.visibility = if (bars != null) View.VISIBLE else View.GONE
        if (bars != null) update(signalReading, "Wi‑Fi 신호  $bars / 5칸")
        update(internetButton, if (view.weakWifi && ready) "모바일 전환 설정 열기" else "인터넷 설정 열기")
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
        update(autoMessage, when (view.autoMode) {
            AutoMode.OFF -> "이 화면에서만, Wi‑Fi 1–2칸이 6초 지속되면 모바일 경로를 자동으로 요청합니다. 실제 전환은 Android 설정에서 합니다."
            AutoMode.READINESS -> if (view.weakWifi && ready) "모바일 경로 검증됨 · 위의 전환 설정을 열어 Wi‑Fi를 끄세요."
                else "켜짐 · 같은 Wi‑Fi에서 1–2칸이 6초 이어지면 모바일 경로를 한 번 요청합니다."
            AutoMode.MANAGED -> "관리 기기 모드에서 약한 Wi‑Fi와 모바일 경로를 확인 중입니다."
        })
        update(autoButton, if (view.autoMode == AutoMode.READINESS) "자동 대기 끄기" else "자동 대기 켜기")
        autoButton.isEnabled = view.autoMode != AutoMode.MANAGED
        autoButton.alpha = if (autoButton.isEnabled) 1f else 0.6f
        managedPanel.visibility = if (view.deviceOwner || view.restoreNeeded) View.VISIBLE else View.GONE
        managedButton.visibility = if (view.deviceOwner && !view.restoreNeeded) View.VISIBLE else View.GONE
        update(managedButton, if (view.autoMode == AutoMode.MANAGED) "이번 자동 전환 취소" else "관리 기기 자동 전환 준비")
        restoreButton.visibility = if (view.restoreNeeded) View.VISIBLE else View.GONE
        update(restoreButton, if (view.deviceOwner) "Wi‑Fi 다시 켜기" else "Android Wi‑Fi 설정 열기")
        restoreButton.setOnClickListener { if (view.deviceOwner) monitor.restoreWifi() else openWifiSettings() }
        restoreButton.isEnabled = view.radioSwitch != RadioSwitch.RESTORING
        update(managedMessage, when (view.radioSwitch) {
            RadioSwitch.IDLE -> if (view.restoreNeeded) "앞서 요청한 Wi‑Fi 끄기를 되돌릴 수 있습니다."
                else if (view.autoMode == AutoMode.MANAGED) "모바일 후보 확인 중 · 검증 전에는 Wi‑Fi를 끄지 않습니다."
                else "이음선이 기기 소유자로 등록된 경우에만 한 번의 관측 세션을 준비할 수 있습니다."
            RadioSwitch.REQUESTED -> "Wi‑Fi 끄기 요청을 접수했습니다. 실제 모바일 기본 경로를 확인 중입니다."
            RadioSwitch.MOBILE_DEFAULT -> "Wi‑Fi가 꺼지고 검증된 모바일 기본 경로가 관측됐습니다."
            RadioSwitch.RESTORING -> "Wi‑Fi 다시 켜기 요청을 접수했습니다. 실제 상태를 확인 중입니다."
            RadioSwitch.RESTORED -> "Android에서 Wi‑Fi 켜짐을 확인했습니다. 다시 자동 전환하려면 새로 준비하세요."
            RadioSwitch.FAILED -> "전환 또는 복구를 확인하지 못했어요. Android 인터넷 설정을 확인해 주세요."
        })
        update(standbyMessage, when (view.standby) {
            Standby.OFF -> "이 화면을 보면서 최대 2분 동안 모바일 경로를 요청합니다. 배터리 소모가 늘 수 있어요."
            Standby.REQUESTING -> "Android에 모바일 경로를 요청하는 중입니다. 약 15초 안에 경로가 없으면 요청을 해제합니다."
            Standby.HELD -> if (ready) "모바일 인터넷이 검증됐어요. 실제 기본 경로는 위에서 확인하세요."
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
            link.blocked || link.suspended -> "사용 불가"
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
        val strength = link.bars?.let { "$it/5칸" } ?: "신호 미제공"
        val rssi = link.rssi?.let { " ($it dBm)" }.orEmpty()
        return "$label · $security · $strength$rssi"
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
